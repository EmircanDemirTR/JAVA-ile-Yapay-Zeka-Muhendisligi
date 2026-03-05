package com.javaai.bolum09;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("openai")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b94")
@Import(SimpleVectorStoreDemo.SimpleStoreConfig.class)

public class SimpleVectorStoreDemo {

    private final SimpleVectorStore vectorStore;
    private final List<String> documentIds = new CopyOnWriteArrayList<>();

    public SimpleVectorStoreDemo(SimpleVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai");
        SpringApplication.run(SimpleVectorStoreDemo.class, args);
    }

    @PostMapping("/documents")
    public Map<String, Object> addDocument(@RequestBody AddDocumentRequest request) {
        String id = UUID.randomUUID().toString();
        Document document = new Document(id, request.content(), Map.of("source", request.source()));

        vectorStore.add(List.of(document));
        documentIds.add(id);

        return Map.of(
            "message", "Dokuman eklendi",
            "id", id,
            "storedCount", documentIds.size()
        );
    }

    @GetMapping("/search")
    public Map<String, Object> search(
        @RequestParam(defaultValue = "Spring AI") String q,
        @RequestParam(defaultValue = "3") int topK,
        @RequestParam(defaultValue = "0.5") double threshold
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
            .similarityThreshold(threshold) // Benzerlik esik degeri, bu degerin altindaki sonuclar donmez
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

    @DeleteMapping("/reset")
    public Map<String, Object> reset() {
        if (!documentIds.isEmpty()) {
            vectorStore.delete(documentIds);
            documentIds.clear();
        }

        return Map.of(
            "message", "Depo sifirlandi",
            "storedCount", documentIds.size()
        );
    }

    @Configuration
    static class SimpleStoreConfig {

        @Bean
        SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
            return SimpleVectorStore.builder(embeddingModel).build();
            // SimpleVectorStore.builder: in-memory depo olusturur
            // embeddingModel ile metin -> vektor donusumu otomatik yapılır
        }
    }

    record AddDocumentRequest(String content, String source) {

        AddDocumentRequest {
            if (source == null || source.isBlank()) {
                source = "manual";
            }
        }
    }
}
