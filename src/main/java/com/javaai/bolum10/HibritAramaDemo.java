package com.javaai.bolum10;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("pgvector")
@EnableAutoConfiguration
@Import(Bolum10SeedVeriHazirla.class)
@RestController
@RequestMapping(value = "/api/b107", produces = "application/json;charset=UTF-8")

public class HibritAramaDemo {

    private static final String LESSON_CODE = "b107";

    private static final String VECTOR_TABLE_NAME = "vector_store_b9";

    private static final int DEFAULT_TOP_K = 10;

    // RRF formulu sabiti: 1 / (RRF_K + rank) — 60 akademik literaturde standart deger
    private static final int RRF_K = 60;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final Bolum10SeedVeriHazirla seedService;

    public HibritAramaDemo(VectorStore vectorStore,
        JdbcTemplate jdbcTemplate,
        Bolum10SeedVeriHazirla seedService) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.seedService = seedService;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        SpringApplication.run(HibritAramaDemo.class, args);
    }

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return seedService.seedLesson(LESSON_CODE);
    }

    // Birinci dedektif: Sadece anlam benzerligi ile arama
    @GetMapping("/vector")
    public Map<String, Object> vector(@RequestParam(defaultValue = "TokenTextSplitter") String q) {

        List<Document> vectorResults = vectorSearch(q, DEFAULT_TOP_K); // Semantic arama

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("resultCount", vectorResults.size());
        response.put("results", mapVectorDocuments(vectorResults));
        return response;
    }

    // Ikinci dedektif: Sadece tam kelime eslesmesi ile arama
    @GetMapping("/keyword")
    public Map<String, Object> keyword(@RequestParam(defaultValue = "TokenTextSplitter") String q) {

        List<Map<String, Object>> keywordResults = keywordSearch(q, DEFAULT_TOP_K); // SQL full-text

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("resultCount", keywordResults.size());
        response.put("results", keywordResults);
        return response;
    }


    // RRF ile iki dedektifin bulgularini birlestirecegiz.
    @GetMapping("/hybrid")
    public Map<String, Object> hybrid(@RequestParam(defaultValue = "TokenTextSplitter") String q) {

        List<Document> vectorResults = vectorSearch(q, DEFAULT_TOP_K);            // Semantic liste — 1. dedektif
        List<Map<String, Object>> keywordResults = keywordSearch(q, DEFAULT_TOP_K); // Keyword liste — 2. dedektif
        List<Map<String, Object>> fused = reciprocalRankFusion(vectorResults, keywordResults); // RRF birlestirme

        LinkedHashMap<String, Object> response = new LinkedHashMap<>(); // LinkedHashMap: sira koruma
        response.put("query", q);                                       // Kullanilan sorgu
        response.put("vectorCount", vectorResults.size());              // Kac belge semantik aramayla bulundu
        response.put("keywordCount", keywordResults.size());            // Kac belge keyword aramayla bulundu
        response.put("resultCount", fused.size());                      // Birlestirme sonrasi toplam benzersiz belge
        response.put("results", fused);                                 // vectorRank, keywordRank, rrfScore iceren liste
        return response;
    }


    private List<Document> vectorSearch(String q, int topK) {
        SearchRequest request = SearchRequest.builder()
            .query(q)
            .topK(topK)
            .similarityThresholdAll()
            .filterExpression(new FilterExpressionBuilder().eq("lesson", LESSON_CODE).build())
            .build();
        return vectorStore.similaritySearch(request);
    }


    private List<Map<String, Object>> keywordSearch(String q, int limit) {

        // to_tsvector: Metni aranabilir token listesine donusturur
        // plainto_tsquery: Kullanici metnini arama ifadesine donusturur
        // ts_rank: Kac kez gectiine gore 0-1 arasi skor verir
        // 'simple': Dil bagimsiz tokenizer — Turkce/Ingilizce icin uygundur

        String sql = String.format(
            "SELECT id, content, metadata, "
                + "ts_rank(to_tsvector('simple', content), plainto_tsquery('simple', ?)) AS keyword_score "
                + "FROM %s "
                + "WHERE to_tsvector('simple', content) @@ plainto_tsquery('simple', ?) "
                + "  AND metadata->>'lesson' = ? "
                + "ORDER BY keyword_score DESC LIMIT ?",
            VECTOR_TABLE_NAME);

        // q parametresi iki kez geciyor: biri ts_rank icin, biri WHERE kosulu icin
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, q, q, LESSON_CODE, limit);

        ArrayList<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            LinkedHashMap<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("id", row.get("id"));
            mapped.put("text", row.get("content"));
            mapped.put("keywordScore", row.get("keyword_score"));
            mapped.put("metadata", row.get("metadata"));
            result.add(mapped);
        }
        return result;
    }

    // RRF: Iki listenin sira bilgisini birlestirir — skoru degil, sirasini kullanir
    private List<Map<String, Object>> reciprocalRankFusion(
        List<Document> vectorResults,
        List<Map<String, Object>> keywordResults) {

        LinkedHashMap<String, FusionRow> fusionMap = new LinkedHashMap<>();
        // Embedding listesini isle: Her belgeye siraya gore RRF puani ver
        for (int i = 0; i < vectorResults.size(); i++) {
            Document doc = vectorResults.get(i);          // i. siraday belge
            int rank = i + 1;                             // Sira 1'den baslar (0-indexed degil)
            double rrfScore = 1.0 / (RRF_K + rank);      // RRF formulu: 1/(60+rank) — sira artinca puan duser
            // computeIfAbsent: Bu ID icin henuz FusionRow yoksa yeni olustur
            FusionRow row = fusionMap.computeIfAbsent(
                doc.getId(),
                id -> new FusionRow(id, doc.getText(), doc.getMetadata())); // id = String belge UUID'si
            row.vectorRank = rank;                // Embedding listesindeki sirasi
            row.vectorScore = doc.getScore();     // Embedding benzerlik skoru (0-1)
            row.rrfScore += rrfScore;             // Toplam RRF puanina ekle — keyword puani da sonra eklenecek
        }
        // Keyword listesini isle: Ayni belge varsa rrfScore'u topla, yoksa yeni ekle
        for (int i = 0; i < keywordResults.size(); i++) {
            Map<String, Object> keyRow = keywordResults.get(i); // i. siraday keyword sonucu
            String id = String.valueOf(keyRow.get("id"));        // UUID'yi String'e cevir
            int rank = i + 1;                                    // Sira 1'den baslar
            double rrfScore = 1.0 / (RRF_K + rank);             // Ayni formul: 1/(60+rank)
            // Embedding listesinde de varsa FusionRow zaten mevcut — puani topluyoruz
            FusionRow row = fusionMap.computeIfAbsent(
                id,
                key -> new FusionRow(key, String.valueOf(keyRow.get("text")), keyRow.get("metadata")));
            row.keywordRank = rank;                    // Keyword listesindeki sirasi
            row.keywordScore = keyRow.get("keywordScore"); // PostgreSQL ts_rank skoru
            row.rrfScore += rrfScore;                  // Embedding RRF + keyword RRF = toplam puan
        }

        // Birlestirilen listeyi rrfScore'a gore azalan sirada sirala ve Map'e donustur
        return fusionMap.values().stream()
            .sorted(Comparator.comparingDouble((FusionRow r) -> r.rrfScore).reversed()) // En yuksek RRF once
            .map(FusionRow::toMap)  // Her FusionRow'u JSON uyumlu Map'e donustur
            .toList();
    }


    private List<Map<String, Object>> mapVectorDocuments(List<Document> documents) {
        return documents.stream()
            .map(doc -> Map.of(
                "id", doc.getId(),        // Belge UUID'si
                "score", doc.getScore(),     // Cosine benzerlik skoru (0-1)
                "text", doc.getText(),      // Ham metin icerigi
                "metadata", doc.getMetadata()  // lesson, source gibi etiketler
            ))
            .toList();
    }


    private static final class FusionRow {

        private final String id;          // Belge benzersiz kimligi — fusionMap anahtari ile ayni
        private final String text;        // Belge metni — hem vector hem keyword listesinden geliyor
        private final Object metadata;    // JSON metadata nesnesi — lesson, source alanlari

        private Integer vectorRank;   // Embedding listesindeki sira (1-based); null = embedding'de yok
        private Object vectorScore;   // Embedding benzerlik skoru; null = embedding'de yok
        private Integer keywordRank;  // Keyword listesindeki sira (1-based); null = keyword'de yok
        private Object keywordScore;  // PostgreSQL ts_rank skoru; null = keyword'de yok
        private double rrfScore;      // 1/(60+vectorRank) + 1/(60+keywordRank) toplami — siralamayi belirler

        private FusionRow(String id, String text, Object metadata) {
            this.id = id;
            this.text = text;
            this.metadata = metadata;
        }

        private Map<String, Object> toMap() {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>(); // LinkedHashMap: alan sira koruma
            row.put("id", id);                   // Belge UUID'si
            row.put("text", text);               // Belge metni
            row.put("metadata", metadata);       // lesson, source gibi metadata
            row.put("vectorRank", vectorRank);   // Embedding listesindeki sira — null ise embedding'de yoktu
            row.put("vectorScore", vectorScore); // Embedding benzerlik skoru
            row.put("keywordRank", keywordRank); // Keyword listesindeki sira — null ise keyword'de yoktu
            row.put("keywordScore", keywordScore); // ts_rank skoru
            row.put("rrfScore", rrfScore);       // Nihai RRF skoru — bu alana gore sirali geliyor
            return row;
        }
    }
}