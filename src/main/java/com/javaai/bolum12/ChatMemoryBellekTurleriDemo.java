package com.javaai.bolum12;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping(value = "/api/b121", produces = "application/json;charset=UTF-8")

public class ChatMemoryBellekTurleriDemo {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    // chatMemory — Konusma gecmisini depolayan ve okuyan ChatMemory API; MemoryConfig @Bean saglar


    public ChatMemoryBellekTurleriDemo(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder
            .defaultSystem("Sen yardimci bir Java asistanisin. Kisa ve net cevaplar ver.")
            .build();
        this.chatMemory = chatMemory; // Enjekte edilen bellek deposunu sakla
    }


    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai");
        SpringApplication.run(ChatMemoryBellekTurleriDemo.class, args);
    }


    @GetMapping("/belleksiz-chat")
    public Map<String, Object> belleksizChat(
        @RequestParam(defaultValue = "Merhaba, benim adim Emircan") String message) {

        String answer = chatClient
            .prompt()
            .user(message)
            .call()
            .content();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("lesson", "B12.1");
        response.put("query", message);
        response.put("answer", answer);
        response.put("memoryUsed", false);

        return response;
    }


    @GetMapping("/hatirlama-testi")
    public Map<String, Object> hatirlamaTesti() {

        // 1. adim: Modele isim soyluyor
        String answer1 = chatClient
            .prompt()
            .user("Benim adim Emircan. Bunu hatirla lutfen!")
            .call()
            .content();

        // 2. adim: Baska bir bilgi ekleniyor
        String answer2 = chatClient
            .prompt()
            .user("Java 25 surumu en yeni LTS versiyondur.")
            .call()
            .content();

        // 3. adim: Isim soruluyor
        String answer3 = chatClient
            .prompt()
            .user("Benim adim neydi? Lutfen soyler misin?")
            .call()
            .content();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("lesson", "B12.1");
        response.put("step1_prompt", "Benim adim Emircan. Bunu hatirla lutfen!");
        response.put("step1_answer", answer1);
        response.put("step2_prompt", "Java 25 surumu en yeni LTS versiyondur.");
        response.put("step2_answer", answer2);
        response.put("step3_prompt", "Benim adim neydi?");
        response.put("step3_answer", answer3);

        return response;
    }


    @PostMapping("/memory/add")
    public Map<String, Object> addToMemory(
        @RequestParam String convId,
        @RequestParam(defaultValue = "User") String role,
        @RequestParam String text
    ) {

        Message message = role.equalsIgnoreCase("assistant")
            ? new AssistantMessage(text)
            : new UserMessage(text);

        chatMemory.add(convId, List.of(message)); // convID oturumuna mesajı depola
        List<Message> history = chatMemory.get(convId); // Guncellenmis gecmisi oku

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("convID", convId);
        response.put("addedRole", role);
        response.put("totalMessages", history.size());

        return response;
    }


    @GetMapping("/memory/history/{convId}")
    public Map<String, Object> getHistory(@PathVariable String convId) {

        List<Message> history = chatMemory.get(convId);

        List<Map<String, Object>> formatted = history.stream()
            .map(m -> Map.<String, Object>of(
                "role", m.getMessageType().name(),
                "content", m.getText()))
            .toList();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("convID", convId);
        response.put("totalMessages", history.size());
        response.put("messages", formatted);

        return response;
    }


    static class MemoryConfig {

        @Bean
        ChatMemory chatMemory() {
            return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
        }
    }

}