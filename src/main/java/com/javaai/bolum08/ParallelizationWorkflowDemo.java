package com.javaai.bolum08;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/api/b84")

public class ParallelizationWorkflowDemo {

    private final ChatClient chatClient;

    @Autowired
    public ParallelizationWorkflowDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("Sen analiz uzmani bir asistansin. Kisa ve oz cevap ver.")
            .build();
    }

    ParallelizationWorkflowDemo(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public static void main(String[] args) {
        SpringApplication.run(ParallelizationWorkflowDemo.class, args);
    }

    // Sıralı Analiz
    @GetMapping("/serial")
    public Map<String, Object> serial(
        @RequestParam(defaultValue = "Yapay zeka teknolojileri hizla gelisiyor ve hayatimizin her alanina etki ediyor") String text) {

        long startTime = System.nanoTime();

        String sentiment = chatClient.prompt()
            .user(String.format("Su metnin duygusal tonunu analiz et (POZITIF/NEGATIF/NOTR): %s", text))
            .call()
            .content();

        String summary = chatClient.prompt()
            .user(String.format("Su metni tek cumlede ozetle: %s", text))
            .call()
            .content();

        String keywords = chatClient.prompt()
            .user(String.format("Su metinden 3 anahtar kelime cikar, virgul ile ayir: %s", text))
            .call()
            .content();

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        return Map.of(
            "approach", "SERIAL",
            "sentiment", sentiment.trim(),
            "summary", summary.trim(),
            "keywords", keywords.trim(),
            "durationMs", durationMs
        );
    }

    // Paralel Analiz
    @GetMapping("/parallel")
    public Map<String, Object> parallel(
        @RequestParam(defaultValue = "Yapay zeka teknolojileri hizla gelisiyor ve hayatimizin her alanina etki ediyor") String text
    ) {
        long startTime = System.nanoTime();

        try (var executer = Executors.newVirtualThreadPerTaskExecutor()) {

            Future<String> sentimentFuture = executer.submit(() ->
                chatClient.prompt()
                    .user(String.format("Su metnin duygusal tonunu analiz et (POZITIF/NEGATIF/NOTR): %s", text))
                    .call()
                    .content()
            );

            Future<String> summaryFuture = executer.submit(() ->
                chatClient.prompt()
                    .user(String.format("Su metni tek cumlede ozetle: %s", text))
                    .call()
                    .content()
            );

            Future<String> keywordsFuture = executer.submit(() ->
                chatClient.prompt()
                    .user(String.format("Su metinden 3 anahtar kelime cikar, virgul ile ayir: %s", text))
                    .call()
                    .content()
            );

            String sentiment = sentimentFuture.get();
            String summary = summaryFuture.get();
            String keywords = keywordsFuture.get();
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            return Map.of(
                "approach", "PARALLEL",
                "sentiment", sentiment.trim(),
                "summary", summary.trim(),
                "keywords", keywords.trim(),
                "durationMs", durationMs
            );


        } catch (Exception e) {
            return Map.of(
                "error", e.getMessage()
            );
        }
    }

    // Voting Pattern - Cogunluk Oyu
    @GetMapping("/voting")
    public Map<String, Object> voting(
        @RequestParam(defaultValue = "Java'nın en onemli ozelligi nedir?") String question,
        @RequestParam(defaultValue = "3") int count) {

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() ->
                    chatClient.prompt()
                        .user(question)
                        .call()
                        .content()
                ));
            }

            List<String> answers = new ArrayList<>();
            for (Future<String> f : futures) {
                answers.add(f.get().trim());
            }

            // Voting Mekanizması
            Map<String, Long> voteMap = new HashMap<>();
            for (String a : answers) {
                String normalized = a.toLowerCase().trim();
                voteMap.merge(normalized, 1L, Long::sum);
            }

            // En çok oy alan cevabı bulma
            String winningKey = voteMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");

            String selectedAnswer = answers.stream()
                .filter(a -> a.toLowerCase().trim().equals(winningKey))
                .findFirst()
                .orElse("CEVAP ALINAMADI");

            long voteCount = answers.stream().filter(a -> a.equals(selectedAnswer)).count();

            return Map.of(
                "question", question,
                "allAnswers", answers,
                "selectedAnswer", selectedAnswer,
                "voteCount", voteCount
            );

        } catch (Exception e) {
            return Map.of(
                "error", e.getMessage()
            );
        }
    }


}