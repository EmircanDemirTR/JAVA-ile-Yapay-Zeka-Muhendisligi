// Swagger UI : http://localhost:8080/swagger-ui.html
// Test (GET) : http://localhost:8080/api/b27/merhaba?isim=Dünya
// Test (GET) : http://localhost:8080/api/b27/bilgi
// Test (POST) : Swagger UI üzerinden → /api/b27/hesapla
// Test (POST) : Swagger UI üzerinden → /api/b27/chat

package com.javaai.bolum02;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration // Spring Boot'un otomatik yapilandirma ozelligini aktif eder.
@RestController // Bu sinifin bir REST API controller'i oldugunu belirtir.
@RequestMapping("/api/b27") // bu siniftaki her endpoint "/api/b27/..." ile baslar.
@Tag(name = "B2.7 - Swagger UI Demo", description = "REST API test araclari")

public class SwaggerTestDemo {


    private final ChatClient chatClient;
    // ChatClient: LLM ile iletisim kurmayi saglayan Spring AI arayuzu.


    public SwaggerTestDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("Sen yardimci bir asistansin. Kisa ve oz yanitler ver. Turkce yanit ver")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(SwaggerTestDemo.class, args);
    }


    @GetMapping("/merhaba")
    @Operation(summary = "Selamlama (GET)", description = "Tarayicidan dogrudan test edilebilir.")

    public String merhaba(@RequestParam(defaultValue = "Dunya") String isim) {
        return "Merhaba " + isim + "! Swagger Uı'a hoşgeldiniz.";
    }

    @PostMapping("/hesapla")
    @Operation(summary = "Hesaplama (POST)", description = "Tarayicidan test edilemez")

    public Map<String, Object> hesapla(@RequestBody HesaplamaRequest request) {
        int a = request.sayi1();
        int b = request.sayi2();

        return Map.of("toplam", a + b, "fark", a - b, "carpim", a * b, "bolum", a / b);
    }

    @PostMapping("/chat")
    @Operation(summary = "AI Sohbet", description = "ChatClient ile LLM'e mesaj gönderin")

    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String yanit = chatClient.prompt().user(request.message()).call().content();

        return Map.of("soru", request.message(), "yanit", yanit);
    }

    public record HesaplamaRequest(int sayi1, int sayi2) {

    }

    public record ChatRequest(String message) {

    }

}
