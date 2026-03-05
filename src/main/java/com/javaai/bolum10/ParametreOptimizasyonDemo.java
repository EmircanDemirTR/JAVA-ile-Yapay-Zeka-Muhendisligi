package com.javaai.bolum10;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
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
@RequestMapping(value = "/api/b106", produces = "application/json;charset=UTF-8")

public class ParametreOptimizasyonDemo {

    private static final String LESSON_CODE = "b106";

    private final ChatClient.Builder chatClientBuilder;

    private final VectorStore vectorStore;

    private final Bolum10SeedVeriHazirla seedService;

    public ParametreOptimizasyonDemo(ChatClient.Builder chatClientBuilder,
        VectorStore vectorStore,
        Bolum10SeedVeriHazirla seedService) {
        this.chatClientBuilder = chatClientBuilder;
        this.vectorStore = vectorStore;
        this.seedService = seedService;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector"); // pgvector profili: PostgreSQL + pgvector aktif
        SpringApplication.run(ParametreOptimizasyonDemo.class, args); // Uygulamayi baslat
    }

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return seedService.seedLesson(LESSON_CODE);
    }


    @GetMapping("/sweep")
    public Map<String, Object> sweep(
        @RequestParam(defaultValue = "Spring AI retrieval") String q
    ) {
        List<Integer> topKValues = List.of(1, 3, 5, 10); // Denenecek topK degerleri
        List<Double> thresholdValues = List.of(0.0, 0.2, 0.4, 0.6);

        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder(); // Filtre builder'i tek seferlik, cagri disinda olusturulabilir
        LinkedHashMap<String, Object> matrix = new LinkedHashMap<>();

        for (Integer topK : topKValues) {
            for (Double threshold : thresholdValues) {
                SearchRequest request = SearchRequest.builder()
                    .query(q)
                    .topK(topK)
                    .similarityThreshold(threshold)
                    .filterExpression(filterBuilder.eq("lesson", LESSON_CODE).build())
                    .build();

                List<Document> results = vectorStore.similaritySearch(request); // Arama yap ve sonuclari al

                Double topScore = results.isEmpty() ? null : results.get(0).getScore(); // En iyi sonucun skorunu al (var ise)

                LinkedHashMap<String, Object> row = new LinkedHashMap<>(); // Sonuclar icin bir row olustur
                row.put("count", results.size()); // Bulunan belge sayisi
                row.put("topScore", topScore); // En iyi sonucun skoru

                matrix.put("topK_" + topK + "_threshold_" + threshold, row); // Sonuclari matrise ekle
            }
        }

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("matrix", matrix); // Tum sonuclari iceren matrisi don
        return response;
    }


    @GetMapping("/runtime-filter")
    public Map<String, Object> runtimeFilter(
        @RequestParam(defaultValue = "RAG tuning") String q,
        @RequestParam(defaultValue = "manual") String source
    ) {
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore)
            .searchRequest(SearchRequest.builder()
                .topK(5)
                .similarityThreshold(0.0)
                .build())
            .build();

        ChatClient ragClient = chatClientBuilder.clone()
            .defaultSystem("Yaniti baglamdan sapmadan ver")
            .defaultAdvisors(advisor)
            .build();

        String expression = String.format("lesson == 'b106' && source == '%s'", source);

        String answer = ragClient.prompt()
            .advisors(spec -> spec.param("qa_filter_expression", expression)) // Runtime filter ifadesi
            .user(q)
            .call()
            .content();

        List<Document> filtered = searchLessonSourceDocuments(q, source); // Runtime filter ile ayni kosullarla manuel arama yap

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("source", source);
        response.put("answer", answer);
        response.put("filteredDocuments", mapDocuments(filtered)); // Filtrelenmis belgeleri map
        return response;
    }

    //Ders + kaynak metadata filtreleriyle pgvector'de belge arar.
    //Hem lesson hem source kosulunu karsilamasi gerekir (AND mantigi)
    private List<Document> searchLessonSourceDocuments(String q, String source) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();   // Yeni builder — her cagri icin taze instance
        SearchRequest request = SearchRequest.builder()
            .query(q)                                    // Aranacak embedding metni
            .topK(5)                                     // En iyi 5 eslesme
            .similarityThreshold(0.0)                    // Hic filtre — lesson+source zaten daraltiyor
            .filterExpression(filterBuilder.and(         // AND: Her iki kosul da saglanmali
                filterBuilder.eq("lesson", LESSON_CODE), // Bu ders belgeleri
                filterBuilder.eq("source", source)       // Ve bu kaynak belgeleri
            ).build())
            .build();
        return vectorStore.similaritySearch(request); // Aramay calistir ve sonuclari don
    }

    private List<Map<String, Object>> mapDocuments(List<Document> documents) {
        return documents.stream()
            .map(doc -> Map.of(
                "id", doc.getId(),
                "score", doc.getScore(),
                "text", doc.getText(),
                "metadata", doc.getMetadata()
            ))
            .toList(); // Stream'i List'e cevir
    }
}