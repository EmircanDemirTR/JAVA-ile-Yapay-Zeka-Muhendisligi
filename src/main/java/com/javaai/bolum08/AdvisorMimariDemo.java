package com.javaai.bolum08;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b81")

public class AdvisorMimariDemo {
    // CallAdvisor - call akisinda çalışır, senkron istekleri sarar
    // StreamAdvisor - stream akışında çalışır, Flux tabanlı parcali yanıtları sarar
    // BaseAdvisor - call + stream icin ortak bir temel kolaylık katmanı saglar

    private final ChatClient chatClient; // Normal siralamali advisor chain
    private final ChatClient chatClientReversed; // Ters sıralamalı advisor chain
    private final ChatClient streamClient; // Streaming icin (advivor'suz)


    public AdvisorMimariDemo(ChatClient.Builder builder) {

        // Normal: Timing(0) -> Logging(1)
        this.chatClient = builder.clone()
            .defaultSystem("Sen yardimci bir asistansin")
            .defaultAdvisors(new TimingCallAdvisor(), new LoggingCallAdvisor())
            .build();

        // Ters: Logging(0) -> Timing(1)
        LoggingCallAdvisor loggingFirst = new LoggingCallAdvisor() {
            @Override
            public int getOrder() {
                return 0;
            }
        };
        TimingCallAdvisor timingSecond = new TimingCallAdvisor() {
            @Override
            public int getOrder() {
                return 1;
            }
        };
        this.chatClientReversed = builder.clone()
            .defaultSystem("Sen yardimci bir asistansin")
            .defaultAdvisors(loggingFirst, timingSecond)
            .build();

        // Stream client - StreamAdvisor
        this.streamClient = builder.clone()
            .defaultSystem("Sen yardimci bir asistansin")
            .defaultAdvisors(new LoggingStreamAdvisor())
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(AdvisorMimariDemo.class, args);
    }

    @GetMapping("/call")
    public Map<String, Object> callWithAdvisors(@RequestParam(defaultValue = "Java nedir?") String message) {
        String answer = chatClient.prompt()
            .user(message)
            .call()
            .content();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", message);
        result.put("answer", answer);
        result.put("advisorChain", "TimingCallAdvisor(0) -> LoggingCallAdvisor(1)");
        result.put("consoleNote", "Konsolu kontrol edin — advisor log'lari gorunur");
        return result;
    }

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public Flux<String> streamResponse(@RequestParam(defaultValue = "Spring Boot nedir?") String message) {
        return streamClient.prompt()
            .user(message)
            .stream()
            .content()
            .doOnSubscribe(s -> LoggerFactory.getLogger(getClass()).info("[StreamDemo] SSE basladi"))
            .doOnComplete(() -> LoggerFactory.getLogger(getClass()).info("[StreamDemo] SSE tamamlandi"));
    }

    @GetMapping("/order-demo")
    public Map<String, Object> orderDemo(
        @RequestParam(defaultValue = "Merhaba") String message,
        @RequestParam(defaultValue = "false") boolean reverse
    ) {
        ChatClient selectedClient = reverse ? chatClientReversed : chatClient;
        String answer = selectedClient.prompt()
            .user(message)
            .call()
            .content();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", message);
        result.put("reverse", reverse);
        result.put("orderInfo", reverse
            ? "LoggingCallAdvisor(0) -> TimingCallAdvisor(1)"
            : "TimingCallAdvisor(0) -> LoggingCallAdvisor(1)");
        result.put("answer", answer);
        result.put("consoleNote", "Konsol ciktisini karsilastirin");
        return result;
    }

    static class LoggingCallAdvisor implements CallAdvisor {

        private static final Logger log = LoggerFactory.getLogger(LoggingCallAdvisor.class);

        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            String promptContent = request.prompt().getContents() == null ? "" : request.prompt().getContents();
            log.info("[LoggingAdvisor] Istek alindi, mesaj uzunlugu: {} karakter", promptContent.length());

            ChatClientResponse response = chain.nextCall(request);
            log.info("[LoggingAdvisor] Yanit alindi");
            return response;
        }

        @Override
        public String getName() {
            return "LoggingCallAdvisor";
        }

        @Override
        public int getOrder() {
            return 1; // Timing'den sonra
        }
    }

    static class TimingCallAdvisor implements CallAdvisor {

        private static final Logger log = LoggerFactory.getLogger(TimingCallAdvisor.class);

        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            long start = System.nanoTime();
            ChatClientResponse response = chain.nextCall(request);
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info("[TimingAdvisor] LLM cagrisi {} ms surdu", durationMs);
            return response;
        }

        @Override
        public String getName() {
            return "TimingCallAdvisor";
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }

    static class LoggingStreamAdvisor implements StreamAdvisor {

        private static final Logger log = LoggerFactory.getLogger(LoggingStreamAdvisor.class);

        @Override
        public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
            log.info("[StreamAdvisor] Stream istegi alindi");
            return chain.nextStream(request)
                .doOnComplete(() -> log.info("[StreamAdvisor] Stream tamamlandi"));
        }

        @Override
        public String getName() {
            return "LoggingStreamAdvisor";
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }
}
