package com.javaai.bolum12;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
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


@Profile("chatmemory") // Bu profil application-chatmemory.yml'i aktif eder: JDBC Chat Memory + PostgreSQL
@EnableAutoConfiguration
@RestController
@RequestMapping(value = "/api/b124", produces = "application/json;charset=UTF-8")

public class VeritabaniKaliciBellekDemo {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory; // JDBC tabanli ChatMemory; mesajlar PostgreSQL'de saklanir

    public VeritabaniKaliciBellekDemo(ChatClient.Builder builder, ChatMemory chatMemory) {

        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
            .build();

        this.chatClient = builder
            .clone()
            .defaultSystem("Sen yardimci bir Java asistanisin. Baglama sadik kalarak, kisa ve net cevaplar ver.")
            .defaultAdvisors(memoryAdvisor)
            .build();

        this.chatMemory = chatMemory;
    }


    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "chatmemory"); // JDBC + PostgreSQL profili
        SpringApplication.run(VeritabaniKaliciBellekDemo.class, args); // Spring Boot context baslatir
    }


    @PostMapping("/chat/{convId}") // POST: JDBC tabanli bellekli sohbet — mesajlar PostgreSQL'e yazilir
    public Map<String, Object> chat(@PathVariable String convId, @RequestParam String message) {

        String answer = chatClient
            .prompt()
            .user(message)
            .advisors(a -> a
                .param("chat_memory_conversation_id", convId))
            .call()
            .content();

        int historySize = chatMemory.get(convId).size();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("convId", convId);                // Hangi oturum
        response.put("query", message);                // Gonderilen soru
        response.put("answer", answer);                // Modelin yaniti
        response.put("storageType", "JDBC");           // PostgreSQL'de saklaniyor
        response.put("totalMessages", historySize);    // Toplam kaydedilmis mesaj

        return response;
    }


    @GetMapping("/history/{convId}") // GET: PostgreSQL'deki oturum gecmisini sirali dondurur
    public Map<String, Object> getHistory(@PathVariable String convId) {

        List<Message> history = chatMemory.get(convId);

        List<Map<String, Object>> formatted = history.stream()
            .map(m -> Map.<String, Object>of(
                "role", m.getMessageType().name(), // USER, ASSISTANT
                "content", m.getText()))           // Mesaj icerigi
            .toList();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("convId", convId);               // Hangi oturum
        response.put("messageCount", history.size()); // PostgreSQL'deki mesaj sayisi
        response.put("storageType", "JDBC");          // PostgreSQL'den geldi
        response.put("maxMessages", 20);              // Pencere boyutu
        response.put("messages", formatted);          // Mesaj listesi

        return response;
    }


    @DeleteMapping("/history/{convId}") // DELETE: PostgreSQL'e DELETE sorgusu — bu convId'in satirlari kaldirilir
    public Map<String, Object> clearHistory(@PathVariable String convId) {

        chatMemory.clear(convId);

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("convId", convId);                                    // Hangi oturum temizlendi
        response.put("cleared", true);                                     // Basarili
        response.put("remainingMessages", chatMemory.get(convId).size()); // 0 olmali
        return response;
    }


    @GetMapping("/persistence-check/{convId}") // GET: restart'tan sonra mesajlar duruyor mu? Kalicilik testi
    public Map<String, Object> persistenceCheck(@PathVariable String convId) {

        List<Message> history = chatMemory.get(convId); // PostgreSQL sorgusu: bu oturum var mi?

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("convId", convId);              // Hangi oturum sorgulanidigi
        response.put("messageCount", history.size()); // Kac mesaj var

        if (history.isEmpty()) {
            // Oturum bos — sohbet baslatilmamis veya temizlenmis
            response.put("status", "BOS");            // Bos oturum

        } else {
            response.put("status", "DOLU");                             // Mesaj var
            response.put("lastMessage", history.getLast().getText());   // Son mesaji goster
        }

        return response;
    }


    // B12.3'ten fark: chatMemoryRepository otomatik enjekte edilen JdbcChatMemoryRepository instance'i —
    // Spring auto-config tarafindan saglaniyor.
    @Configuration
    static class MemoryConfig {

        // application-chatmemory.yml'deki initialize-schema: always ayari tabloyu otomatik olusturur.
        @Bean
        ChatMemory chatMemory(ChatMemoryRepository repository) {
            return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository) // JDBC deposunu bagla — B12.2'deki InMemory'den fark
                .maxMessages(20)                  // Pencere boyutu: son 20 mesaj LLM'e gider
                .build();
        }
    }
}