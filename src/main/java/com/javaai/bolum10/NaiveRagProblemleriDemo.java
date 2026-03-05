package com.javaai.bolum10;

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
@RequestMapping(value = "/api/b103", produces = "application/json; charset=utf-8")

public class NaiveRagProblemleriDemo {

    private static final String LESSON_CODE = "b103";
    private static final int DEFAULT_TOP_K = 10;
    private static final double DEFAULT_THRESHOLD = 0.3;
    private static final int CONTEXT_POLLUTION_TOP_K = 20;
    private static final double CONTEXT_POLLUTION_THRESHOLD = 0.0;

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;
    private final Bolum10SeedVeriHazirla seedService;

    public NaiveRagProblemleriDemo(ChatClient.Builder chatClientBuilder, ChatModel chatModel, VectorStore vectorStore,
        Bolum10SeedVeriHazirla seedService) {
        this.chatClientBuilder = chatClientBuilder;
        this.vectorStore = vectorStore;
        this.seedService = seedService;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        org.springframework.boot.SpringApplication.run(NaiveRagProblemleriDemo.class, args);
    }

    @PostMapping("/seed-noise")
    public Map<String, Object> seedNoise() {
        return this.seedService.seedLesson(LESSON_CODE);
    }

    @GetMapping("/naive-test")
    public Map<String, Object> naiveTest(
        @RequestParam(defaultValue = "Spring AI nedir?") String q,
        @RequestParam(defaultValue = "10") int topK,
        @RequestParam(defaultValue = "0.3") double threshold
    ) {
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(this.vectorStore)
            .searchRequest(SearchRequest.builder().topK(topK).similarityThreshold(threshold).build())
            .build();

        ChatClient ragClient = this.chatClientBuilder.clone()
            .defaultSystem("Yaniti baglam odakli ver")
            .defaultAdvisors(advisor)
            .build();

        String answer = ragClient.prompt()
            .advisors(spec -> spec.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "lesson == '" + LESSON_CODE + "'"))
            .user(q)
            .call()
            .content();

        List<Document> rawRetrieved = searchLessonDocuments(q, topK, threshold);
        List<Document> retrieved = deduplicateDocuments(rawRetrieved);

        long noisyCount = countNoisyDocuments(retrieved);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("topK", topK);
        response.put("threshold", threshold);
        response.put("rawResultCount", rawRetrieved.size());
        response.put("resultCount", retrieved.size());
        response.put("noisyDocumentCount", noisyCount);
        response.put("results", mapDocuments(retrieved));
        response.put("answer", answer);
        return response;
    }

    @GetMapping("/context-pollution")
    public Map<String, Object> contextPollution(
        @RequestParam(defaultValue = "Spring AI'da RAG nasil calisir?") String q
    ) {

        List<Document> rawRetrieved = searchLessonDocuments(q, CONTEXT_POLLUTION_TOP_K,
            CONTEXT_POLLUTION_THRESHOLD);
        List<Document> retrieved = deduplicateDocuments(rawRetrieved);
        long noisyCount = countNoisyDocuments(retrieved);
        long relevantCount = retrieved.size() - noisyCount;

        String interpretation;

        if (noisyCount > relevantCount) { // Kirlilik kritik esigi asti: ilgisiz dokuman cogunlukta
            interpretation = "Baglam kirli: ilgisiz dokuman sayisi ilgili dokumandan fazla.";

        } else if (noisyCount > 0) { // Kismen kirli: dalgalanma riski var ama felaket degil
            interpretation = "Karisik baglam: cevap kalitesi dalgalanabilir.";

        } else { // Hicbir noise gelmedi — bu sorguda temiz sonuc
            interpretation = "Baglam temiz: bu senaryoda belirgin kirlilik gorulmedi.";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("topK", CONTEXT_POLLUTION_TOP_K);
        response.put("threshold", CONTEXT_POLLUTION_THRESHOLD);
        response.put("rawResultCount", rawRetrieved.size());
        response.put("resultCount", retrieved.size());
        response.put("relevantCount", relevantCount);
        response.put("noisyDocumentCount", noisyCount);
        response.put("results", mapDocuments(retrieved));
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

    private long countNoisyDocuments(List<Document> documents) {
        return documents.stream()
            .filter(document -> {
                Object topic = document.getMetadata().get("topic");
                return topic != null && !"spring-ai".equals(topic.toString());
            })
            .count();
    }

    private List<Document> deduplicateDocuments(List<Document> documents) {
        Map<String, Document> uniqueDocuments = new LinkedHashMap<>();
        for (Document document : documents) {
            Object topicValue = document.getMetadata().get("topic");
            String topic = topicValue == null ? "unknown" : topicValue.toString();
            String key = document.getText() + "|" + topic;
            if (!uniqueDocuments.containsKey(key)) {
                uniqueDocuments.put(key, document);
            }
        }
        return new ArrayList<>(uniqueDocuments.values());
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
