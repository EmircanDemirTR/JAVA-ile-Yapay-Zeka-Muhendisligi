package com.javaai.bolum12;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
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

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping(value = "/api/b123", produces = "application/json;charset=UTF-8")
public class MessageChatMemoryAdvisorDemo {

    // chatClient — MessageChatMemoryAdvisor EKLI; otomatik bellek yazar ve okur
    private final ChatClient chatClient;

    // statelessClient — Advisor YOK; belleksiz kontrol grubu
    private final ChatClient statelessClient;

    // chatMemory — Gecmisi okumak icin direkt erisim
    private final ChatMemory chatMemory;

    public MessageChatMemoryAdvisorDemo(ChatClient.Builder builder, ChatMemory chatMemory) {
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        this.chatClient = builder.clone()
            .defaultSystem(
                "Sen yardımcı bir Java asistanısın. "
                    + "Sohbet bağlamını, kullanıcının adını ve verdiği bilgileri hafızanda tutarak cevap ver."
                    + "Kısa ve net cevaplar ver.")
            .defaultAdvisors(memoryAdvisor)
            .build();

        this.statelessClient = builder.clone()
            .defaultSystem("Sen yardimci bir Java asistanisin. Kisa ve net cevaplar ver.")
            .build();

        this.chatMemory = chatMemory;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai");
        SpringApplication.run(MessageChatMemoryAdvisorDemo.class, args);
    }

    @PostMapping("/chat/{convId}") // Bellekli sohbet
    public Map<String, Object> chat(@PathVariable String convId, @RequestParam String message) {

        String answer = chatClient
            .prompt()
            .user(message)
            .advisors(a -> a
                .param("chat_memory_conversation_id", convId))
            .call()
            .content();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("convId", convId);       // Aktif oturum
        response.put("query", message);
        response.put("answer", answer);        // Bellekli yanit
        response.put("memoryEnabled", true);   // Advisor aktif
        return response;
    }

    @GetMapping("/chat-stateless") // Belleksiz sohbet
    public Map<String, Object> chatStateless(
        @RequestParam(defaultValue = "Benim adim neydi? Lutfen soyler misin?") String message) {

        String answer = statelessClient
            .prompt()
            .user(message)
            .call()
            .content();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("query", message);
        response.put("answer", answer);          // Belleksiz yanit
        response.put("memoryEnabled", false);
        return response;
    }

    // 3) GET /history/{convId} — Advisor'in kaydettigi gecmisi okuma
    @GetMapping("/history/{convId}")
    public Map<String, Object> getHistory(@PathVariable String convId) {

        List<Message> history = chatMemory.get(convId);
        List<Map<String, Object>> formatted = history.stream()
            .map(m -> Map.<String, Object>of(
                "role", m.getMessageType().name(), // USER, ASSISTANT
                "content", m.getText()))           // Mesaj metni
            .toList();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("convId", convId);
        response.put("messageCount", history.size()); // Advisor'in kaydettigi mesaj sayisi
        response.put("messages", formatted);

        return response;
    }

    @DeleteMapping("/history/{convId}") // DELETE: chatMemory.clear(convId) — oturum gecmisini sifirla
    public Map<String, Object> clearHistory(@PathVariable String convId) {

        chatMemory.clear(convId); // Bu convId'e ait tum mesajlari sil

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("convId", convId);
        response.put("cleared", true);
        response.put("remainingMessages", chatMemory.get(convId).size()); // 0 olmali
        return response;
    }


    @GetMapping("/canli-demo")
    public Map<String, Object> canliDemo() {

        String demoConvId = "canli-demo";

        String answer1 = chatClient
            .prompt()
            .user("Benim adim Emircan. Bunu lutfen hatirla.")
            .advisors(a -> a
                .param("chat_memory_conversation_id", demoConvId))
            .call()
            .content();

        String answer2 = chatClient
            .prompt()
            .user("Java 25 en yeni LTS versiyonudur. Bu bilgiyi de not et.")
            .advisors(a -> a
                .param("chat_memory_conversation_id", demoConvId))
            .call()
            .content();

        String answer3 = chatClient
            .prompt()
            .user("Benim adim neydi? Lutfen soyler misin?")
            .advisors(a -> a
                .param("chat_memory_conversation_id", demoConvId))
            .call()
            .content();

        int historySize = chatMemory.get(demoConvId).size(); // Kac mesaj depolandi: 6 olmali

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("convId", demoConvId);
        response.put("step1_prompt", "Benim adim Emircan. Bunu lutfen hatirla!");
        response.put("step1_answer", answer1);
        response.put("step2_prompt", "Java 25 en yeni LTS versiyondur.");
        response.put("step2_answer", answer2);
        response.put("step3_prompt", "Benim adim neydi?");
        response.put("step3_answer", answer3);
        response.put("totalMessages", historySize);    // 6: 3 user + 3 assistant mesaji

        return response;


    }


    @Configuration
    static class MemoryConfig {

        @Bean
        ChatMemory chatMemory() {
            return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
        }
    }
}