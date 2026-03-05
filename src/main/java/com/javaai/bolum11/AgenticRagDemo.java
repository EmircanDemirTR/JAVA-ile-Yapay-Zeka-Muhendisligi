package com.javaai.bolum11;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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
@RequestMapping(value = "/api/b114", produces = "application/json;charset=UTF-8")

public class AgenticRagDemo {

    private static final String LESSON_CODE = "b114";
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.3;

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;
    private final Bolum11SeedVeriHazirla seedService;


    // Agentic RAG'de ChatModel direkt KULLANMIYORUZ — chatClientBuilder yeterli.
    // Tum LLM islemleri tek bir agentic client uzerinden gidecek.
    // Tool ise VectorStore'a direkt erisecek — ChatModel'e ihtiyaci yok.
    public AgenticRagDemo(ChatClient.Builder chatClientBuilder,
        VectorStore vectorStore,
        Bolum11SeedVeriHazirla seedService) {
        this.chatClientBuilder = chatClientBuilder;
        this.vectorStore = vectorStore;
        this.seedService = seedService;
    }


    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        SpringApplication.run(AgenticRagDemo.class, args);
    }

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return seedService.seedLesson(LESSON_CODE);
    }

    @GetMapping("/agentic-answer")
    public Map<String, Object> agenticAnswer(
        @RequestParam(defaultValue = "Spring AI'da RAG ve Tool Calling arasindaki fark nedir?") String q
    ) {
        KnowledgeBaseTools tools = new KnowledgeBaseTools(vectorStore, LESSON_CODE);

        ChatClient agenticClient = chatClientBuilder.clone()
            .defaultSystem("Sen bir arastirma asistanisin. Soruyu cevaplamak icin bilgi tabanini kullan."
                + "Gerekirse birden fazla arama yap. Her aramayi farkli anahtar kelimelerle gerceklestir.")
            .defaultTools(tools)
            .build();

        String answer = agenticClient.prompt()
            .user(q)
            .call()
            .content();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("answer", answer);

        return response;
    }


    private List<Document> searchLessonDocuments(String q, int topK, double threshold) {
        FilterExpressionBuilder fb = new FilterExpressionBuilder();
        return vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(q)
                .topK(topK)
                .similarityThreshold(threshold)
                .filterExpression(fb.eq("lesson", LESSON_CODE).build())
                .build());
    }

    private List<Map<String, Object>> mapDocuments(List<Document> documents) {
        return documents.stream()
            .map(doc -> Map.of(
                "id", doc.getId(),                    // Belge UUID'si
                "score", doc.getScore(),              // Benzerlik skoru (0-1 arasi)
                "topic", doc.getMetadata().get("topic"), // Konu metadata'si
                "text", doc.getText()                 // Belge icerigi
            ))
            .toList(); // Liste olarak don — detay: stream().toList() Java 16+
    }

    public static class KnowledgeBaseTools {

        private final VectorStore vectorStore;
        private final String lessonCode;

        public KnowledgeBaseTools(VectorStore vectorStore, String lessonCode) {
            this.vectorStore = vectorStore;
            this.lessonCode = lessonCode;
        }

        @Tool(description = "Bilgi tabanında verilen sorguyla arama yapar ve ilgili dokumanları döndürür")
        public String searchKnowledgeBase(
            @ToolParam(description = "Aranacak sorgu metni") String query,
            @ToolParam(description = "Dönecek maksimum sonuc sayisi") int topK
        ) {
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            int clampedTopK = Math.max(1, Math.min(topK, 5));

            SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(clampedTopK)
                .similarityThreshold(0.3)
                .filterExpression(fb.eq("lesson", lessonCode).build())
                .build();

            List<Document> results = vectorStore.similaritySearch(request);

            if (results.isEmpty()) {
                return "Bilgi tabanında bu sorguyla ilgili dokuman bulunamadi";
            }

            return results.stream().
                map(doc -> String.format("[%s] %s,",
                    doc.getMetadata().get("topic"),
                    doc.getText()))
                .collect(Collectors.joining("\n--\n"));
        }
    }
}
