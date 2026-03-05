// Swagger UI : http://localhost:8080/swagger-ui.html
// Test : http://localhost:8080/api/b31/chat?message=Java+nedir
// Test : http://localhost:8080/api/b31/chat/english?message=Java+nedir
// Test : http://localhost:8080/api/b31/review?code=for(int+i=0;i<10;i++){}

package com.javaai.bolum03;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration

@RestController
@RequestMapping("/api/b31")

public class ChatClientDemo {

    private final ChatClient generalClient;
    private final ChatClient codeReviewClient;

    public ChatClientDemo(ChatClient.Builder builder) {
        this.generalClient = builder
                .defaultSystem("Sen yardimci bir Java asistanisin. Turkce kısa ve oz yanitlar ver.")
                .build();

        this.codeReviewClient = builder.defaultSystem(
                "Sen bir senior Java gelistiricisin. Verilen kodu incele." + "1. Okunabilirlik"
                        + "2. Performans" + "3. Best practice" + " Turkce yanit ver.")
                .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(ChatClientDemo.class, args);
    }

    // prompt, user ,call, content
    @GetMapping("chat")

    public String chat(@RequestParam String message) {
        return generalClient.prompt().user(message).call().content();
    }

    @GetMapping("/chat/english")
    public String chatInEnglish(@RequestParam String message) {
        return generalClient.prompt()
                .system("You are a helpful Java assistant. Always respond in English").user(message)
                .call().content();
    }

    @GetMapping("/review")
    public String review(@RequestParam String code) {
        return codeReviewClient.prompt().user("Su kodu incele:\n\n" + code).call().content();
    }
}
