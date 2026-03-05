// Swagger UI : http://localhost:8080/swagger-ui.html
// Test (POST) : http://localhost:8080/api/b32/chat — Body: {"message":"Java nedir?"}
// Test (POST) : http://localhost:8080/api/b32/chat/context — Body: {"context":"Spring
// AI...","message":"Ne ise yarar?"}
// Test (POST) : http://localhost:8080/api/b32/chat/options?temperature=0.9&maxTokens=500 — Body:
// {"message":"Hikaye yaz"}


package com.javaai.bolum03;


import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("openai") // OpenAiChatOptions kullandigi icin sadece OpenAI profilinde aktif.
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b32")

public class OpenAiSohbetDemo {

    private static final Logger log = LoggerFactory.getLogger(OpenAiSohbetDemo.class);
    private final ChatClient chatClient;

    public OpenAiSohbetDemo(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(
                "Sen yardimci bir asistansin. Acik ve anlasilir yanitlar ver. Turkce yanit ver")
                .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(OpenAiSohbetDemo.class, args);
    }

    // TEMEL KULLANIM — Basit Sohbet
    @PostMapping("/chat")

    public String chat(@RequestBody ChatRequest request) {
        return chatClient.prompt().user(request.message()).call().content();
    }

    // BAGLAM ILE KULLANIM
    @PostMapping("/chat/context")
    public String chatWithContext(@RequestBody Map<String, String> request) {
        String context = request.getOrDefault("context", "");
        String message = request.getOrDefault("message", "");

        return chatClient.prompt().system(
                "Asagidaki baglam bilgisini kullanarak soruyu yanitla\n\n" + "BAGLAM:\n" + context)
                .user(message).call().content();
    }

    // GELISMIS KULLANIM — Runtime Options
    @PostMapping("chat/options")
    public Map<String, Object> chatWithOptions(@RequestBody ChatRequest request,
            @RequestParam(defaultValue = "0.7") double temperature,
            @RequestParam(defaultValue = "1000") int maxTokens) {

        OpenAiChatOptions options =
                OpenAiChatOptions.builder().temperature(temperature).maxTokens(maxTokens).build();

        String response =
                chatClient.prompt().user(request.message()).options(options).call().content();

        return Map.of("response:", response, "temperature:", temperature, "maxTokens:", maxTokens);
    }

    // HATA YONETIMI
    // @ExceptionHandler: Bu controller icinde firlatilan HATALARI yakalayan ozel metot.
    // Normalde bir exception firlatildiginda Spring varsayilan bir hata sayfasi gosterir.
    // @ExceptionHandler ile bu davranisi kendi istedigimiz sekilde degistirebiliriz.
    // (Exception.class) parametresi "tum exception turlerini yakala" demek.

    @ExceptionHandler(Exception.class)
    public Map<String, String> handleError(Exception ex) {
        String message = ex.getMessage() != null
                ? ex.getMessage().replaceAll("sk-[a-zA-Z0-9]{20,}", "sk-****")
                : "Bilinmeyen hata";
        log.error("Hata: {}", message);
        return Map.of("error", message);
    }

    public record ChatRequest(String message) {

        public ChatRequest {
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("Mesaj bos olamaz");
            }
        }
    }
}
