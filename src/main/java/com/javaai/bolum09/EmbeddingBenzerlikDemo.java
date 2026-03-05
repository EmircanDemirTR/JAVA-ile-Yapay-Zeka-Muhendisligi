package com.javaai.bolum09;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("openai")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b91")

public class EmbeddingBenzerlikDemo {

    // EmbeddingModel: Metni sabit boyutlu float dizisine donusturur
    private final EmbeddingModel embeddingModel;

    public EmbeddingBenzerlikDemo(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai");
        SpringApplication.run(EmbeddingBenzerlikDemo.class, args);
    }

    @GetMapping("/embed")
    public Map<String, Object> embed(
        @RequestParam(defaultValue = "Java ile Spring AI ogreniyorum") String text
    ) {
        if (text.isBlank()) {
            return Map.of("error", "Metin bos olamaz");
        }

        float[] vector = embeddingModel.embed(text);
        float[] firstFive = Arrays.copyOf(vector, Math.min(5, vector.length));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("text", text);
        response.put("vectorSize", vector.length);
        response.put("firstFive", firstFive);
        return response;
    }

    @GetMapping("/similarity")
    public Map<String, Object> similarity(
        @RequestParam(defaultValue = "Java programlama dili") String text1,
        @RequestParam(defaultValue = "Java yazilim platformu") String text2
    ) {
        if (text1.isBlank() || text2.isBlank()) {
            return Map.of("error", "Her iki metin de bos olamaz");
        }

        float[] vec1 = embeddingModel.embed(text1);
        float[] vec2 = embeddingModel.embed(text2);

        double sim = cosineSimilarity(vec1, vec2);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("text1", text1);
        response.put("text2", text2);
        response.put("similarity", sim);
        return response;
    }

    private double cosineSimilarity(float[] vec1, float[] vec2) {
        double dotProduct = 0.0; // Nokta carpimi toplami
        double norm1 = 0.0;
        double norm2 = 0.0; // ikinci vektorun buyuklugu (kare toplami)

        for (int i = 0; i < Math.min(vec1.length, vec2.length); i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0; // Sifir uzunluklu vektorler benzerlik hesaplanamaz
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
