package com.javaai.bolum03;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b34")

public class StreamingDemo {

    // Teknik olarak stream fonksiyonu, SSE (Server-Sent Events) protokolünü kullanır.
    private static final Logger log = LoggerFactory.getLogger(StreamingDemo.class);
    private final ChatClient chatClient;

    public StreamingDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("Sen yardimci bir asistansin. Turkce yanit ver.")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(StreamingDemo.class, args);
    }

    //call - LLM'e soruyu gönder ve TÜM YANIT hazır olana kadar bekle.
    // Thread burada bloklanır, yanıt gelenee kadar başka iş yapmaz
    @GetMapping("/sync")
    public String syncChat(@RequestParam String message) {
        log.info("Senkron cagri basladi. {}", truncate(message, 50));
        long start = System.currentTimeMillis();

        String response = chatClient.prompt()
            .user(message)
            .call()
            .content();

        log.info("Senkron cagri tamamlandı. {} ms", System.currentTimeMillis() - start);
        return response;
    }

    // Stream -- Yanıt parçalar chunklar halinde gelir.
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> streamChat(@RequestParam String message) {
        return chatClient.prompt()
            .user(message)
            .stream()
            .content();
    }

    @GetMapping(value = "/stream/detail", produces = "text/event-stream;charset=UTF-8")
    public Flux<ChatResponse> streamDetailed(@RequestParam String message) {
        return chatClient.prompt()
            .user(message)
            .stream()
            .chatResponse();
    }

    // Timeout
    @GetMapping(value = "/stream/timeout", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> streamWithTimeout(@RequestParam String message) {
        return chatClient.prompt()
            .user(message)
            .stream()
            .content()
            .timeout(Duration.ofSeconds(30))
            .onErrorResume(e -> {
                log.error("Stream timeout hatası");
                return Flux.just("[HATA: Yanit zaman asimina ugradi}");
            });
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ?
            text.substring(0, maxLength) + "..."
            : text;
    }

}
