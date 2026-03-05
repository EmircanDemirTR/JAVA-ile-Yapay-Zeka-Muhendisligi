// Swagger UI  : http://localhost:8080/swagger-ui.html
// Test (POST) : http://localhost:8080/api/b911/ingest?source=data/spring-ai-intro.txt
// Test (POST) : http://localhost:8080/api/b911/ingest-all
// Test (GET)  : http://localhost:8080/api/b911/search?q=Spring%20AI

package com.javaai.bolum09;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("pgvector")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b911")
public class UctanUcaEtlDemo {

    private static final List<String> SUPPORTED_SOURCES = List.of(
        "data/spring-ai-intro.txt",       // TXT format — TextReader ile okunur
        "data/java-temelleri.md",         // Markdown format — TikaDocumentReader ile okunur
        "data/urunler.json",             // JSON format — JsonReader ile okunur
        "data/yapay-zeka-sozlugu.txt"    // TXT format — farkli konu alani, retrieval cesitliligi icin
    );

    private final VectorStore vectorStore;

    private IngestResult lastIngestResult = new IngestResult(0, 0, 0); // Bos rapor ile basla

    public UctanUcaEtlDemo(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        SpringApplication.run(UctanUcaEtlDemo.class, args);
    }


    @PostMapping("/ingest")
    public Map<String, Object> ingest(
        @RequestParam(defaultValue = "data/spring-ai-intro.txt") String source
    ) {
        if (!SUPPORTED_SOURCES.contains(source)) { // Desteklenmeyen dosya yolu kontrolu
            return Map.of("error", "Desteklenmeyen source: " + source);
        }

        DocumentReader reader = createReader(source); // Dosya uzantisina gore uygun reader sec — TXT=TextReader, JSON=JsonReader, diger=Tika

        List<Document> rawDocuments = reader.get(); // Kaynak dosyayi oku ve Document listesine cevir

        TokenTextSplitter splitter = createDefaultSplitter(); // Ortak chunk ayarlariyla splitter olustur

        List<Document> chunks = splitter.apply(rawDocuments); // Ham metni parcalara bol — rawDocuments → N chunk
        vectorStore.add(chunks); // Parcalari vektor deposuna yaz — her chunk icin embedding otomatik uretilir ve pgvector'e INSERT edilir

        // chunksCreated = TextSplitter ciktisi (parcalama sonucu),
        lastIngestResult = new IngestResult(rawDocuments.size(), chunks.size(), chunks.size()); // Raporu guncelle

        return Map.of(
            "source", source,
            "report", lastIngestResult
        );
    }

    @PostMapping("/ingest-all")
    public Map<String, Object> ingestAll() {
        int totalRead = 0;   // Tum kaynaklar icin toplam ham dokuman sayisi
        int totalChunks = 0; // Tum kaynaklar icin toplam chunk sayisi

        for (String source : SUPPORTED_SOURCES) {
            DocumentReader reader = createReader(source); // Bu kaynaga uygun reader sec

            List<Document> rawDocuments = reader.get(); // Kaynak dosyayi oku — reader otomatik parse yapar
            TokenTextSplitter splitter = createDefaultSplitter(); // Ayni chunk stratejisi her kaynak icin — tutarli bolme

            List<Document> chunks = splitter.apply(rawDocuments); // Parcala — her dokuman → N chunk
            vectorStore.add(chunks); // Depoya yaz — embedding uretilip pgvector'e yazilir

            totalRead += rawDocuments.size();
            totalChunks += chunks.size();
        }

        lastIngestResult = new IngestResult(totalRead, totalChunks, totalChunks); // Toplu raporu kaydet

        return Map.of(
            "sources", SUPPORTED_SOURCES,
            "report", lastIngestResult
        );
    }

    @GetMapping("/ingest-report")
    public Map<String, Object> ingestReport() {
        return Map.of(
            "lastIngestResult", lastIngestResult
        );
    }

    @GetMapping("/search")
    public Map<String, Object> search(
        @RequestParam(defaultValue = "Spring AI") String q
    ) {
        if (q.isBlank()) { // Bos sorgu kontrolu — embedding API bos metin istemez
            return Map.of("error", "q bos olamaz");
        }

        SearchRequest request = SearchRequest.builder()
            .query(q) // Kullanici sorgusu — embedding'e cevrilir
            .topK(5) // Ilk 5 aday sonuc — retrieval aday limiti
            .similarityThresholdAll() // Esik yok, tum adaylari listele — filtre kapali
            .build();

        List<Document> results = vectorStore.similaritySearch(request); // Arama yap — embedding benzerlik araması

        Map<String, Object> response = new LinkedHashMap<>(); // Siralama korunan map
        response.put("query", q);
        response.put("resultCount", results.size());
        response.put("results", results.stream().map(document -> Map.of( // Her sonuc icin detay haritasi
            "id", document.getId(),
            "score", document.getScore(),
            "text", document.getText(),
            "metadata", document.getMetadata()
        )).toList());
        return response;
    }

    private DocumentReader createReader(String source) {

        if (source.endsWith(".txt")) {
            return new TextReader(new ClassPathResource(source)); // TextReader — yalnizca TXT icerik okur, basit ve hizli
        }

        if (source.endsWith(".json")) {
            return new JsonReader(new ClassPathResource(source)); // JsonReader — JSON yapisini parse eder, her JSON nesnesi → 1 Document
        }

        return new TikaDocumentReader(new ClassPathResource(source)); // Diger formatlar (MD, PDF vb.) icin Tika
    }

    private TokenTextSplitter createDefaultSplitter() {
        return TokenTextSplitter.builder()
            .withChunkSize(500)
            .withMinChunkSizeChars(200) // 200 karakter alti atilir
            .withMinChunkLengthToEmbed(5) // 5 token alti atilir
            .withMaxNumChunks(200) // — chunk sayisi ust sinir
            .withKeepSeparator(true) // ayiricilar silinmez
            .build();
    }

    record IngestResult(int docsRead, int chunksCreated, int chunksStored) {

        IngestResult {
        }
    }
}