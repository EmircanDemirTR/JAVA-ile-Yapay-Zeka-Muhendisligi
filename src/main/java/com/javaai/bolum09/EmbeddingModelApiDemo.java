package com.javaai.bolum09;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("openai")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b92")

public class EmbeddingModelApiDemo {

    // EmbeddingModel: Metni sabit boyutlu float dizisine donusturur
    private final EmbeddingModel embeddingModel;

    public EmbeddingModelApiDemo(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai");
        SpringApplication.run(EmbeddingModelApiDemo.class, args);
    }

    @GetMapping("/single")
    public Map<String, Object> single(@RequestParam(defaultValue = "Spring AI ile Java Backend") String text) {
        if (text.isBlank()) {
            return Map.of("error", "Lutfen gecerli bir metin giriniz");
        }

        float[] vector = embeddingModel.embed(text);

        Map<String, Object> response = Map.of(
            "text", text,
            "vectorSize", vector.length
        );
        return response;
    }

    @PostMapping("/batch")
    public Map<String, Object> batch(@RequestBody List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Map.of("error", "Lutfen gecerli bir metin listesi giriniz");
        }

        EmbeddingResponse response = embeddingModel.embedForResponse(texts); // tek istekte N metin -> N embedding + metadata
        List<Embedding> results = response.getResults(); // Her metin icin ayri Embedding nesnesi doner

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputSize", texts.size());
        payload.put("resultSize", results.size());
        payload.put("firstVectorSize", results.isEmpty() ? 0 : results.get(0).getOutput().length); // İlk vektorun boyutu

        return payload;
    }


    @GetMapping("/most-similar")
    public Map<String, Object> mostSimilar(@RequestParam(defaultValue = "Java backend uygulamasi") String query) {
        if (query.isBlank()) {
            return Map.of("error", "Lutfen gecerli bir sorgu metni giriniz");
        }

        List<String> candidates = List.of(
            "Spring AI ile Java Backend",
            "Python ile veri bilimi",
            "Bahce sulama rehberi",
            "Java backend uygulamasi"
        );

        float[] queryVector = embeddingModel.embed(query);

        String bestCandidate = "";
        double bestScore = -1.0;

        for (String candidate : candidates) {
            float[] candidateVector = embeddingModel.embed(candidate);
            double similarity = cosineSimilarity(queryVector, candidateVector);

            double score = cosineSimilarity(queryVector, candidateVector);
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", query);
        response.put("bestCandidate", bestCandidate);
        response.put("similarityScore", bestScore);

        return response;
    }

    @GetMapping("/response-detail")
    public Map<String, Object> responseDetail(@RequestParam(defaultValue = "Embedding metadata ornegi") String text) {
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        Usage usage = response.getMetadata().getUsage();

        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("model", response.getMetadata().getModel());
        responseMap.put("promptTokens", usage.getPromptTokens()); // Girdi token sayısı
        responseMap.put("completionTokens", usage.getCompletionTokens()); // Cikti token sayısı - DAIMA 0
        responseMap.put("totalTokens", usage.getTotalTokens());
        responseMap.put("resultCount", response.getResults().size()); // Kac embedding uretti

        return responseMap;
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
