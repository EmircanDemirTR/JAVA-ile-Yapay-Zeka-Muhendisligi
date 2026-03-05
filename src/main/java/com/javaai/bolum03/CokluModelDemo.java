package com.javaai.bolum03;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b36")

public class CokluModelDemo {
    // Fallback -- Yedek sağlayıcıya otomatik geçiş
    // Retry -- Geçici hatalarda tekrar deneme
    // Health Check -- Sağlayıcı durumunu izleme

    private static final Logger log = LoggerFactory.getLogger(CokluModelDemo.class);
    private final ChatClient openAIClient;
    private final ChatClient ollamaClient;

    public CokluModelDemo(
        @Autowired(required = false) OpenAiChatModel openAiModel,
        @Autowired(required = false) OllamaChatModel ollamaModel) {

        // OpenAI modeli varsa client oluştur, yoksa null bırak
        this.openAIClient = openAiModel != null
            ? ChatClient.builder(openAiModel)
            .defaultSystem("Sen yardimci bir asistansin. Turkce yanit ver.").build()
            : null;

        // Ollama modeli varsa client oluştur, yoksa null bırak
        this.ollamaClient = ollamaModel != null
            ? ChatClient.builder(ollamaModel)
            .defaultSystem("Sen yardimci bir asistansin. Turkce yanit ver.").build()
            : null;

        // Uygulama başlarken hangi sağlayıcıların aktif olduğunu loglayalım
        log.info("CokluModelDemo basladi -- OpenAI: {} ve Ollama: {}",
            openAIClient != null ? "AKTIF" : "YOK",
            ollamaClient != null ? "AKTIF" : "YOK");
    }

    public static void main(String[] args) {
        SpringApplication.run(CokluModelDemo.class, "--spring.profiles.active=multimodel");
    }

    // FALLBACK mekanizması
    @PostMapping("/chat")
    public Map<String, Object> fallbackChat(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");

        // 1. ADIM OPENAI
        if (openAIClient != null) {
            try {
                log.info("OpenAI deneniyor.");
                String response = openAIClient.prompt().user(message).call().content();
                log.info("OpenAI başarılı");
                return Map.of("response", response, "provider", "openai");
            } catch (Exception e) {
                log.warn("Open AI hatasi. Ollama'ya geçiliyor.");
            }
        }

        // 2. ADIM OLLAMA
        if (ollamaClient != null) {
            try {
                log.info("Ollama deneniyor.");
                String response = ollamaClient.prompt().user(message).call().content();
                log.info("Ollama başarılı");
                return Map.of("response", response, "provider", "ollama");
            } catch (Exception e) {
                log.error("Ollama da hatali.");
            }
        }

        return Map.of("error", "Tum AI saglayicilari kullanılamıyor.");
    }

    // RETRY Mekanizması
    @PostMapping("/chat/retry")
    public Map<String, Object> retryChast(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        ChatClient client = openAIClient != null ? openAIClient : ollamaClient;
        if (client == null) {
            return Map.of("error", "Hic AI sağlayıcısı yapılandırılmamış");
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                log.info("Deneme {} / 3...", attempt);
                String response = client.prompt().user(message).call().content();
                return Map.of(
                    "response", response,
                    "attempt", attempt,
                    "provider", openAIClient != null ? "openai" : "ollama"
                );
            } catch (Exception e) {
                log.warn("Deneme {} / 3 basarisiz.", attempt);
                if (attempt < 3) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        return Map.of("error", "3 deneme de basariz. Lutfen birkac dakika sonra tekrar deneyiniz.");
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "openai", openAIClient != null,
            "ollama", ollamaClient != null,
            "anyAvailable", openAIClient != null || ollamaClient != null
        );
    }

    // Hata Yönetimi
    @ExceptionHandler(Exception.class)
    public Map<String, String> handleError(Exception ex) {
        log.error("Hata: {}", ex.getMessage());
        return Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Bilinmeyen hata");
    }

}
