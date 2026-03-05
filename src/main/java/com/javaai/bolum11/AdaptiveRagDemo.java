package com.javaai.bolum11;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
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
@Import(Bolum11SeedVeriHazirla.class)
@RestController
@RequestMapping(value = "/api/b113", produces = "application/json;charset=UTF-8")

public class AdaptiveRagDemo {

    private static final String LESSON_CODE = "b113";
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.3;

    private final ChatClient.Builder chatClientBuilder;
    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final Bolum11SeedVeriHazirla seedService;

    public AdaptiveRagDemo(ChatClient.Builder chatClientBuilder,
        ChatModel chatModel,
        VectorStore vectorStore,
        Bolum11SeedVeriHazirla seedService) {
        this.chatClientBuilder = chatClientBuilder;
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.seedService = seedService;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        SpringApplication.run(AdaptiveRagDemo.class, args);
    }

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return seedService.seedLesson(LESSON_CODE);
    }


    @GetMapping("/classify") // GET: LLM ile sorgu karmasikligini siniflandir — SIMPLE/MODERATE/COMPLEX
    public Map<String, Object> classify(
        @RequestParam(defaultValue = "Spring AI nedir?") String q) {

        QueryComplexity complexity = classifyQuery(q); // LLM ile siniflandir — asagida tanimliyoruz

        String description = switch (complexity) {
            case SIMPLE -> "Kisa, tekil soru. Retrieval gereksiz — dogrudan LLM yeterli."; // Triyaj: aile hekimi
            case MODERATE -> "Orta karmasiklik. QuestionAnswerAdvisor ile standart RAG.";    // Uzman polikinigi
            case COMPLEX -> "Cok faktorlu soru. CRAG-hafif kalite filtrelemesi uygulanacak."; // Ameliyathane
        };

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("complexity", complexity); // SIMPLE / MODERATE / COMPLEX
        response.put("description", description); // Strateji aciklamasi
        return response;
    }


    @GetMapping("/adaptive-answer") // GET: siniflandir → strateji sec → yanit uret
    public Map<String, Object> adaptiveAnswer(
        @RequestParam(defaultValue = "Spring AI'da RetrievalAugmentationAdvisor nasil calisir?") String q) {

        long start = System.nanoTime(); // Latency olcumu baslat — ms cinsinden raporlanacak

        QueryComplexity complexity = classifyQuery(q); // LLM ile karmasiklik siniflandir

        // Triyaj kararina gore dogru odaya yonlendir
        String answer;
        String selectedStrategy;

        if (complexity == QueryComplexity.SIMPLE) {
            answer = executeSimpleStrategy(q);                               // Direkt LLM — hizli
            selectedStrategy = "SIMPLE — Dogrudan LLM (retrieval yok, hizli)"; // Triyaj: aile hekimi
        } else if (complexity == QueryComplexity.MODERATE) {
            answer = executeModerateStrategy(q);                             // Standart RAG
            selectedStrategy = "MODERATE — QuestionAnswerAdvisor ile standart RAG"; // Uzman
        } else {
            answer = executeComplexStrategy(q);                              // Filtreli retrieval
            selectedStrategy = "COMPLEX — CRAG-hafif (ilgi filtreli retrieval + uretim)"; // Ameliyat
        }

        long latencyMs = (System.nanoTime() - start) / 1_000_000L; // ns → ms cevirimi

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);                    // Orijinal sorgu
        response.put("complexity", complexity);       // Hangi kategoriye girdi
        response.put("selectedStrategy", selectedStrategy); // Secilen strateji aciklamasi
        response.put("answer", answer);              // Uretilen yanit
        response.put("latencyMs", latencyMs);        // Toplam sure (siniflandirma dahil)
        response.put("notes", "SIMPLE en hizli, COMPLEX en kapsamli. latencyMs bunu yansitir.");
        return response; // complexity + selectedStrategy + answer + latencyMs
    }


    private QueryComplexity classifyQuery(String q) {
        String raw = ChatClient.builder(chatModel) // Ham model — basit siniflandirma cagrisi
            .defaultSystem("Yalnizca SIMPLE, MODERATE veya COMPLEX kelimesini yaz. Baska hic bir sey yazma.")
            .build() // Sade tek amacli client
            .prompt()
            .user(String.format(
                "Asagidaki sorunun karmasiklik seviyesini belirle. SADECE SIMPLE, MODERATE veya COMPLEX yaz. Sadece seviyeyi yaz.\nSoru: %s", q))
            .call()
            .content()
            .trim()
            .toUpperCase();

        try {
            return QueryComplexity.valueOf(raw); // Ham yanittan enum degeri uret
        } catch (IllegalArgumentException e) {
            // LLM beklenmedik bir sey yazarsa — guvenli varsayilan: MODERATE
            // "Bilinmeyende ortaya git" prensibi — ne cok hafif ne cok agir
            return QueryComplexity.MODERATE;
        }
    }


    // SIMPLE strateji — en sade, en hizli. Retrieval yok, sadece LLM.
    private String executeSimpleStrategy(String q) {
        return ChatClient.builder(chatModel) // Ham model — en sade yapilandirma
            .defaultSystem("Kisa ve net cevap ver.") // Kisaltilmis yanit istiyoruz
            .build() // Sade client — advisor yok, retrieval yok
            .prompt()
            .user(q)    // Basit soru
            .call()     // LLM'e istek — sadece egitim verisiyle yanit
            .content(); // Yanit
    }

    // MODERATE strateji — QuestionAnswerAdvisor ile klasik RAG
    private String executeModerateStrategy(String q) {
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore) // RAG advisor
            .searchRequest(SearchRequest.builder()
                .topK(DEFAULT_TOP_K)        // 5 belge
                .similarityThreshold(DEFAULT_THRESHOLD) // 0.3 esik
                .build())
            .build(); // Advisor hazir

        return chatClientBuilder.clone() // Konfigureli builder kopyasi — orijinal bozulmaz
            .defaultSystem("Yaniti kisa, net ve baglama sadik ver.")
            .defaultAdvisors(advisor) // RAG advisor — retrieval + augmentation
            .build() // Advisor'li client hazir
            .prompt()
            .advisors(spec -> spec.param(           // Advisor parametresi — filtre expression
                "qa_filter_expression",
                String.format("lesson == '%s'", LESSON_CODE))) // Bu derse ait belgeler
            .user(q)    // Orta karmasik soru
            .call()
            .content(); // RAG destekli yanit
    }

    // COMPLEX strateji — CRAG-hafif: belgeleri LLM ile ikinci kez filtrele, kalan ile yanit uret.
    private String executeComplexStrategy(String q) {
        // Adim 1: Genis ag ile aday belgeleri getir
        List<Document> candidates = searchLessonDocuments(q, DEFAULT_TOP_K, DEFAULT_THRESHOLD);

        // Adim 2: Her belgeyi LLM ile "ilgili mi?" diye puan — 1 = ilgili, 0 = degil
        ChatClient scorerClient = ChatClient.builder(chatModel) // Tek amacli puanlama istemcisi
            .defaultSystem("Yalnizca 0 veya 1 yaz. Baska hic bir sey yazma.") // Katı format
            .build();

        List<Document> relevant = new ArrayList<>(); // Ilgili bulunan belgeler buraya gidecek

        for (Document doc : candidates) { // Her aday belgeyi filtreden gecir
            String score = scorerClient.prompt()
                .user(String.format("Soru: %s\nBelge: %s\nBu belge soruya ilgili mi? 1 (ilgili) veya 0 (ilgisiz) yaz.",
                    q, doc.getText()))
                .call()
                .content() // "0" veya "1" bekliyoruz
                .trim();   // Bosluk temizle

            if ("1".equals(score)) { // LLM "1" dediyse — ilgili, ekle
                relevant.add(doc);   // Ilgili belge listeye giriyor
            }
            // "0" veya baska bir sey geldiyse — belge atiliyor, sessiz devam
        }

        if (relevant.isEmpty()) { // Hic ilgili belge bulunamadi — SIMPLE'a dusur
            return executeSimpleStrategy(q); // Fallback: genel bilgiyle yanit ver
        }

        // Adim 3: Ilgili belgelerden context olustur ve LLM'e ver
        StringBuilder ctx = new StringBuilder(); // String birlesimi icin StringBuilder
        for (Document doc : relevant) {
            ctx.append(doc.getText()).append("\n\n"); // Her belge aralarinda boslukla
        }

        return ChatClient.builder(chatModel) // Context ile guclendirilen yanit istemcisi
            .defaultSystem("Yalnizca asagidaki baglamdaki bilgileri kullanarak cevap ver. Baglam disina cikma.")
            .build() // Filtreli context ile donanimli client
            .prompt()
            .user(String.format("Baglam:\n%s\nSoru: %s",
                ctx.toString().trim(), q)) // Filtreli belgeler + kullanici sorusu
            .call()
            .content(); // COMPLEX strateji yaniti — kaynak kontrollü
    }


    private List<Document> searchLessonDocuments(String q, int topK, double threshold) {
        FilterExpressionBuilder fb = new FilterExpressionBuilder();
        return vectorStore.similaritySearch( // pgvector'de benzerlik araması
            SearchRequest.builder()
                .query(q)
                .topK(topK)
                .similarityThreshold(threshold)
                .filterExpression(fb.eq("lesson", LESSON_CODE).build())
                .build());
    }


    private enum QueryComplexity {
        SIMPLE,   // Kisa, bilinen sorgu — direkt LLM, retrieval yok (aile hekimi)
        MODERATE, // Orta karmasik — standart QuestionAnswerAdvisor RAG (uzman)
        COMPLEX   // Cok faktorlu — LLM destekli belge filtrelemesi (ameliyat)
    }

}