package com.javaai.bolum14;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
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
@RequestMapping(value = "/api/b144", produces = "application/json;charset=UTF-8")
public class ObservabilityMetricsDemo {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityMetricsDemo.class);

    private final ChatClient chatClient;       // AiMetricsAdvisor zincirli client
    private final MeterRegistry meterRegistry; // Micrometer metrik kayit defteri


    // MeterRegistry Spring Boot tarafindan otomatik inject edilir (Actuator aktifse).
    public ObservabilityMetricsDemo(ChatClient.Builder builder, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry; // Metrik kayit defteri
        this.chatClient = builder.clone()
            .defaultSystem("Sen yardimci bir asistansin.")
            .defaultAdvisors(new AiMetricsAdvisor(meterRegistry)) // Metrik toplayan advisor
            .build();
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai");
        SpringApplication.run(ObservabilityMetricsDemo.class, args);
    }


    // Metrik Toplanan Chat
    @GetMapping("/observe-chat")
    public Map<String, Object> observeChat(
        @RequestParam(defaultValue = "Java nedir?") String message) {

        long start = System.nanoTime();
        log.info("AI chat istegi basladi: {}", message);

        String answer = chatClient.prompt()
            .user(message)
            .call()
            .content();

        long durationMs = (System.nanoTime() - start) / 1_000_000; // ns -> ms
        log.info("AI chat istegi tamamlandi: {}ms", durationMs); // Latency logu

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("answer", answer);
        response.put("durationMs", durationMs);
        return response;
    }


    @GetMapping("/metrics-snapshot")
    public Map<String, Object> metricsSnapshot() {
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();

        Timer chatTimer = meterRegistry.find("ai.chat.duration").timer(); // Registry'den timer'ı bul
        response.put("count", chatTimer.count());
        response.put("totalTimeMs", chatTimer.totalTime(TimeUnit.MILLISECONDS));

        Counter successCounter = meterRegistry.find("ai.chat.requests.total").tag("status", "success").counter();
        Counter errorCounter = meterRegistry.find("ai.chat.requests.total").tag("status", "error").counter();

        response.put("BasariliSayisi: ", successCounter.count());
        response.put("HataliSayisi: ", errorCounter != null ? (long) errorCounter.count() : 0);

        Counter promptTokens = meterRegistry.find("ai.chat.tokens").tag("type", "prompt").counter();
        Counter completionTokens = meterRegistry.find("ai.chat.tokens").tag("type", "completion").counter();

        response.put("promptTokens: ", promptTokens.count());
        response.put("completionTokens: ", completionTokens != null ? (long) completionTokens.count() : 0);

        return response;
    }


    static class AiMetricsAdvisor implements CallAdvisor {

        private final MeterRegistry registry;

        AiMetricsAdvisor(MeterRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            Timer.Sample sample = Timer.start(registry);
            try {
                ChatClientResponse response = chain.nextCall(request);
                sample.stop(Timer.builder("ai.chat.duration")
                    .tag("model", "gpt-4o")
                    .tag("status", "success")
                    .register(registry));

                ChatResponse chatResponse = response.chatResponse();
                if (chatResponse != null && chatResponse.getMetadata() != null
                    && chatResponse.getMetadata().getUsage() != null) {
                    var usage = chatResponse.getMetadata().getUsage();
                    registry.counter("ai.chat.tokens", "type", "prompt")
                        .increment(usage.getPromptTokens());      // Prompt token sayaci
                    registry.counter("ai.chat.tokens", "type", "completion")
                        .increment(usage.getCompletionTokens());  // Completion token sayaci
                }
                // Basarili istek sayaci
                registry.counter("ai.chat.requests.total", "status", "success").increment();
                return response;

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String getName() {
            return "AiMetricsAdvisor";
        }

        @Override
        public int getOrder() {
            return 0;
        }

    }

}