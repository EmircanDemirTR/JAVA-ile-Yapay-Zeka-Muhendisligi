package com.javaai.bolum14;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping(value = "/api/b143", produces = "application/json;charset=UTF-8")
public class CachingMaliyetOptimizasyonDemo {


    private static final double PROMPT_COST_PER_1K = 0.0025;    // $2.50/1M token = $0.0025/1K
    private static final double COMPLETION_COST_PER_1K = 0.0100; // $10.00/1M token = $0.0100/1K


    private final ChatClient chatClient;
    private final AtomicLong totalPromptTokens = new AtomicLong(0);     // Toplam prompt token sayaci
    private final AtomicLong totalCompletionTokens = new AtomicLong(0); // Toplam completion token sayaci
    private final AtomicLong totalRequests = new AtomicLong(0);         // Toplam istek sayaci
    private final CacheManager cacheManager;                             // Caffeine istatistikleri icin


    public CachingMaliyetOptimizasyonDemo(ChatClient.Builder builder, CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        this.chatClient = builder.clone()
            .defaultSystem("Sen yardimci bir asistansin. Kisa ve oz cevaplar ver.")
            .defaultAdvisors(new CostTrackingAdvisor(totalPromptTokens, totalCompletionTokens))
            .build();
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai");
        System.setProperty("spring.cache.type", "caffeine");        // application.yml'deki none'u override
        SpringApplication.run(CachingMaliyetOptimizasyonDemo.class, args);
    }


    // Cache'siz Soru
    @GetMapping("/ask-no-cache")
    public Map<String, Object> askNoCache(
        @RequestParam(defaultValue = "Java nedir?") String message) {

        totalRequests.incrementAndGet();  // Istek sayaci — cache'siz de sayilir

        String answer = chatClient.prompt()
            .user(message)
            .call()
            .content();
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("lesson", "B15.3");
        response.put("answer", answer);
        response.put("source", "llm");
        return response;
    }


    // Cache'li Soru
    @GetMapping("/ask-cached")
    public Map<String, Object> askCached(
        @RequestParam(defaultValue = "Java nedir?") String message
    ) {
        totalRequests.incrementAndGet();
        var cache = cacheManager.getCache("ai-responses");
        String answer = null;
        boolean fromCache = false;

        if (cache != null) {
            answer = cache.get(message, String.class);
            if (answer != null) {
                fromCache = true;
            }
        }

        if (answer == null) {
            answer = chatClient.prompt()
                .user(message)
                .call()
                .content();
            if (cache != null) {
                cache.put(message, answer);
            }
        }

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("answer", answer);
        response.put("source", fromCache ? "cache" : "llm");
        return response;
    }


    @CacheEvict(value = "ai-responses", allEntries = true)
    @PostMapping("/cache-evict")
    public Map<String, Object> cacheEvict() {
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("action", "Tum AI response cache'i temizlendi.");
        return response;
    }


    static class CostTrackingAdvisor implements CallAdvisor {

        private final AtomicLong promptTokens;
        private final AtomicLong completionTokens;

        CostTrackingAdvisor(AtomicLong promptTokens, AtomicLong completionTokens) {
            this.completionTokens = completionTokens;
            this.promptTokens = promptTokens;
        }

        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            ChatClientResponse response = chain.nextCall(request);

            ChatResponse chatResponse = response.chatResponse();
            if (chatResponse != null && chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                promptTokens.addAndGet(usage.getPromptTokens());
                completionTokens.addAndGet(usage.getCompletionTokens());
            }
            return response;
        }

        @Override
        public String getName() {
            return "CostTrackingAdvisor";
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }

    @Configuration
    @EnableCaching
    static class CacheConfig {

        @Bean
        CacheManager cacheManager() {
            CaffeineCacheManager manager = new CaffeineCacheManager("ai-responses", "embeddings");
            manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats());

            return manager;
        }
    }
}