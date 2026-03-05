// Swagger UI  : http://localhost:8080/swagger-ui.html
// Test (GET)  : http://localhost:8080/api/b93/openai-vs-ollama?text=Java%20backend
// Test (GET)  : http://localhost:8080/api/b93/latency
// Test (GET)  : http://localhost:8080/api/b93/quality-matrix

package com.javaai.bolum09;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b93")

public class EmbeddingKarsilastirmaDemo {

    private final OpenAiEmbeddingModel openAiEmbeddingModel;
    private final OllamaEmbeddingModel ollamaEmbeddingModel;

    // Constructor'da iki embedding modelini opsiyonel aliyoruz.
    // required=false: API key eksik veya Ollama servisi kapali ise bean null gelir, uygulama ayakta kalir.
    public EmbeddingKarsilastirmaDemo(
        @Autowired(required = false) OpenAiEmbeddingModel openAiEmbeddingModel,
        @Autowired(required = false) OllamaEmbeddingModel ollamaEmbeddingModel
    ) {
        this.openAiEmbeddingModel = openAiEmbeddingModel;
        this.ollamaEmbeddingModel = ollamaEmbeddingModel;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "multimodel");
        SpringApplication.run(EmbeddingKarsilastirmaDemo.class, args);
    }
    

    @GetMapping("/openai-vs-ollama")
    public Map<String, Object> compare(
        @RequestParam(defaultValue = "Java backend uygulamasi") String text
    ) {
        if (isAnyModelMissing()) {
            return missingModelResponse();
        }
        if (text.isBlank()) {
            return Map.of("error", "text bos olamaz");
        }

        // System.nanoTime(): currentTimeMillis()'dan daha hassas — OS zamanlayici interrupt'larindan etkilenmez.
        long openAiStart = System.nanoTime();
        float[] openAiVector = openAiEmbeddingModel.embed(text); // OpenAI API'sine istek: metin → vektor (bulut)
        long openAiMs = (System.nanoTime() - openAiStart) / 1_000_000;

        long ollamaStart = System.nanoTime();
        float[] ollamaVector = ollamaEmbeddingModel.embed(text); // Ollama yerel sunucuya istek: metin → vektor (lokal)
        long ollamaMs = (System.nanoTime() - ollamaStart) / 1_000_000;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("text", text);
        response.put("openAi", Map.of(
            "dimension", openAiVector.length, // 1536 (small) veya 3072 (large) — VectorStore bu boyuta gore yapilanmali
            "durationMs", openAiMs,
            "firstFive", Arrays.copyOf(openAiVector, Math.min(5, openAiVector.length))
        ));
        response.put("ollama", Map.of(
            "dimension", ollamaVector.length, // 768 (nomic-embed-text) — OpenAI ile farkli! ayni VectorStore kullanilmaz
            "durationMs", ollamaMs,
            "firstFive", Arrays.copyOf(ollamaVector, Math.min(5, ollamaVector.length))
        ));
        return response;
    }


    @GetMapping("/latency")
    public Map<String, Object> latency() {
        if (isAnyModelMissing()) {
            return missingModelResponse();
        }
        List<String> sampleTexts = List.of(
            "Java ve Spring Boot",
            "RAG mimarisi ve retrieval",
            "PostgreSQL pgvector kullanimi",
            "Yerel model ile embedding",
            "Prompt muhendisligi"
        );

        long openAiTotal = 0L;
        long ollamaTotal = 0L;

        for (String text : sampleTexts) {
            long startOpenAi = System.nanoTime();
            openAiEmbeddingModel.embed(text);
            openAiTotal += (System.nanoTime() - startOpenAi) / 1_000_000;

            long startOllama = System.nanoTime();
            ollamaEmbeddingModel.embed(text);
            ollamaTotal += (System.nanoTime() - startOllama) / 1_000_000;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sampleCount", sampleTexts.size()); // Kac metin uzerinde olcum yapildi
        response.put("openAiAvgMs", openAiTotal / sampleTexts.size()); // OpenAI ortalama gecikme (ms)
        response.put("ollamaAvgMs", ollamaTotal / sampleTexts.size()); // Ollama ortalama gecikme (ms)
        response.put("note", "Olcumler lokal ortama gore degisir"); // GPU/CPU/ag durumuna gore dalgalanabilir
        return response;
    }

    @GetMapping("/quality-matrix")
    public Map<String, Object> qualityMatrix() {
        if (isAnyModelMissing()) {
            return missingModelResponse();
        }
        String baseText = "Java backend gelistirme"; // Referans metin — adaylar bununla karsilastirilacak
        List<String> candidates = List.of(
            "Spring Boot REST API",
            "Futbol mac yorumu",
            "PostgreSQL veritabani yonetimi",
            "Bahce bakimi"
        );

        float[] openAiBase = openAiEmbeddingModel.embed(baseText); // Referans metni OpenAI ile vektore cevir
        float[] ollamaBase = ollamaEmbeddingModel.embed(baseText); // Ayni metni Ollama ile vektore cevir

        Map<String, Double> openAiScores = new LinkedHashMap<>();
        Map<String, Double> ollamaScores = new LinkedHashMap<>();

        for (String candidate : candidates) {
            // Ayni adayi iki farkli modelle karsilastirip skor farklarini gozlemliyoruz.
            openAiScores.put(candidate, cosineSimilarity(openAiBase, openAiEmbeddingModel.embed(candidate)));
            ollamaScores.put(candidate, cosineSimilarity(ollamaBase, ollamaEmbeddingModel.embed(candidate)));
        }

        return Map.of(
            "lesson", "B9.3",
            "baseText", baseText,
            "openAiScores", openAiScores,
            "ollamaScores", ollamaScores
        );
    }

    // isAnyModelMissing: iki modelden biri bile inject edilmediyse true doner.
    private boolean isAnyModelMissing() {
        return openAiEmbeddingModel == null || ollamaEmbeddingModel == null;
    }

    // missingModelResponse: eksik model durumunda standart hata cevabi uretir.
    private Map<String, Object> missingModelResponse() {
        return Map.of(
            "lesson", "B9.3",
            "error", "OpenAI veya Ollama embedding modeli yuklenemedi",
            "advice", "multimodel profilinde OPENAI_API_KEY ve Ollama servis durumunu kontrol edin"
        );
    }

    // cosineSimilarity: iki vektorun aci benzerligini 0-1 arasinda hesaplar.
    private double cosineSimilarity(float[] vector1, float[] vector2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < Math.min(vector1.length, vector2.length); i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}