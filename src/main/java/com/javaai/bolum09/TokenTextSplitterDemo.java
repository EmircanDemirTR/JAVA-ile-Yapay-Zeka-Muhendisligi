package com.javaai.bolum09;

import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("pgvector")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b99")

public class TokenTextSplitterDemo {

    private static final String SOURCE_FILE = "data/spring-ai-intro.txt";

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        org.springframework.boot.SpringApplication.run(TokenTextSplitterDemo.class, args);
    }

    @PostMapping("/split-basic")
    public Map<String, Object> splitBasic() {
        List<Document> chunks = splitWith(500, 200, 5, 100, false);

        return Map.of(
            "chunkCount", chunks.size(),
            "firstChunk", chunks.get(0).getText()
        );
    }

    @PostMapping("/split-overlap")
    public Map<String, Object> splitOverlap() {
        List<Document> chunks = splitWith(500, 200, 5, 100, true);

        return Map.of(
            "chunkCount", chunks.size(),
            "firstChunk", chunks.get(0).getText()
        );
    }

    @GetMapping("/compare-strategies")
    public Map<String, Object> compareStrategies() {
        List<Document> small = splitWith(200, 100, 5, 300, false);
        List<Document> medium = splitWith(500, 200, 5, 150, true);
        List<Document> large = splitWith(1000, 300, 5, 80, true);

        return Map.of(
            "smallChunkCount", small.size(),
            "mediumChunkCount", medium.size(),
            "largeChunkCount", large.size(),
            "smallFirstChunk", small.get(0).getText(),
            "mediumFirstChunk", medium.get(0).getText(),
            "largeFirstChunk", large.get(0).getText()
        );
    }

    private List<Document> splitWith(
        int chunkSize,
        int minChunkSize,
        int minChunkLengthToEmbed,
        int maxNumChunks,
        boolean keepSeparator
    ) {
        TextReader reader = new TextReader(new ClassPathResource(SOURCE_FILE));
        List<Document> rawDocs = reader.get();

        TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(chunkSize)
            .withMinChunkSizeChars(minChunkSize)
            .withMinChunkLengthToEmbed(minChunkLengthToEmbed)
            .withMaxNumChunks(maxNumChunks)
            .withKeepSeparator(keepSeparator)
            .build();

        return splitter.apply(rawDocs);
    }


}
