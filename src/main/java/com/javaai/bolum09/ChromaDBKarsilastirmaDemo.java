package com.javaai.bolum09;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaApi.Collection;
import org.springframework.ai.chroma.vectorstore.ChromaApi.CreateCollectionRequest;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("chroma")
@EnableAutoConfiguration
@Import(ChromaDBKarsilastirmaDemo.ChromaObjectMapperConfig.class) // Chroma auto-config icin ObjectMapper bean'ini ayri config'ten yüklüyoruz
@RestController
@RequestMapping("/api/b96")

public class ChromaDBKarsilastirmaDemo {

    private final ObjectProvider<VectorStore> vectorStoreProvider; // VectorStore bean'ini sağlayan provider
    private final ChromaApi chromaApi;
    private final String tenantName;
    private final String databaseName;
    private final String collectionName;

    public ChromaDBKarsilastirmaDemo(
        @Qualifier("vectorStore")
        ObjectProvider<VectorStore> vectorStoreProvider,
        ChromaApi chromaApi,
        @Value("${spring.ai.vectorstore.chroma.tenant-name:default_tenant}") String tenantName,
        @Value("${spring.ai.vectorstore.chroma.database-name-name:default_database}") String databaseName,
        @Value("${spring.ai.vectorstore.chroma.collection-name:default_collection}") String collectionName
    ) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.chromaApi = chromaApi;
        this.tenantName = tenantName;
        this.databaseName = databaseName;
        this.collectionName = collectionName;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "chroma");
        SpringApplication.run(ChromaDBKarsilastirmaDemo.class, args);
    }

    private VectorStore readyVectorStore() {
        ensureCollectionExists(); // Koleksiyonun varlığını kontrol ediyoruz
        return vectorStoreProvider.getObject(); // VectorStore bean'ini sağlıyoruz
    }

    private void ensureCollectionExists() {
        try {
            if (chromaApi.getTenant(tenantName) == null) {
                chromaApi.createTenant(tenantName);
            }
            if (chromaApi.getDatabase(tenantName, databaseName) == null) {
                chromaApi.createDatabase(tenantName, databaseName);
            }

            List<Collection> collections = chromaApi.listCollections(tenantName, databaseName);
            boolean exists = collections != null && collections.stream().
                anyMatch(collection -> collectionName.equals(collection.name()));

            if (!exists) {
                chromaApi.createCollection(tenantName, databaseName, new CreateCollectionRequest(collectionName));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/documents")
    public Map<String, Object> addDocuments() {
        List<Document> documents = List.of(

            new Document(UUID.randomUUID().toString(), // Benzersiz dokuman kimligi
                "ChromaDB ile hizli vector arama", // Dokuman metni
                Map.of("store", "chroma", "source", "b96")

            ), new Document(UUID.randomUUID().toString(), // Benzersiz dokuman kimligi
                "PgVector SQL merkezli kalici depolama sunar", // Dokuman metni
                Map.of("store", "pgvector", "source", "b96")

            ), new Document(UUID.randomUUID().toString(), // Benzersiz dokuman kimligi
                "SimpleVectorStore sadece bellek uzerinde calisir", // Dokuman metni
                Map.of("store", "simple", "source", "b96")
            ));

        readyVectorStore().add(documents); // ChromaDB'ye yaz — Chroma HTTP API uzerinden embedding

        return Map.of(
            "message", "Dokumanlar ChromaDB'ye eklendi",
            "count", documents.size()
        );
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "vector store") String q) {
        SearchRequest request = SearchRequest.builder()
            .query(q)
            .topK(3)
            .similarityThresholdAll()
            .build();

        List<Document> results = readyVectorStore().similaritySearch(request); // ChromaDB'den arama yap — Chroma HTTP API uzerinden embedding

        List<Map<String, Object>> mapped = results.stream()
            .map(document -> Map.<String, Object>of(
                "id", document.getId(),
                "score", document.getScore(),
                "text", document.getText(),
                "metadata", document.getMetadata()
            ))
            .toList();

        return Map.of(
            "query", q,
            "results", mapped
        );
    }

    @Configuration
    static class ChromaObjectMapperConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper(); // Chroma'nın kendi ObjectMapper'ını kullanarak JSON işlemlerini optimize ediyoruz
        }
    }
}
