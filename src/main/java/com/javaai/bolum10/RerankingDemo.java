package com.javaai.bolum10;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("pgvector")
@EnableAutoConfiguration
@Import(Bolum10SeedVeriHazirla.class)
@RestController
@RequestMapping(value = "/api/b108", produces = "application/json;charset=UTF-8")

public class RerankingDemo {

    private static final String LESSON_CODE = "b108";

    // Ilk asamada kac belge getirilecegi — rerank oncesi ham havuz buyuklugu
    private static final int INITIAL_TOP_K = 10;

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;
    private final Bolum10SeedVeriHazirla seedService;

    public RerankingDemo(ChatClient.Builder chatClientBuilder,
        VectorStore vectorStore,
        Bolum10SeedVeriHazirla seedService) {
        this.chatClientBuilder = chatClientBuilder;
        this.vectorStore = vectorStore;
        this.seedService = seedService;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector"); // pgvector profili: PostgreSQL + pgvector aktif
        SpringApplication.run(RerankingDemo.class, args);          // Uygulamayi baslat
    }

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return seedService.seedLesson(LESSON_CODE);
    }

    // Juri oncesi adaylar — ham siralamayi gosteriyoruz, rerank henuz yok
    @GetMapping("/before-rerank")
    public Map<String, Object> beforeRerank(@RequestParam(defaultValue = "embedding") String q) {

        List<Document> retrieved = searchLessonDocuments(q, INITIAL_TOP_K);
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("resultCount", retrieved.size());
        response.put("results", mapDocuments(retrieved));

        return response;
    }

    // Juri - ayni adaylar, bu sefer LLM puanliyor
    @GetMapping("/after-rerank")
    public Map<String, Object> afterRerank(@RequestParam(defaultValue = "embedding") String q) {

        List<Document> retrieved = searchLessonDocuments(q, INITIAL_TOP_K); // Once ham embedding aramasini yap

        // LlmReranker: Her belge icin LLM'e "0-10 arasi puan ver" diyor
        LlmReranker reranker = new LlmReranker(chatClientBuilder.clone());

        List<Document> reranked = reranker.process(new Query(q), retrieved); // Query nesnesi: reranker sorgu metnini bu sekilde aliyor

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("resultCount", reranked.size());
        response.put("results", mapDocuments(reranked));

        return response;
    }

    // Tam pipeline: Al → Puan → Cevapla
    @GetMapping("/rag-with-rerank")
    public Map<String, Object> ragWithRerank(@RequestParam(defaultValue = "embedding") String q) {

        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder(); // Metadata filtresi olusturucu
        // VectorStoreDocumentRetriever: QuestionAnswerAdvisor'dan daha modular — detay: B10.4
        // RetrievalAugmentationAdvisor ile kullanilabilen resmi retriever arayuzu
        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)                                             // Belgeler buradan alinacak
            .topK(INITIAL_TOP_K)                                                  // On 10 aday — rerank oncesi havuz
            .similarityThreshold(0.0)                                             // Esik yok — reranker zaten eleme yapacak
            .filterExpression(filterBuilder.eq("lesson", LESSON_CODE).build())   // Yalnizca b108 belgeleri
            .build();

        // LlmReranker: DocumentPostProcessor implement ediyor — asagida tanimliyoruz
        LlmReranker reranker = new LlmReranker(chatClientBuilder.clone()); // clone: taze builder, orijinal sagalim

        // RetrievalAugmentationAdvisor: Modular RAG mimarisi — retriever + postprocessor + augmenter — detay: B10.4
        RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
            .documentRetriever(retriever)              // 1. asama: Belge al
            .documentPostProcessors(reranker)          // 2. asama: Rerank et
            .queryAugmenter(ContextualQueryAugmenter.builder() // 3. asama: Soruyu baglam ile zenginlestir
                .allowEmptyContext(false)           // Baglamda hicbir sey yoksa cevap uretme
                .build())
            .build();

        // RAG client: Reranking pipeline'i entegre ChatClient
        ChatClient ragClient = chatClientBuilder.clone()                           // Taze builder kopyasi
            .defaultSystem("Yaniti baglama dayanarak ver. Bilgi yoksa bilmiyorum de.") // Hallucination onlemi
            .defaultAdvisors(advisor)                                          // Reranking pipeline aktif
            .build();

        String answer = ragClient.prompt()
            .advisors(spec -> spec.param("vector_store_filter_expression", "lesson == 'b108'")) // spec = AdvisorSpec
            .user(q)      // Kullanici sorusu
            .call()       // LLM'e senkron istek
            .content();   // Metin cevabini al

        // On izleme: Hangi belgeler rerank sonrasi one cikti?
        List<Document> rerankedPreview = reranker
            .process(new Query(q), searchLessonDocuments(q, INITIAL_TOP_K)) // Ham liste tekrar rerank
            .stream()
            .limit(2)  // Yalnizca ilk 2 — uzun ciktiyi onlemek icin
            .toList();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();

        response.put("query", q);
        response.put("resultCount", rerankedPreview.size());               // Gosterilen reranked belge sayisi (max 5)
        response.put("results", mapDocuments(rerankedPreview));            // Reranked belgeler + puanlar
        response.put("answer", answer);                                    // LLM'in nihai RAG cevabi

        return response;
    }

    private List<Document> searchLessonDocuments(String q, int topK) {
        SearchRequest request = SearchRequest.builder()
            .query(q)                                                        // Embedding uretilecek metin
            .topK(topK)                                                      // Kac belge donecek
            .similarityThresholdAll()                                        // Esik yok — reranker zaten eleme yapacak
            .filterExpression(new FilterExpressionBuilder().eq("lesson", LESSON_CODE).build()) // Yalnizca b108 belgeleri
            .build();
        return vectorStore.similaritySearch(request); // pgvector aramasini calistir
    }

    private List<Map<String, Object>> mapDocuments(List<Document> documents) {
        return documents.stream().<Map<String, Object>>map(doc -> {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>(); // LinkedHashMap: JSON sira koruma
            row.put("id", doc.getId());                  // Belge UUID'si
            row.put("text", doc.getText());              // Ham metin icerigi
            row.put("vectorScore", doc.getScore());      // Embedding benzerlik skoru (0-1)
            // rerankScore: Yalnizca LlmReranker'dan gecirilmis belgelerde bulunur (0-10 arasi)
            Object rerankScore = doc.getMetadata().get("rerankScore");
            if (rerankScore != null) {
                row.put("rerankScore", rerankScore); // Rerank uygulanmissa skoru goster (0-10)
            }
            // rerankRationale: LLM'in neden bu puani verdigi aciklamasi
            Object rationale = doc.getMetadata().get("rerankRationale");
            if (rationale != null) {
                row.put("rerankRationale", rationale); // Gerekce varsa goster — debug icin degerli
            }

            row.put("metadata", doc.getMetadata());    // Tam metadata — lesson, source gibi etiketler
            return row;
        }).toList();
    }


    // LLM RERANKER — DocumentPostProcessor implementasyonu
    // LlmReranker: after-rerank ve ragWithRerank endpointlerinden olusturuluyor
    // DocumentPostProcessor: Spring AI pipeline'inda post-retrieval asama arayuzu
    private static final class LlmReranker implements DocumentPostProcessor {

        // Logger: Parse hatalarini sessizce yutmuyoruz — uyari log'a yaziliyor
        private static final Logger log = LoggerFactory.getLogger(LlmReranker.class);

        // scoringClient: Her belge icin "SKOR|GEREKCE" formatinda puan isteyen ozel ChatClient
        private final ChatClient scoringClient;

        // Constructor: Puanlama isine ozel system prompt ile ChatClient olusturuyoruz
        // "0 ile 10 arasinda puanla" — LLM'e kesin format veriyoruz, serbest yorum istemiyoruz
        private LlmReranker(ChatClient.Builder chatClientBuilder) {
            this.scoringClient = chatClientBuilder
                .defaultSystem(                                         // Puanlama system prompt'u
                    "Sana verilen dokumani sorguya gore 0 ile 10 arasinda puanla. "
                        + "Format: SKOR|GEREKCE. "
                        + "Ornek: 8|Dogrudan ilgili")                  // Ornek format: boru ile ayrilan iki kisim
                .build();                                               // Immutable scoring client hazir
        }

        // process: DocumentPostProcessor arayuzunun zorunlu metodu — pipeline buraya cagiriyor
        @Override
        public List<Document> process(Query query, List<Document> documents) {
            return documents.stream()
                .map(doc -> scoreWithRationale(query.text(), doc))        // Her belgeyi tek tek LLM ile puan
                .sorted(Comparator.comparingDouble(ScoredDocument::score) // Puana gore azalan siralama
                    .reversed())
                .map(scored -> {
                    scored.document().getMetadata().put("rerankScore", scored.score());     // Puani metadata'ya kaydet
                    scored.document().getMetadata().put("rerankRationale", scored.rationale()); // Gerekceyi metadata'ya kaydet
                    return scored.document(); // Metadata zenginlestirilmis Document'i don
                })
                .toList();
        }

        // scoreWithRationale: process metodu tarafindan her belge icin cagriliyor
        private ScoredDocument scoreWithRationale(String query, Document document) {

            // LLM'e hem sorguyu hem belge metnini veriyoruz — format baglantisini netlestiriyor

            String prompt = "Query: " + query
                + "\nDokuman: " + document.getText()
                + "\nFormat: SKOR|GEREKCE"; // Cikti formatini tekrar hatirlatiyoruz

            String raw = scoringClient.prompt()
                .user(prompt)  // Puanlama isteği
                .call()        // LLM'e senkron istek
                .content();    // "8|Dogrudan ilgili" gibi ham yanit

            if (raw == null || raw.isBlank()) { // LLM bos donerse — aga sorunu veya timeout
                return new ScoredDocument(document, 0.0, "Cevap alinamadi");
            }

            String[] parts = raw.trim().split("\\|", 2); // Boru karakteri ile 2 kisima bol: [skor, gerekce]

            String rationale = parts.length > 1 ? parts[1].trim() : ""; // Gerekce kismi — yoksa bos string

            try {
                double score = Double.parseDouble(parts[0].trim().replace(',', '.')); // Virgul -> nokta: Turkce LLM uyumlulugu
                score = Math.max(0.0, Math.min(10.0, score)); // 0-10 arasiyla kisitla — LLM disina cikabilir
                return new ScoredDocument(document, score, rationale); // Basarili parse — belge + puan + gerekce

            } catch (NumberFormatException e) {
                // LLM bazen "Puan: 8" gibi beklenmedik format donebiliyor — sessiz yutmuyoruz
                log.warn("Reranker skoru parse edilemedi. raw={} reason={}", raw, e.getMessage());
                return new ScoredDocument(document, 0.0, "Parse hatasi"); // Guvenli varsayilan
            }
        }

        // ScoredDocument: LLM puanlama sonucunu tutan gecici veri yapisi
        // process metodu icinde stream operasyonlarinda kullaniliyor — disarida gerek yok
        private record ScoredDocument(
            Document document, // Orijinal belge — metadata eklenecek
            double score,      // LLM'den gelen puan (0-10) — siralamayi belirliyor
            String rationale   // LLM'in neden bu puani verdigi — debugging icin
        ) {

        }
    }
}