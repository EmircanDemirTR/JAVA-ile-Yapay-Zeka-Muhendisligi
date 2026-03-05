package com.javaai.bolum09;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("pgvector")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b97")

public class EtlPipelineDemo {

    private static final String SOURCE_FILE = "data/spring-ai-intro.txt";

    private final VectorStore vectorStore;

    private int lastReadCount;
    private int lastChunkCount;
    private int lastStoredCount;

    public EtlPipelineDemo(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        org.springframework.boot.SpringApplication.run(EtlPipelineDemo.class, args);
    }

    @PostMapping("/pipeline/run")
    public Map<String, Object> runPipeline() {
        List<Document> rawDocuments = readSourceDocuments(); // READ adimi
        TokenTextSplitter splitter = createDefaultSplitter(); // TRANSFORM hazirlik - chunk parametreleri ayarlanir.
        List<Document> chunks = splitter.apply(rawDocuments); // TRANSFORM adimi
        vectorStore.add(chunks); // WRITE adimi

        lastReadCount = rawDocuments.size();
        lastChunkCount = chunks.size();
        lastStoredCount = chunks.size();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("source", SOURCE_FILE);
        response.put("readCount", lastReadCount);
        response.put("chunkCount", lastChunkCount);
        response.put("storedCount", lastStoredCount);
        return response;
    }

    @GetMapping("/pipeline/preview")
    public Map<String, Object> preview() {
        List<Document> rawDocuments = readSourceDocuments(); // READ adimi
        TokenTextSplitter splitter = createDefaultSplitter(); // TRANSFORM hazirlik - chunk parametreleri ayarlanir.
        List<Document> chunks = splitter.apply(rawDocuments); // TRANSFORM adimi

        lastReadCount = rawDocuments.size();
        lastChunkCount = chunks.size();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("source", SOURCE_FILE);
        response.put("readCount", lastReadCount);
        response.put("chunkCount", lastChunkCount);
        response.put("firstChunkPreview", chunks.get(0).getText());
        return response;
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "Spring AI") String q) {

        SearchRequest request = SearchRequest.builder()
            .query(q)
            .topK(5)
            .similarityThresholdAll()
            .build();

        List<Document> results = vectorStore.similaritySearch(request);

        return Map.of(
            "query", q,
            "resultCount", results.size(),
            "results", results.stream().map(document -> Map.of(
                    "id", document.getId(),
                    "score", document.getScore(),
                    "text", document.getText(),
                    "metadata", document.getMetadata()
                ))
                .toList()
        );
    }

    private List<Document> readSourceDocuments() {
        TextReader reader = new TextReader(new ClassPathResource(SOURCE_FILE));
        return reader.get();
    }

    private TokenTextSplitter createDefaultSplitter() {
        return TokenTextSplitter.builder()
            .withChunkSize(500) // 500 tokenlik chunklar olusturulur.
            .withMinChunkSizeChars(200) // Minimum 200 karakterlik chunklar olusturulur.
            .withMinChunkLengthToEmbed(5) // Minimum 5 tokenlik chunklar embedding icin kullanilir.
            .withMaxNumChunks(100) // Maksimum 100 chunk olusturulur.
            .withKeepSeparator(true) // Chunklar arasinda ayirici karakterler korunur.
            .build();
    }
}
