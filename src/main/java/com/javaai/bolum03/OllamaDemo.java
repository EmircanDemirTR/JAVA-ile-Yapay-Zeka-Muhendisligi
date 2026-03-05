// Swagger UI : http://localhost:8080/swagger-ui.html
// Test : http://localhost:8080/api/b33/chat?question=Java+nedir
// Test (POST) : http://localhost:8080/api/b33/code — Body: {"request":"FizzBuzz Java"}
// Test : http://localhost:8080/api/b33/advanced?message=Merhaba
// Test : http://localhost:8080/api/b33/benchmark?question=Merhaba

package com.javaai.bolum03;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("ollama") // OllamaChatOptions kullandigi icin sadece Ollama profilinde aktif.
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b33")

public class OllamaDemo {


    private static final Logger log = LoggerFactory.getLogger(OllamaDemo.class);
    private final ChatClient chatClient;


    public OllamaDemo(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem(
                        "Sen yardimci bir asistansin. Acik ve net yanitler ver. Turkce yanit ver.")
                .build();
    }


    static void main(String[] args) {
        SpringApplication.run(OllamaDemo.class, "--spring.profiles.active=ollama");
    }


    // TEMEL KULLANIM — Genel sohbet (llama3.2)

    @GetMapping("/chat")
    public Map<String, Object> generalChat(@RequestParam String question) {
        OllamaChatOptions options = OllamaChatOptions.builder().model("llama3.2").temperature(0.7)
                .numPredict(2048).build();

        long start = System.currentTimeMillis();
        String response = chatClient.prompt().user(question).options(options).call().content();
        long duration = System.currentTimeMillis() - start;

        return Map.of("response:", response, "model", "llama3.2", "duration", duration);
    }

    // KOD URETIMI — deepseek-r1:8b

    @PostMapping("/code")
    public Map<String, Object> generateCode(@RequestBody Map<String, String> request) {
        String codeRequest = request.getOrDefault("request", "Hello World Java");
        OllamaChatOptions options = OllamaChatOptions.builder().model("deepseek-r1:8b")
                .temperature(0.1).numPredict(4096).numCtx(8192).build();

        long start = System.currentTimeMillis();
        String response = chatClient.prompt().system(
                "Sen deneyimli bir yazilim gelistiricisin. Temiz, okunabilir, best practice'lere uygun kod yaz.")
                .user(codeRequest).options(options).call().content();

        long duration = System.currentTimeMillis() - start;
        return Map.of("code:", response, "model", "deepseek-r1:8b", "duration", duration);
    }
}
