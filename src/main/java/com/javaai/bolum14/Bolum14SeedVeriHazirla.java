package com.javaai.bolum14;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class Bolum14SeedVeriHazirla {

    private static final String VECTOR_TABLE_NAME = "vector_store_b9";
    private static final List<String> ALL_LESSONS = List.of("b141", "b142", "b143", "b144", "b145");
    private static final Map<String, String> LESSON_LABELS = createLessonLabels();

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public Bolum14SeedVeriHazirla(ObjectProvider<VectorStore> vectorStoreProvider,
        ObjectProvider<JdbcTemplate> jdbcTemplateProvider) { // Test/ollama profilinde null bean riskini bu yolla guvenli yonetiyoruz.
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    private static String uuid(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static Map<String, String> createLessonLabels() {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        labels.put("b141", "b14.1 — Prompt Injection ve PII Onleme");
        labels.put("b142", "b14.2 — API Guvenligi ve Rate Limiting");
        labels.put("b143", "b14.3 — Caching ve Maliyet Optimizasyonu");
        labels.put("b144", "b14.4 — Asenkron Cagrilar ve Circuit Breaker");
        labels.put("b145", "b14.5 — Observability: Metrics, Tracing, Grafana");
        return labels;
    }

    public Map<String, Object> seedAll() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (String lesson : ALL_LESSONS) {
            result.put(lesson, seedLesson(lesson));
        }
        return result;
    }

    public Map<String, Object> seedLesson(String lessonCode) {
        List<Document> documents = getDocuments(lessonCode); // Ders koduna gore hazirlanmis Document listesini al.

        if (documents.isEmpty()) {
            throw new IllegalArgumentException("Bilinmeyen lesson: " + lessonCode);
        }

        this.jdbcTemplate.update( // Ayni dersin eski verilerini temizle — idempotent seed icin zorunlu.
            String.format("DELETE FROM %s WHERE metadata->>'lesson' = ?",
                VECTOR_TABLE_NAME), // Yalnizca bu derse ait satirlari sil, digerleri dokunulmadan kalsin.
            lessonCode // Hangi ders kodunun silineceği — parametre olarak gecip SQL injection'u onluyoruz.
        );
        this.vectorStore.add(documents);
        return Map.of(
            "lesson", LESSON_LABELS.getOrDefault(lessonCode, lessonCode),
            "indexedCount", documents.size(),
            "lessonFilter", lessonCode,
            "notes", "Seed tamamlandi."
        );
    }

    private List<Document> getDocuments(String lessonCode) {
        return switch (lessonCode) {
            case "b141" -> b141Docs();
            case "b142" -> b142Docs();
            case "b143" -> b143Docs();
            case "b144" -> b144Docs();
            case "b145" -> b145Docs();
            default -> List.of();
        };
    }

    private List<Document> b141Docs() {
        return List.of(
            new Document(uuid("b141-1"),
                "Prompt injection saldirisi, kullanicinin LLM'e verilen sistem talimatlarini manipule etmeye calismasi durumudur. Ornegin 'onceki kurallari unut' gibi ifadeler.",
                Map.of("lesson", "b141", "topic", "prompt-injection")),
            new Document(uuid("b141-2"),
                "PII (Personally Identifiable Information) maskeleme, TC kimlik no, telefon, e-posta gibi hassas verilerin LLM'e gonderilmeden once gizlenmesi islemidir.",
                Map.of("lesson", "b141", "topic", "pii-masking")),
            new Document(uuid("b141-3"),
                "Uc katmanli savunma: (1) Input sanitizasyonu - regex ile PII temizleme, (2) Advisor zinciri - injection pattern tespiti, (3) SafeGuardAdvisor - yasakli konu filtreleme.",
                Map.of("lesson", "b141", "topic", "defense-layers")),
            new Document(uuid("b141-4"),
                "Spring AI SafeGuardAdvisor, belirli anahtar kelimeleri tespit ederek LLM cagrisini engelleyen hazir bir guvenlik advisor'udur. Yasakli kelime listesi ve ret mesaji ile yapilandirilir.",
                Map.of("lesson", "b141", "topic", "safeguard-advisor")),
            new Document(uuid("b141-5"),
                "OWASP Top 10 for LLM Applications: Prompt Injection (LLM01) en yuksek oncelikli risktir. Dogrudan ve dolayli injection olmak uzere iki turu vardir.",
                Map.of("lesson", "b141", "topic", "owasp-llm"))
        );
    }

    private List<Document> b142Docs() {
        return List.of(
            new Document(uuid("b142-1"),
                "Bearer Token kimlik dogrulama, API isteklerinde Authorization header'i ile token gonderilmesini gerektirir. Format: 'Bearer <token>'.",
                Map.of("lesson", "b142", "topic", "bearer-token")),
            new Document(uuid("b142-2"),
                "Rate limiting, belirli bir zaman diliminde yapilabilecek istek sayisini sinirlar. Token bucket algoritmasi en yaygin yontemdir.",
                Map.of("lesson", "b142", "topic", "rate-limiting")),
            new Document(uuid("b142-3"),
                "OncePerRequestFilter, Spring Web'in her HTTP isteginde bir kez calisan filtre mekanizmasidir. spring-boot-starter-security olmadan da kullanilabilir.",
                Map.of("lesson", "b142", "topic", "filter")),
            new Document(uuid("b142-4"),
                "Bucket4j kutuphanesi, Java icin token bucket algoritmasi implementasyonudur. Kapasite ve yenileme hizi ayarlanabilir.",
                Map.of("lesson", "b142", "topic", "bucket4j"))
        );
    }

    private List<Document> b143Docs() {
        return List.of(
            new Document(uuid("b143-1"),
                "Spring Cache, @Cacheable annotation'i ile metot sonuclarini onbellege alir. Ayni parametrelerle tekrar cagrildiginda cache'ten doner.",
                Map.of("lesson", "b143", "topic", "spring-cache")),
            new Document(uuid("b143-2"),
                "Caffeine, Java icin yuksek performansli in-memory cache kutuphanesidir. TTL, max-size ve istatistik destegi sunar.",
                Map.of("lesson", "b143", "topic", "caffeine")),
            new Document(uuid("b143-3"),
                "LLM maliyet optimizasyonu: GPT-4o icin prompt $2.50/1M token, completion $10.00/1M token. Cache ile tekrar eden sorgularda %80'e kadar tasarruf mumkun.",
                Map.of("lesson", "b143", "topic", "cost-optimization")),
            new Document(uuid("b143-4"),
                "Cache invalidation stratejileri: TTL (zaman bazli), LRU (en az kullanilan), manual eviction (@CacheEvict). Bilgi degistiginde eski cevaplari temizlemek kritiktir.",
                Map.of("lesson", "b143", "topic", "cache-invalidation"))
        );
    }

    private List<Document> b144Docs() {
        return List.of(
            new Document(uuid("b144-1"),
                "Circuit Breaker, uc durumlu bir koruma mekanizmasidir: CLOSED (normal), OPEN (reddediyor), HALF_OPEN (deneme). Sigorta kutusu analojisi.",
                Map.of("lesson", "b144", "topic", "circuit-breaker")),
            new Document(uuid("b144-2"),
                "Resilience4j, Java icin hafif bir dayaniklilik kutuphanesidir. CircuitBreaker, Retry, Bulkhead, RateLimiter ve TimeLimiter modullerini icerir.",
                Map.of("lesson", "b144", "topic", "resilience4j")),
            new Document(uuid("b144-3"),
                "CompletableFuture ile asenkron LLM cagrilari, ana thread'i bloklamadan paralel istek gondermeyi saglar. supplyAsync() ve thenApply() zinciri.",
                Map.of("lesson", "b144", "topic", "async")),
            new Document(uuid("b144-4"),
                "Fallback stratejisi, birincil servis kullanilamadiginda alternatif cevap donme mekanizmasidir. Graceful degradation saglar.",
                Map.of("lesson", "b144", "topic", "fallback"))
        );
    }

    private List<Document> b145Docs() {
        return List.of(
            new Document(uuid("b145-1"),
                "Observability uc sutunu: Metrics (sayisal olcumler), Logs (olay kayitlari), Traces (istek izleme). Production'da uc sutun birlikte kullanilir.",
                Map.of("lesson", "b145", "topic", "observability")),
            new Document(uuid("b145-2"),
                "Micrometer, Spring'in metrik toplama API'sidir. Timer, Counter, Gauge gibi metrik tipleri sunar. Prometheus, Grafana, Datadog gibi sistemlere export edebilir.",
                Map.of("lesson", "b145", "topic", "micrometer")),
            new Document(uuid("b145-3"),
                "Prometheus, zaman serisi metrik veritabanidir. Pull-based model: belirli araliklarla uygulamadan metrik toplar. PromQL sorgu dili ile analiz.",
                Map.of("lesson", "b145", "topic", "prometheus")),
            new Document(uuid("b145-4"),
                "Grafana, metrik gorsellestirme ve dashboard aracidir. Prometheus'tan veri cekerek AI uygulamasinin latency, token kullanimi ve hata oranini izler.",
                Map.of("lesson", "b145", "topic", "grafana")),
            new Document(uuid("b145-5"),
                "Distributed tracing, bir istegin tum servisler arasindaki yolculugunu izler. Trace ID ile loglar ve metrikler iliskilendirilir.",
                Map.of("lesson", "b145", "topic", "tracing"))
        );
    }

}
