package com.javaai.bolum11;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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
@RequestMapping(value = "/api/b112", produces = "application/json;charset=UTF-8")

public class SelfRagDemo {

    private static final String LESSON_CODE = "b112";
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.3;
    private static final int MAX_ITERATIONS_DEFAULT = 3; // Self-RAG dongusunun varsayilan maksimum adim sayisi

    private final ChatClient.Builder chatClientBuilder;
    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final Bolum11SeedVeriHazirla seedService;


    public SelfRagDemo(ChatClient.Builder chatClientBuilder,
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
        SpringApplication.run(SelfRagDemo.class, args);
    }

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return seedService.seedLesson(LESSON_CODE);
    }

    @GetMapping("/needs-retrieval")
    public Map<String, Object> needsRetrievalEndpoint(
        @RequestParam(defaultValue = "Java'da stream API nasil calisir?") String q
    ) {
        boolean needed = needsRetrieval(q);

        LinkedHashMap<String, Object> resp = new LinkedHashMap<>();
        resp.put("query", q);
        resp.put("retrievalNeeded", needed);
        resp.put("rawResponse", needed ? "YES" : "NO");
        return resp;
    }

    @GetMapping("/self-rag")
    public Map<String, Object> selfRag(
        @RequestParam(defaultValue = "Spring AI'da VectorStore nasil calisir?") String q,
        @RequestParam(defaultValue = "3") int maxIterations
    ) {
        // 1. ADIM - Retrieval gerekli mi
        boolean retrievalNeeded = needsRetrieval(q);

        // Retrieval gerekmiyorsa
        if (!retrievalNeeded) {
            String directAnswer = ChatClient.builder(chatModel)
                .build()
                .prompt()
                .user(q)
                .call()
                .content();
            LinkedHashMap<String, Object> resp = new LinkedHashMap<>();
            resp.put("query", q);
            resp.put("retrievalNeeded", false);
            resp.put("iterations", 0);
            resp.put("iyilestirme", List.of());
            resp.put("finalAnswer", directAnswer);
            return resp;
        }

        // Retrival gerekiyorsa
        List<String> iyilestirilmisSorgular = new ArrayList<>();
        String simdikiSorgu = q;
        String finalAnswer = "";
        int iterations = 0;

        for (int i = 0; i < maxIterations; i++) {
            iterations++;

            QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                    .topK(DEFAULT_TOP_K)
                    .similarityThreshold(DEFAULT_THRESHOLD)
                    .build())
                .build();

            finalAnswer = chatClientBuilder.clone()
                .defaultSystem("Yalnizca verilen baglama dayanarak cevap ver. Kisa ve net ol.")
                .defaultAdvisors(advisor) // RAG advisor
                .build()
                .prompt()
                .advisors(spec -> spec.param(
                    "qa_filter_expression", String.format("lesson == '%s'", LESSON_CODE)))
                .user(simdikiSorgu)
                .call()
                .content();

            // ADIM 3 - Yanıt yeterli mi
            if (isAnswerSatisfactory(simdikiSorgu, finalAnswer)) {
                break;
            }

            if (i >= maxIterations - 1) {
                continue;
            }

            // ADIM 4 - Sorguyu İyileştirme
            String refined = refineQuery(simdikiSorgu, finalAnswer);
            iyilestirilmisSorgular.add(refined);
            simdikiSorgu = refined;
        }

        LinkedHashMap<String, Object> resp = new LinkedHashMap<>();
        resp.put("query", q);
        resp.put("retrievalNeeded", true);
        resp.put("iterations", iterations);
        resp.put("iyilestirme", iyilestirilmisSorgular);
        resp.put("finalAnswer", finalAnswer);
        return resp;
    }

    private boolean needsRetrieval(String query) {
        String decision = ChatClient.builder(chatModel)
            .build()
            .prompt()
            .user(String.format("Bu soruyu cevaplamak icin dis kaynak gerekli mi? SADECE, YES veya NO yaz. \n Soru: %s", query))
            .call().content();
        return decision != null && decision.toUpperCase().contains("YES");
    }

    private boolean isAnswerSatisfactory(String query, String answer) {
        String judgment = ChatClient.builder(chatModel)
            .build()
            .prompt()
            .user(String.format("Soru: %s\nCevap: %s\nBu cevap soruyu yeterince karsiladi mi? SADECE YES veya NO yaz.", query, answer))
            .call()
            .content();
        return judgment != null && judgment.toUpperCase().contains("YES");
    }

    private String refineQuery(String query, String previousAnswer) {
        String refined = ChatClient.builder(chatModel)
            .build()
            .prompt()
            .user(String.format("Bu soru yeterince cevaplanamadi. Soruyu daha spesifik hale getir. Sadece yeni soruyu yaz.\n"
                + "Orijinal: %s\nYetersiz Cevap: %s", query, previousAnswer))
            .call().content();

        return (refined != null && !refined.isBlank()) ? refined.trim() : query;
    }
}