package com.javaai.bolum12;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping(value = "/api/b122", produces = "application/json;charset=UTF-8")

public class ChatMemoryRepoMessageWindowDemo {

    // FIELD: chatMemory — MemoryConfig @Bean; InMemoryChatMemoryRepository (RAM, restart=sifir)
    private final ChatMemory chatMemory;


    // Bu derste ChatClient kullanmiyoruz — bellegi direkt test ediyoruz.
    public ChatMemoryRepoMessageWindowDemo(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    // ==========================================
    // ENDPOINT'LER
    // ==========================================

    // 1) POST /window/add-batch — Toplu mesaj ekleme
    // Pencere boyutu 10 — count=6 girerseniz 12 mesaj, hepsi pencerede.
    // count=7 girerseniz 14 mesaj, ilk 4 duser. Deneyin!

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai");
        SpringApplication.run(ChatMemoryRepoMessageWindowDemo.class, args);
    }

    // Konusma oturumuna toplu soru-cevap cifti ekler. Pencere etkisini test eder.
    @PostMapping("/window/add-batch")
    public Map<String, Object> addBatch(
        @RequestParam String convId,
        @RequestParam(defaultValue = "3") int count) { // Varsayilan 3 cift = 6 mesaj

        for (int i = 1; i <= count; i++) { // Her iterasyonda bir soru-cevap cifti ekle
            chatMemory.add(convId, List.of(
                new UserMessage(String.format("Soru %d: Java nedir?", i)),          // Kullanici sorusu
                new AssistantMessage(String.format("Cevap %d: Java bir programlama dilidir.", i)) // Model yaniti
            ));
        }

        List<Message> history = chatMemory.get(convId); // Ekleme sonrasi guncellenmis gecmis

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();

        response.put("convId", convId);
        response.put("addedMessages", count * 2);
        response.put("totalInMemory", history.size());
        response.put("storageType", "IN_MEMORY");

        return response;
    }

    // 2) GET /window/history/{convId} — Penceredeki mesajlari okuma
    @GetMapping("/window/history/{convId}")
    public Map<String, Object> getHistory(@PathVariable String convId) {

        List<Message> history = chatMemory.get(convId);

        List<Map<String, Object>> formatted = history.stream()
            .map(m -> Map.<String, Object>of(
                "role", m.getMessageType().name(), // USER, ASSISTANT, SYSTEM
                "content", m.getText()))           // Mesaj icerigi
            .toList();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("convId", convId);
        response.put("messageCount", history.size());
        response.put("storageType", "IN_MEMORY");
        response.put("messages", formatted);
        return response;
    }


    @GetMapping("/window/effect")
    public Map<String, Object> windowEffect() {

        MessageWindowChatMemory localMemory = MessageWindowChatMemory.builder()
            .maxMessages(5)
            .build();

        String testConvId = "pencere-demo";
        for (int i = 0; i < 7; i++) {
            localMemory.add(testConvId, List.of(
                new UserMessage(String.format("Mesaj #%d", i))
            ));
        }

        List<Message> remaining = localMemory.get(testConvId);
        List<Map<String, Object>> formatted = remaining.stream()
            .map(m -> Map.<String, Object>of(
                "role", m.getMessageType().name(),
                "content", m.getText()
            ))
            .toList();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("windowsSize", 5);
        response.put("addedCount", 7);
        response.put("remainingCount", remaining.size());
        response.put("messages", formatted);

        return response;

    }


    @DeleteMapping("/window/clear/{convId}")
    public Map<String, Object> clearHistory(@PathVariable String convId) {

        chatMemory.clear(convId);
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("convId", convId);
        response.put("remainingMessages", chatMemory.get(convId).size());

        return response;
    }

    // MEMORY CONFIG — ChatMemoryRepository + ChatMemory @Bean
    // InMemoryChatMemoryRepository + pencere katmani.
    @Configuration
    static class MemoryConfig {

        /**
         * InMemoryChatMemoryRepository ile ChatMemory bean'ini olusturur.
         * InMemory: RAM'de Map tutar; restart = sifirla.
         */
        @Bean
        // Spring bu metodu cagirarak bean'i context'e ekler
        ChatMemory chatMemory() {
            InMemoryChatMemoryRepository repository = new InMemoryChatMemoryRepository(); // RAM deposu
            return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository) // Depo kaynagini bagla — İleride JDBC olacak
                .maxMessages(10)                  // Pencere: 10 mesajdan fazlasi duser
                .build();
        }
    }
}