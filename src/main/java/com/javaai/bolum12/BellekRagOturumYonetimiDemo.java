package com.javaai.bolum12;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
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

@Profile("ragmemory")
@EnableAutoConfiguration
@RestController
@RequestMapping(value = "/api/b125", produces = "application/json;charset=UTF-8")

public class BellekRagOturumYonetimiDemo {

    private static final String LESSON_CODE = "b125";
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.5;

    private final ChatClient chatClient;            // Memory + RAG advisor zinciri ile donanimli
    private final ChatClient.Builder clientBuilder; // order-compare'da farkli sira test etmek icin sakla
    private final ChatMemory chatMemory;             // JDBC tabanli; gecmis okuma icin
    private final VectorStore vectorStore;           // pgvector tabanli; RAG belgeleri


    public BellekRagOturumYonetimiDemo(
        ChatClient.Builder builder, ChatMemory chatMemory, VectorStore vectorStore) {

        this.chatMemory = chatMemory;   // JDBC ChatMemory referansini sakla
        this.vectorStore = vectorStore; // pgvector referansini sakla
        this.clientBuilder = builder;   // order-compare icin builder'i koru — kopyalama burada degil

        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build(); // JDBC tabanli bellek

        QuestionAnswerAdvisor ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
            .searchRequest(SearchRequest.builder()
                .topK(DEFAULT_TOP_K)
                .similarityThreshold(DEFAULT_THRESHOLD)
                .build())
            .build(); // VectorStore'a baglanmis RAG advisor

        this.chatClient = builder.clone() // Builder'i kopyala — clientBuilder'i kirletme
            .defaultSystem("Sen Spring AI konularinda uzman bir asistansin."
                + "Baglami ve konusma gecmisini kullanarak kisa ve net cevaplar ver.")
            .defaultAdvisors(memoryAdvisor, ragAdvisor) // SIRA: Memory(1) -> RAG(2)
            .build(); // Memory + RAG destekli ChatClient hazir
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "ragmemory"); // JDBC + pgvector profili
        SpringApplication.run(BellekRagOturumYonetimiDemo.class, args);
    }

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        Map<String, Object> meta = Map.of("source", "b125-seed", "lesson", LESSON_CODE); // lesson filtresi
        List<Document> documents = List.of(
            new Document(UUID.randomUUID().toString(), "ChatMemory, Spring AI'da konusma gecmisini yoneten API'dir.", meta),
            new Document(UUID.randomUUID().toString(), "MessageWindowChatMemory, son N mesaji tutan kayan pencere implementasyonudur.", meta),
            new Document(UUID.randomUUID().toString(), "MessageChatMemoryAdvisor, ChatClient'a bellek yetenegini otomatik kazandiran advisor'dur.",
                meta),
            new Document(UUID.randomUUID().toString(), "JDBC ChatMemoryRepository, konusma gecmisini PostgreSQL'de kalici saklar.", meta),
            new Document(UUID.randomUUID().toString(), "RAG, Retrieval-Augmented Generation anlamina gelir. Modele dis bilgi kaynagi saglar.", meta),
            new Document(UUID.randomUUID().toString(), "Advisor sirasi onemli: Memory-first once gecmis ekler, RAG-first once belgeler ekler.", meta)
        );
        vectorStore.add(documents); // pgvector'a yaz; embedding otomatik uretilir
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("indexedCount", documents.size()); // 6 belge
        response.put("lessonFilter", LESSON_CODE);       // b125 filtresi
        return response;
    }

    @PostMapping("/chat/{convId}") // POST: JDBC bellek + pgvector RAG birlesik sohbet
    public Map<String, Object> chat(@PathVariable String convId, @RequestParam String message) {

        String answer = chatClient.prompt().user(message)
            .advisors(a -> a
                .param("chat_memory_conversation_id", convId)     // Memory: oturum
                .param("qa_filter_expression",
                    String.format("lesson == '%s'", LESSON_CODE)))   // RAG: b125
            .call().content();

        int historySize = chatMemory.get(convId).size(); // JDBC'deki guncel mesaj sayisi

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("convId", convId);
        response.put("query", message);
        response.put("answer", answer);              // Memory + RAG destekli yanit
        response.put("historySize", historySize);    // JDBC mesaj sayisi

        return response;
    }

    @GetMapping("/order-compare/{convId}") // GET: Memory-first vs RAG-first karsilastirmasi — ayni soru, farkli sira
    public Map<String, Object> orderCompare(
        @PathVariable String convId,
        @RequestParam(defaultValue = "ChatMemory nedir ve nasil calisir?") String question) {

        int historySize = chatMemory.get(convId).size(); // Kiyaslama oncesi gecmis boyutu
        FilterExpressionBuilder fb = new FilterExpressionBuilder(); // FilterExpression DSL

        List<Document> retrieved = vectorStore.similaritySearch(SearchRequest.builder()
            .query(question).topK(DEFAULT_TOP_K).similarityThreshold(DEFAULT_THRESHOLD)
            .filterExpression(fb.eq("lesson", LESSON_CODE).build()).build()); // kac belge var?

        // SIRA 1: Memory-first — ana chatClient: defaultAdvisors(memory, rag)
        String answer1 = chatClient.prompt().user(question)
            .advisors(a -> a.param("chat_memory_conversation_id", convId)
                .param("qa_filter_expression", String.format("lesson == '%s'", LESSON_CODE)))
            .call().content(); // Once gecmis, sonra RAG — Memory-first yaniti

        // SIRA 2: RAG-first — yeni istemci: defaultAdvisors(rag, memory)
        String answer2 = clientBuilder.clone()
            .defaultSystem("Sen Spring AI konularinda uzman bir asistansin.")
            .defaultAdvisors(
                QuestionAnswerAdvisor.builder(vectorStore).searchRequest(
                    SearchRequest.builder().topK(DEFAULT_TOP_K)
                        .similarityThreshold(DEFAULT_THRESHOLD).build()).build(), // RAG once
                MessageChatMemoryAdvisor.builder(chatMemory).build())                     // Memory sonra
            .build()
            .prompt().user(question)
            .advisors(a -> a.param("chat_memory_conversation_id", convId + "-compare")
                .param("qa_filter_expression", String.format("lesson == '%s'", LESSON_CODE)))
            .call().content(); // Once belgeler, sonra gecmis — RAG-first yaniti

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();

        response.put("lesson", "B12.5");
        response.put("question", question);
        response.put("retrievedDocCount", retrieved.size());    // Kac belge bulundu
        response.put("historySize", historySize);               // Kiyaslama oncesi gecmis boyutu
        response.put("memoryFirst", Map.of("advisorOrder", "Memory -> RAG", "answer", answer1));
        response.put("ragFirst", Map.of("advisorOrder", "RAG -> Memory", "answer", answer2));

        return response;
    }

    @GetMapping("/history/{convId}") // GET: JDBC'deki oturum gecmisini dondurur; detay: B12.4
    public Map<String, Object> getHistory(@PathVariable String convId) {

        List<Message> history = chatMemory.get(convId); // JDBC'den bu oturumun gecmisini al

        List<Map<String, Object>> formatted = history.stream()
            .map(m -> Map.<String, Object>of(
                "role", m.getMessageType().name(), // USER, ASSISTANT
                "content", m.getText()))           // Mesaj metni
            .toList();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("lesson", "B12.5");
        response.put("convId", convId);
        response.put("messageCount", history.size()); // JDBC'deki mesaj sayisi
        response.put("storageType", "JDBC");
        response.put("messages", formatted);
        return response;
    }

    @DeleteMapping("/history/{convId}") // DELETE: JDBC'den bu convId'in tum satirlarini sil
    public Map<String, Object> clearHistory(@PathVariable String convId) {

        chatMemory.clear(convId); // JDBC'den bu convId'e ait tum satirlari sil
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("lesson", "B12.5");
        response.put("convId", convId);
        response.put("cleared", true);
        response.put("remainingMessages", chatMemory.get(convId).size()); // 0 olmali
        return response;
    }

    @Configuration
    static class MemoryConfig {

        @Bean
        ChatMemory chatMemory(ChatMemoryRepository repository) {
            return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository) // JDBC deposunu bagla
                .maxMessages(20)                  // Son 20 mesaji LLM'e gonder
                .build();
        }
    }
}