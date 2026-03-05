package com.javaai.bolum10;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Content;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("pgvector")
@EnableAutoConfiguration
@Import(Bolum10SeedVeriHazirla.class)
@RestController
@RequestMapping(value = "/api/b105", produces = "application/json;charset=UTF-8")

public class SorguDonusturmeDemo {

    private static final String LESSON_CODE = "b105";
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.3;

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;
    private final Bolum10SeedVeriHazirla seedService;


    public SorguDonusturmeDemo(ChatClient.Builder chatClientBuilder,
        VectorStore vectorStore,
        Bolum10SeedVeriHazirla seedService) {
        this.chatClientBuilder = chatClientBuilder;
        this.vectorStore = vectorStore;
        this.seedService = seedService;
    }


    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        SpringApplication.run(SorguDonusturmeDemo.class, args);
    }


    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return seedService.seedLesson(LESSON_CODE);  // DELETE + INSERT — idempotent
    }


    @GetMapping("/rewrite")
    public Map<String, Object> rewrite(@RequestParam(defaultValue = "bu ne") String q) {
        RewriteQueryTransformer rewriter = RewriteQueryTransformer.builder()
            .chatClientBuilder(chatClientBuilder.clone())
            .targetSearchSystem("vector store")
            .build();

        Query originalQuery = new Query(q); // Ham sorgu
        Query rewrittenQuery = rewriter.transform(originalQuery); // Dönüştürülmüş sorgu

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("originalQuery", originalQuery.text());
        response.put("rewrittenQuery", rewrittenQuery.text());
        return response;
    }

    @GetMapping("/multi-query")
    public Map<String, Object> multiQuery(@RequestParam(defaultValue = "Spring AI RAG") String q) {

        MultiQueryExpander expander = MultiQueryExpander.builder()
            .chatClientBuilder(chatClientBuilder.clone())
            .numberOfQueries(3) // Kaç farklı sorgu üretileceği
            .includeOriginal(true) // Orijinal sorguyu da sonuçlara dahil et
            .build();

        List<Query> expandedQueries = expander.expand(new Query(q)); // Genişletilmiş sorgular
        List<String> queryTexts = expandedQueries.stream()
            .map(Query::text)
            .toList();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("originalQuery", q);
        response.put("expandedQueries", queryTexts);
        return response;
    }

    @GetMapping("/compress")
    public Map<String, Object> compress(@RequestParam(defaultValue = "Daha fazla anlat") String q) {

        List<Message> conversationHistory = List.of(
            new UserMessage("Java'da Spring AI nedir?"),
            new AssistantMessage("Spring AI, Java uygulamalarinda LLM entegrasyonu saglayan bir frameworktur."),
            new UserMessage(q)
        );

        CompressionQueryTransformer compressor = CompressionQueryTransformer.builder()
            .chatClientBuilder(chatClientBuilder.clone())
            .build();

        Query originalQuery = Query.builder()
            .text(q)
            .history(conversationHistory)
            .build();

        Query compressedQuery = compressor.transform(originalQuery);

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("originalQuery", q);
        response.put("conversationContext",
            conversationHistory.stream().map(Content::getText).toList()); // Gecmis metinleri
        response.put("compressedQuery", compressedQuery.text());    // Netlesmis bagimsiz sorgu

        return response;
    }


    @GetMapping("full-pipeline")
    public Map<String, Object> fullPipeline(@RequestParam(defaultValue = "Spring AI retrieval nasil iyilestirilir?") String q) {

        // Adim 1: Rewrite — belirsiz sorguyu netlestir (LLM cagrisi yapar)
        RewriteQueryTransformer rewriter = RewriteQueryTransformer.builder()
            .chatClientBuilder(chatClientBuilder.clone())  // Her transformer icin bagimsiz kopya
            .build();

        // Adim 2: Expander — netlesmis sorgudan 3 varyant uret + orijinal = 4 sorgu
        MultiQueryExpander expander = MultiQueryExpander.builder()
            .chatClientBuilder(chatClientBuilder.clone())
            .numberOfQueries(3).includeOriginal(true)  // 3 yeni + 1 orijinal
            .build();

        // Adim 3: Retriever — her varyant icin VectorStore'dan belge getir
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .similarityThreshold(DEFAULT_THRESHOLD)                            // Minimum esik
            .topK(DEFAULT_TOP_K)                                                // Maksimum belge
            .filterExpression(filterBuilder.eq("lesson", LESSON_CODE).build()) // Sadece b105
            .build();

        // Adim 4: Augmenter — belgeleri prompt'a ekle, bos baglamda hallucination engelle
        ContextualQueryAugmenter augmenter = ContextualQueryAugmenter.builder()
            .allowEmptyContext(false).build();

        // Tum adimlari tek pipeline'a bagla
        RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
            .queryTransformers(rewriter)   // Pre-retrieval: rewrite
            .queryExpander(expander)       // Pre-retrieval: expand
            .documentRetriever(retriever)  // Retrieval
            .queryAugmenter(augmenter)     // Augmentation
            .build();

        ChatClient ragClient = chatClientBuilder.clone()
            .defaultSystem("Yaniti yalnizca baglama dayanarak ver.")
            .defaultAdvisors(advisor)                                 // Full pipeline
            .build();

        String answer = ragClient.prompt()
            .advisors(spec -> spec.param("vector_store_filter_expression", "lesson == 'b105'"))  // spec -> AdvisorSpec: ders filtresi
            .user(q)    // Kullanici sorusu
            .call()     // Senkron LLM cagrisi — tum adimlar sirayla calisir
            .content(); // Metin yanit

        // Ham retrieval — pipeline'in hangi belgeleri kullandığını gosterir
        List<Document> retrieved = vectorStore.similaritySearch(SearchRequest.builder()
            .query(q).topK(DEFAULT_TOP_K).similarityThreshold(DEFAULT_THRESHOLD)
            .filterExpression(new FilterExpressionBuilder().eq("lesson", LESSON_CODE).build())
            .build());

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();

        response.put("query", q);
        response.put("resultCount", retrieved.size());           // Ham retrieval sonuc sayisi
        response.put("results", retrieved.stream().map(d -> Map.of(
            "id", d.getId(), "score", d.getScore(),          // UUID ve benzerlik skoru (0-1)
            "text", d.getText(), "metadata", d.getMetadata() // Icerik ve topic/source alanlari
        )).toList());
        response.put("answer", answer);                          // Full pipeline LLM cevabi
        response.put("notes", "Full pipeline: rewrite -> expand -> retrieve -> augment -> LLM.");

        return response; // JSON: lesson, query, resultCount, results, answer, notes
    }
}