package com.javaai.bolum09;

import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("pgvector")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b98")

public class DocumentReaderDemo {

    private static final String TEXT_PATH = "data/spring-ai-intro.txt";
    private static final String MARKDOWN_PATH = "data/java-temelleri.md";
    private static final String JSON_PATH = "data/urunler.json";

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        org.springframework.boot.SpringApplication.run(DocumentReaderDemo.class, args);
    }

    @PostMapping("/read/text")
    public Map<String, Object> readText() {
        TextReader reader = new TextReader(new ClassPathResource(TEXT_PATH));
        List<Document> documents = reader.get();

        return Map.of(
            "reader", "TextReader",
            "documentCount", documents.size(),
            "preview", documents.get(0).getText()
        );
    }

    @PostMapping("/read/markdown")
    public Map<String, Object> readMarkdown() {
        TikaDocumentReader reader = new TikaDocumentReader(new ClassPathResource(MARKDOWN_PATH));
        List<Document> documents = reader.get();

        return Map.of(
            "reader", "TikaDocumentReader",
            "documentCount", documents.size(),
            "preview", documents.get(0).getText()
        );
    }

    @PostMapping("/read/json")
    public Map<String, Object> readJson() {
        JsonReader reader = new JsonReader(new ClassPathResource(JSON_PATH));
        List<Document> documents = reader.get();

        return Map.of(
            "reader", "JsonReader",
            "documentCount", documents.size(),
            "preview", documents.get(0).getText()
        );
    }

}
