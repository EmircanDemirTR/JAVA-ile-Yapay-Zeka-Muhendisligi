package com.javaai.bolum10;

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
@Import(Bolum10SeedVeriHazirla.class)
@RestController
@RequestMapping(value = "/api/b102", produces = "application/json; charset=utf-8")

public class IlkRagDemo {

    private static final String LESSON_CODE = "b102";
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.3;

    private final ChatClient.Builder chatClientBuilder;
    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final Bolum10SeedVeriHazirla seedService;

    public IlkRagDemo(ChatClient.Builder chatClientBuilder, ChatModel chatModel, VectorStore vectorStore, Bolum10SeedVeriHazirla seedService) {
        this.chatClientBuilder = chatClientBuilder;
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.seedService = seedService;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector"); // pgvector profili: PostgreSQL + pgvector uzantisi aktif edilir
        SpringApplication.run(IlkRagDemo.class, args);
    }

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return this.seedService.seedLesson(LESSON_CODE);
    }

    @GetMapping("/rag")
    public Map<String, Object> rag(
        @RequestParam(defaultValue = "Spring AI'da Chatlient ne ise yarar?") String q
    ) {
        if (q.isBlank()) {
            return Map.of("error", "Soru boş olamaz");
        }

        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(this.vectorStore) // Hangi depodan arama yapacağımızı belirtiyoruz
            .searchRequest(SearchRequest.builder()
                .topK(DEFAULT_TOP_K)
                .similarityThreshold(DEFAULT_THRESHOLD)
                .build())
            .build();

        ChatClient ragClient = this.chatClientBuilder.clone()
            .defaultSystem("Yaniti kisa, net ve baglama sadik ver")
            .defaultAdvisors(advisor)
            .build();

        String filterExpression = "lesson == '" + LESSON_CODE + "'"; // Sadece belirli bir derse ait verileri çekmek için filtre ifadesi

        String answer = ragClient.prompt()
            .advisors(spec -> spec.param(QuestionAnswerAdvisor.FILTER_EXPRESSION,
                filterExpression)) // Advisor'a filtre ifadesini parametre olarak geçiyoruz
            .user(q)
            .call()
            .content();

        List<Document> retrived = searchLessonDocuments(q, DEFAULT_TOP_K, DEFAULT_THRESHOLD);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("resultCount", retrived.size());
        response.put("results", mapDocuments(retrived));
        response.put("answer", answer);

        return response;
    }


    @GetMapping("/compare")
    public Map<String, Object> compare(
        @RequestParam(defaultValue = "VectorStore ne ise yarar?") String q
    ) {
        if (q.isBlank()) {
            return Map.of("lesson", "B10.2", "error", "q bos olamaz");
        }

        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(this.vectorStore)
            .searchRequest(SearchRequest.builder().topK(DEFAULT_TOP_K).similarityThreshold(DEFAULT_THRESHOLD).build())
            .build();

        ChatClient ragClient = this.chatClientBuilder.clone()              // Builder clone edilir; /rag akisi kirletilmez
            .defaultSystem("Yalnizca baglama dayanarak cevap ver.")    // Retrieval kontekstine sadik kalmasi emredilir
            .defaultAdvisors(advisor)                                      // RAG zinciri: retrieval → prompt augmentation → generation
            .build();                                                      // Immutable ragClient olusturulur

        String ragAnswer = ragClient.prompt()
            .advisors(spec -> spec.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "lesson == '" + LESSON_CODE + "'"))
            .user(q)    // Kullanici sorusu eklenir
            .call()     // Retrieval + generation senkron olarak calisir
            .content(); // Yalnizca metin alinir

        ChatClient plainClient = ChatClient.builder(this.chatModel)
            .defaultSystem("Yalnizca kullanicinin sorusunu cevapla.")
            .build();

        String plainAnswer = plainClient.prompt()
            .user(q)
            .call()
            .content();

        List<Document> retrieved = searchLessonDocuments(q, DEFAULT_TOP_K, DEFAULT_THRESHOLD); // Hangi belgeler retrieval edildi

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("resultCount", retrieved.size());
        response.put("results", mapDocuments(retrieved));
        response.put("ragAnswer", ragAnswer);
        response.put("plainAnswer", plainAnswer);
        return response;
    }


    private List<Document> searchLessonDocuments(String q, int topK, double threshold) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();

        SearchRequest request = SearchRequest.builder()
            .query(q)
            .topK(topK)
            .similarityThreshold(threshold)
            .filterExpression(filterBuilder.eq("lesson", LESSON_CODE).build())
            .build();

        return this.vectorStore.similaritySearch(request); // Embedding benzerlik aramasi; List<Document> olarak doner
    }

    private List<Map<String, Object>> mapDocuments(List<Document> documents) {
        return documents.stream()
            .map(document -> Map.<String, Object>of(
                "id", document.getId(),
                "score", document.getScore(),
                "text", document.getText(),
                "metadata", document.getMetadata()
            ))
            .toList();
    }

}
