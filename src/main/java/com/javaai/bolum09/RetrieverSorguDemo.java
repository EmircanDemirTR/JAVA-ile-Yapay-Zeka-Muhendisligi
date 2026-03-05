// Swagger UI  : http://localhost:8080/swagger-ui.html
// Test (GET)  : http://localhost:8080/api/b912/ask?q=Spring%20AI&topK=5&threshold=0.7
// Test (GET)  : http://localhost:8080/api/b912/ask-with-threshold?q=Java&threshold=0.6
// Test (GET)  : http://localhost:8080/api/b912/topk-compare?q=vector%20store

package com.javaai.bolum09;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("pgvector")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b912")
public class RetrieverSorguDemo {

    private final VectorStore vectorStore;

    public RetrieverSorguDemo(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        SpringApplication.run(RetrieverSorguDemo.class, args);
    }

    @GetMapping("/ask")
    public Map<String, Object> ask(
        @RequestParam(defaultValue = "Spring AI") String q,
        @RequestParam(defaultValue = "5") int topK,
        @RequestParam(defaultValue = "0.7") double threshold
    ) {
        if (q.isBlank()) {
            return Map.of("error", "q bos olamaz");
        }
        if (topK <= 0) { // topK en az 1 olmali — 0 sonuc istemenin anlami yok
            return Map.of("error", "topK sifirdan buyuk olmali");
        }

        SearchRequest request = SearchRequest.builder()
            .query(q)
            .topK(topK)
            .similarityThreshold(threshold)
            .build();

        List<Document> results = vectorStore.similaritySearch(request); // Arama yap

        return Map.of(
            "query", q,
            "topK", topK,
            "threshold", threshold,
            "resultCount", results.size(), // Kalan sonuc sayisi — topK ve threshold birlikte belirler
            "results", mapDocuments(results) // Formatlenmis sonuc listesi
        );
    }

    @GetMapping("/ask-with-threshold")
    public Map<String, Object> askWithThreshold(
        @RequestParam(defaultValue = "Java") String q,
        @RequestParam(defaultValue = "0.7") double threshold
    ) {
        SearchRequest request = SearchRequest.builder()
            .query(q)
            .topK(5)
            .similarityThreshold(threshold) // Dinamik esik degeri — dusurulurse daha cok sonuc gelir
            .build();

        List<Document> results = vectorStore.similaritySearch(request); // Arama yap

        return Map.of(
            "query", q,
            "threshold", threshold,
            "resultCount", results.size(),
            "results", mapDocuments(results)
        );
    }


    @GetMapping("/topk-compare")
    public Map<String, Object> topkCompare(
        @RequestParam(defaultValue = "vector store") String q
    ) {
        List<Integer> topKValues = List.of(1, 3, 5, 10);
        Map<String, Object> matrix = new LinkedHashMap<>();

        for (Integer topK : topKValues) { // Her topK degeri icin ayri arama yap
            SearchRequest request = SearchRequest.builder() // Iterasyon bazli retrieval sorgusu
                .query(q)
                .topK(topK)
                .similarityThresholdAll()
                .build();

            List<Document> results = vectorStore.similaritySearch(request); // Arama yap
            matrix.put("topK_" + topK, Map.of( // Matriste "topK_1", "topK_3" gibi anahtarlarla sakla
                "count", results.size(),
                "results", mapDocuments(results)
            ));
        }

        return Map.of(
            "query", q,
            "matrix", matrix // topK_1/topK_3/topK_5/topK_10 sonuclari — yan yana karsilastirma
        );
    }


    @GetMapping("/search-filtered")
    public Map<String, Object> searchFiltered(
        @RequestParam(defaultValue = "Spring AI") String q,
        @RequestParam(defaultValue = "spring-ai-intro.txt") String source
    ) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        SearchRequest request = SearchRequest.builder()
            .query(q)
            .topK(5)
            .similarityThresholdAll()
            .filterExpression(builder.eq("source", source).build())
            // eq("source", source) = metadata'daki source alani bu degere esit olan kayitlari sec
            .build();

        List<Document> results = vectorStore.similaritySearch(request); // Filtreli arama yap

        return Map.of(
            "query", q,
            "source", source, // Filtreleme kriteri — source metadata degeri
            "resultCount", results.size(),
            "results", mapDocuments(results)
        );
    }

    private List<Map<String, Object>> mapDocuments(List<Document> documents) {
        return documents.stream().map(document -> Map.<String, Object>of( // Her dokuman icin detay haritasi olustur
            "id", document.getId(),
            "score", document.getScore(),
            "text", document.getText(),
            "metadata", document.getMetadata()
        )).toList();
    }
}