package com.javaai.bolum10;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
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
@RequestMapping(value = "/api/b104", produces = "application/json;charset=UTF-8")

public class ModularRagDemo {

    private static final String LESSON_CODE = "b104";

    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.3;

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;
    private final Bolum10SeedVeriHazirla seedService;


    public ModularRagDemo(ChatClient.Builder chatClientBuilder,
        VectorStore vectorStore,
        Bolum10SeedVeriHazirla seedService) {
        this.chatClientBuilder = chatClientBuilder;
        this.vectorStore = vectorStore;
        this.seedService = seedService;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        SpringApplication.run(ModularRagDemo.class, args);
    }

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return seedService.seedLesson(LESSON_CODE);  // DELETE + INSERT — idempotent
    }

    // retrieval detayini gostermek icin kullanir.
    private List<Document> searchLessonDocuments(String q, int topK, double threshold) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder(); // JSONB filtre olusturucu
        SearchRequest request = SearchRequest.builder()
            .query(q)                                                       // Aranacak metin
            .topK(topK)                                                     // Maksimum belge sayisi
            .similarityThreshold(threshold)                                 // Minimum benzerlik esigi
            .filterExpression(filterBuilder.eq("lesson", LESSON_CODE).build()) // Sadece b104
            .build();
        return vectorStore.similaritySearch(request);  // pgvector ile benzerlik aramasi yap
    }


    @GetMapping("/modular")
    public Map<String, Object> modular(
        @RequestParam(defaultValue = "RAG pipeline adimlari nelerdir?") String q
    ) {
        RetrievalAugmentationAdvisor modularAdvisor = buildModularAdvisor();

        ChatClient modularClient = chatClientBuilder.clone()
            .defaultSystem("Yaniti baglamdan sapmadan ver")
            .defaultAdvisors(modularAdvisor)
            .build();

        String answer = modularClient.prompt()
            .advisors(spec -> spec.param("vector_store_filter_expression", "lesson == 'b104'"))
            .user(q)
            .call()
            .content();

        List<Document> retrieved = searchLessonDocuments(q, DEFAULT_TOP_K, DEFAULT_THRESHOLD);

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("resultCount", retrieved.size()); // Bulunan belge sayisi
        response.put("results", mapDocuments(retrieved)); // Belge detaylari
        response.put("answer", answer); // RAG cevabi

        return response;
    }

    private RetrievalAugmentationAdvisor buildModularAdvisor() {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();

        // 1. İstasyon - Retriever
        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .similarityThreshold(DEFAULT_THRESHOLD)
            .topK(DEFAULT_TOP_K)
            .filterExpression(filterBuilder.eq("lesson", LESSON_CODE).build())
            .build();

        // 2. İstasyon - Augmenter
        ContextualQueryAugmenter augmenter = ContextualQueryAugmenter.builder()
            .allowEmptyContext(false) // Boş bağlama izin verme
            .build();

        return RetrievalAugmentationAdvisor.builder()
            .documentRetriever(retriever) // 1. Adım - Belgeyi getir
            .queryAugmenter(augmenter) // 2. Adım - Soruya bağlam ekle
            .build();
    }


    // Document listesini JSON-serializasyon dostu Map listesine donusturur.
    private List<Map<String, Object>> mapDocuments(List<Document> documents) {
        return documents.stream()
            .map(doc -> Map.of(
                "id", doc.getId(),              // VectorStore UUID'i
                "score", doc.getScore(),        // Benzerlik skoru (0-1)
                "text", doc.getText(),          // Belge metni — baglam icerigi
                "metadata", doc.getMetadata()   // lesson, topic, source alanlari
            ))
            .toList();
    }
}