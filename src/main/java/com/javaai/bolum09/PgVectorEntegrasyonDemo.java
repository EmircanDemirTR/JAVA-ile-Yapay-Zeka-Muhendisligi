package com.javaai.bolum09;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("pgvector")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b95")

public class PgVectorEntegrasyonDemo {

    private final VectorStore vectorStore; // pgvector ile vektor veritabanina erisim icin
    private final JdbcTemplate jdbcTemplate; // Spring JDBC sorgulari icin, helath endpointi icin

    public PgVectorEntegrasyonDemo(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector"); // pgvector profilini aktif et
        SpringApplication.run(PgVectorEntegrasyonDemo.class, args);
    }

    @PostMapping("/index")
    public Map<String, Object> indexDocuments() {
        List<Document> documents = List.of(
            new Document(
                UUID.randomUUID().toString(), // Benzersiz ID
                "Spring AI embedding ve retrieval temelleri", // Dokuman metni
                Map.of("source", "b95", "topic", "spring-ai") // Metadata: kaynak dersi ve konu etiketi
            ),
            new Document(
                UUID.randomUUID().toString(), // Benzersiz ID
                "PostgreSQL pgvector ile benzerlik aramasi", // Dokuman metni
                Map.of("source", "b95", "topic", "pgvector") // Metadata: kaynak dersi ve konu etiketi
            ),
            new Document(
                UUID.randomUUID().toString(), // Benzersiz ID
                "Java backend ve REST API mimarisi", // Dokuman metni
                Map.of("source", "b95", "topic", "java") // Metadata: kaynak dersi ve konu etiketi
            )
        );

        vectorStore.add(documents);

        return Map.of("message", "Dokumanlar pgvector'e eklendi",
            "indexedCount", documents.size());
    }

    @GetMapping("/search")
    public Map<String, Object> search(
        @RequestParam(defaultValue = "Spring AI") String q,
        @RequestParam(defaultValue = "3") int topK
    ) {
        if (q.isBlank()) {
            return Map.of("error", "Soru bos olamaz");
        }
        if (topK <= 0) {
            return Map.of("error", "topK pozitif bir sayi olmalidir");
        }

        SearchRequest request = SearchRequest.builder()
            .query(q) // EmbeddingModel, metni vektore donusturur ve bu vektoru kullanarak benzerlik arar
            .topK(topK) // Donen sonuclarin sayisi
            .similarityThresholdAll()
            .build();

        List<Document> results = vectorStore.similaritySearch(request); // Depoda benzerlik aramasini calistir

        List<Map<String, Object>> mappedResults = results.stream()
            .map(document -> Map.<String, Object>of(
                "id", document.getId(),
                "score", document.getScore(),
                "text", document.getText(),
                "metadata", document.getMetadata()
            ))
            .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("resultCount", mappedResults.size());
        response.put("results", mappedResults);

        return response;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        try {
            Integer ping = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'vector_store_b9'",
                Integer.class
            );

            return Map.of(
                "status", "ok",
                "ping", ping,
                "vectorStoreTableExists", tableCount != null && tableCount > 0
            );
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}
