package com.javaai.bolum11;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
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
@Import(Bolum11SeedVeriHazirla.class)
@RestController
@RequestMapping(value = "/api/b111", produces = "application/json;charset=UTF-8")
public class CorrectiveRagDemo {


    private static final String LESSON_CODE = "b111";
    private static final int DEFAULT_TOP_K = 3;
    private static final double QUALITY_THRESHOLD_DEFAULT = 0.6;

    private final ChatClient.Builder chatClientBuilder;
    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final Bolum11SeedVeriHazirla seedService;

    public CorrectiveRagDemo(ChatClient.Builder chatClientBuilder,
        ChatModel chatModel,
        VectorStore vectorStore,
        Bolum11SeedVeriHazirla seedService) {
        this.chatClientBuilder = chatClientBuilder;
        this.chatModel = chatModel;           // CragQualityFilter'a aktarilacak
        this.vectorStore = vectorStore;       // Retrieval buraya gidecek
        this.seedService = seedService;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        SpringApplication.run(CorrectiveRagDemo.class, args);
    }


    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return seedService.seedLesson(LESSON_CODE);
    }


    @GetMapping("/baseline-rag")
    public Map<String, Object> baselineRag(
        @RequestParam(defaultValue = "Spring AI'da ChatClient ne ise yarar?") String q) {

        // QuestionAnswerAdvisor — standart RAG advisor; retrieval + augmentation
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore) // Hangi depoya bak
            .searchRequest(SearchRequest.builder()
                .topK(DEFAULT_TOP_K)
                .similarityThreshold(0.3)
                .build())
            .build();

        // Klasik RAG client — kalite filtresi olmadan, her belge LLM'e ulasiyor
        String answer = chatClientBuilder.clone()
            .defaultSystem("Yaniti kisa ve baglama dayali ver.")
            .defaultAdvisors(advisor)
            .build()
            .prompt()
            .advisors(spec -> spec.param("qa_filter_expression", String.format("lesson == '%s'", LESSON_CODE)))
            .user(q)
            .call()
            .content();

        // Kac belge bulundu — CRAG karsilastirmasi icin raporluyoruz
        int resultCount = vectorStore.similaritySearch(SearchRequest.builder().query(q).topK(DEFAULT_TOP_K)
            .similarityThreshold(0.3).filterExpression(new FilterExpressionBuilder().eq("lesson", LESSON_CODE).build()).build()).size();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("resultCount", resultCount);
        response.put("answer", answer);
        return response;
    }


    @GetMapping("/crag-answer")
    public Map<String, Object> cragAnswer(
        @RequestParam(defaultValue = "Spring AI'da RAG nedir?") String q,
        @RequestParam(defaultValue = "0.6") double threshold
    ) {
        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .topK(DEFAULT_TOP_K)
            .similarityThreshold(0.0)
            .filterExpression(new FilterExpressionBuilder()
                .eq("lesson", LESSON_CODE)
                .build())
            .build();

        CragQualityFilter qualityFilter = new CragQualityFilter(chatModel, threshold);

        RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
            .documentRetriever((DocumentRetriever) retriever) // Belge cekme islemi retriever'a gidecek
            .documentPostProcessors(qualityFilter) // Kalite filtresi burada devreye girecek
            .queryAugmenter((QueryAugmenter) ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .build())
            .build();

        ChatClient cragClient = chatClientBuilder.clone()
            .defaultSystem("Asagidaki baglami kullanarak soruyu cevapla. Baglam yeterliyse kisa ve net yanit ver.")
            .defaultAdvisors(advisor)
            .build();

        String answer = cragClient.prompt()
            .user(q)
            .call()
            .content();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("threshold", threshold);
        response.put("totalRetrieved", qualityFilter.getTotalRetrieved()); // Toplam aday belge sayisi
        response.put("qualityScores", qualityFilter.getQualityScores()); // Her belgenin puani ve gecti mi
        response.put("filteredCount", qualityFilter.getFilteredCount()); // Esigi gecen belge sayisi
        response.put("fallbackUsed", qualityFilter.isFallbackUsed());    // Web fallback tetiklendi mi
        response.put("answer", answer);                                // Uretilen yanit

        return response;

    }


    static class CragQualityFilter implements DocumentPostProcessor {

        private final ChatModel chatModel;
        private final double qualityThreshold;

        private int totalRetrieved = 0; // Toplam bulunan belge sayisi
        private List<Map<String, Object>> qualityScores = new ArrayList<>(); // Belge kalite skorlarini tutacak liste
        private int filteredCount = 0; // Kalite filtresinden gecemeyen belge sayisi
        private boolean fallbackUsed = false; // Fallback mekanizmasinin kullanilip kullanilmadigini takip eder

        CragQualityFilter(ChatModel chatModel, double qualityThreshold) {
            this.chatModel = chatModel;
            this.qualityThreshold = qualityThreshold;
        }

        @Override
        public List<Document> process(Query query, List<Document> documents) {
            totalRetrieved = documents.size();
            List<Document> qualified = new ArrayList<>();
            qualityScores.clear();

            for (Document doc : documents) {
                double score = evaluateQuality(query.text(), doc.getText()); // LLM ile puan al

                LinkedHashMap<String, Object> scoreEntry = new LinkedHashMap<>();
                scoreEntry.put("snippet", doc.getText().substring(0, Math.min(80, doc.getText().length())));
                scoreEntry.put("score", score);
                scoreEntry.put("passed", score >= qualityThreshold);
                qualityScores.add(scoreEntry);

                if (score >= qualityThreshold) {
                    qualified.add(doc);
                }
            }

            filteredCount = qualified.size();
            if (qualified.isEmpty()) {
                fallbackUsed = true;
                String fallbackResult = mockWebSearch(query.text()); // Gercek bir web aramasi yapilabilir
                return List.of(new Document(fallbackResult, Map.of("source", "web-fallback")));
            }
            return qualified;
        }

        private String mockWebSearch(String query) {
            // Gercek bir web aramasi yapilabilir; simule etmek icin sabit bir cevap donuyoruz
            return "Bu, '" + query + "' sorusu icin web'den gelen fallback cevaptir.";
        }

        private double evaluateQuality(String question, String documentText) {
            String prompt = String.format("Soru %s\n\nBelge: %s\n\nBu belge soruyla ne kadar ilgili? 0 ile 1 arasında sayi yaz.", question,
                documentText);

            String rawScore = ChatClient.builder(chatModel)
                .defaultSystem("Sen bir belge kalite hakemisin. SADECE 0 ile 1 arasında ondalik sayi yaz. Baska hicbir sey YAZMA.")
                .build().prompt().user(prompt).call().content().trim();

            try {
                double parsed = Double.parseDouble(rawScore);
                return Math.max(0.0, Math.min(1.0, parsed));
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }

        public int getTotalRetrieved() {
            return totalRetrieved;
        }

        public List<Map<String, Object>> getQualityScores() {
            return qualityScores;
        }

        public int getFilteredCount() {
            return filteredCount;
        }

        public boolean isFallbackUsed() {
            return fallbackUsed;
        }
    }

}