package com.javaai.bolum10;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
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
@RequestMapping(value = "/api/b109", produces = "application/json;charset=UTF-8")

public class CokKaynakliRagDemo {

    private static final String LESSON_CODE = "b109";

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;
    private final Bolum10SeedVeriHazirla seedService;

    public CokKaynakliRagDemo(ChatClient.Builder chatClientBuilder,
        VectorStore vectorStore,
        Bolum10SeedVeriHazirla seedService) {
        this.chatClientBuilder = chatClientBuilder;
        this.vectorStore = vectorStore;
        this.seedService = seedService;
    }


    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        SpringApplication.run(CokKaynakliRagDemo.class, args);
    }


    @PostMapping("/seed-multi")
    public Map<String, Object> seedMulti() {
        return seedService.seedLesson(LESSON_CODE);
    }


    @GetMapping("/cite")
    public Map<String, Object> cite(
        @RequestParam(defaultValue = "Spring AI'da VectorStore turleri nelerdir?") String q
    ) {
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore)
            .searchRequest(SearchRequest.builder()
                .topK(2)
                .similarityThreshold(0.5)
                .build())
            .build();

        ChatClient citationClient = chatClientBuilder.clone()
            .defaultSystem("Soruları SADECE verilen baglama dayanarak cevapla."
                + "Her iddianın sonunda, bilgiyi aldıgın belgenin source ve page metadata'sını kullanarak"
                + "referans ekle. Ornek: [Kaynak: spring-ai-docs.pdf, Sayfa: 15]."
                + "Baglamda bilgi yoksa 'Bu konuda yeterli bilgim yok' de."
                + "Asla uydurma veya tahmin etme.")
            .defaultOptions(ChatOptions.builder()
                .temperature(0.0)
                .build())
            .defaultAdvisors(advisor)
            .build();

        String answer = citationClient.prompt()
            .advisors(spec -> spec.param("qa_filter_expression", "lesson == 'b109'"))
            .user(q)
            .call()
            .content();

        List<Document> retrieved = searchLessonDocuments(q, 2, 0.5);

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("question", q);
        response.put("resultCount", retrieved.size());
        response.put("results", mapDocuments(retrieved));
        response.put("answer", answer);
        
        return response;
    }


    // searchLessonDocuments: cite endpoint'inin eslesen belgeleri gostermesi icin kullaniliyor
    private List<Document> searchLessonDocuments(String q, int topK, double threshold) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
        SearchRequest request = SearchRequest.builder()
            .query(q)                                // Embedding uretilecek metin
            .topK(topK)                              // Donulecek maksimum belge sayisi
            .similarityThreshold(threshold)          // Gecis esigi — disaridan kontrol ediliyor
            .filterExpression(filterBuilder.eq("lesson", LESSON_CODE).build()) // Yalnizca b109 belgeleri
            .build();
        return vectorStore.similaritySearch(request); // pgvector aramasini calistir
    }


    // mapDocuments: Document listesini Swagger'da okunabilir JSON'a cevirir
    private List<Map<String, Object>> mapDocuments(List<Document> documents) {
        return documents.stream()
            .map(doc -> Map.of(
                "id", doc.getId(),
                "score", doc.getScore(),
                "text", doc.getText(),
                "metadata", doc.getMetadata()
            ))
            .toList();
    }
}