package com.javaai.bolum09;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("pgvector")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b910")

public class MetadataZenginlestirmeDemo {

    private final ChatModel chatModel; // LLM modeli
    private final VectorStore vectorStore;

    private List<Document> lastEncrichedDocuments; // Son zenginleştirilmiş belgeleri saklamak için

    public MetadataZenginlestirmeDemo(ChatModel chatModel, VectorStore vectorStore) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        org.springframework.boot.SpringApplication.run(MetadataZenginlestirmeDemo.class, args);
    }

    @PostMapping("/enrich/keywords")
    public Map<String, Object> enrichKeywords(@RequestBody EnrichRequest request) {
        Document document = new Document(
            UUID.randomUUID().toString(),
            request.text(),
            Map.of("source", request.source(), "ingestDate", LocalDate.now().toString())
        );

        KeywordMetadataEnricher enricher = KeywordMetadataEnricher.builder(chatModel)// LLM tabanlı zenginleştirici
            .keywordCount(5)
            .build();

        lastEncrichedDocuments = enricher.apply(List.of(document)); // Belgeyi zenginleştir ve sonucu sakla

        return Map.of(
            "metadata", lastEncrichedDocuments.get(0).getMetadata()
        );
    }

    @PostMapping("/enrich/summary")
    public Map<String, Object> enrichSummary(@RequestBody EnrichRequest request) {
        Document document = new Document(
            UUID.randomUUID().toString(),
            request.text(),
            Map.of("source", request.source(), "ingestDate", LocalDate.now().toString())
        );

        SummaryMetadataEnricher enricher = new SummaryMetadataEnricher(
            chatModel,
            List.of(SummaryMetadataEnricher.SummaryType.CURRENT)
        );

        lastEncrichedDocuments = enricher.apply(List.of(document)); // Belgeyi zenginleştir ve sonucu sakla

        return Map.of(
            "metadata", lastEncrichedDocuments.get(0).getMetadata()
        );
    }

    @GetMapping("/search-filtered")
    public Map<String, Object> searchFiltered(
        @RequestParam(defaultValue = "Spring AI") String q,
        @RequestParam(defaultValue = "manual") String source
    ) {
        if (lastEncrichedDocuments.isEmpty()) {
            return Map.of("error", "No enriched documents available. Please enrich a document first.");
        }

        vectorStore.add(lastEncrichedDocuments);

        FilterExpressionBuilder builder = new FilterExpressionBuilder(); // Metadata filtreleme ifadesi oluşturucu
        SearchRequest request = SearchRequest.builder()
            .query(q)
            .topK(5)
            .similarityThresholdAll()
            .filterExpression(builder.eq("source", source).build())
            .build();

        List<Document> results = vectorStore.similaritySearch(request);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("source", source);
        response.put("resultCount", results.size());
        response.put("results", results.stream().map(doc -> Map.of(
            "id", doc.getId(),
            "text", doc.getText(),
            "metadata", doc.getMetadata()
        )).toList());

        return response;
    }

    record EnrichRequest(String text, String source) {

        EnrichRequest {
            if (source == null || source.isBlank()) {
                source = "manuel";
            }
        }
    }
}
