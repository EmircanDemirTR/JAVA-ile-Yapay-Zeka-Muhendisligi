// Swagger UI  : http://localhost:8080/swagger-ui.html
// Test (GET)  : http://localhost:8080/api/b63b/calculator?operation=toplama&left=12&right=8
// Test (GET)  : http://localhost:8080/api/b63b/expense-summary?base=TRY
// Test (GET)  : http://localhost:8080/api/b63b/assistant?message=Harcamalari+TRY+bazinda+ozetle

package com.javaai.bolum06;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b63b")
public class HarcamaOzetAsistanDemo {

    private static final Logger log = LoggerFactory.getLogger(HarcamaOzetAsistanDemo.class);
    private static final String FRANKFURTER_BASE_URL = "https://api.frankfurter.dev/v1/latest";
    private static final int CURRENCY_CODE_LENGTH = 3;

    private final ChatClient chatClient;
    private final RaporlamaToolService raporlamaToolService;
    private final CalculatorToolService calculatorToolService;
    
    public HarcamaOzetAsistanDemo(ChatClient.Builder builder, JdbcTemplate jdbcTemplate) {
        this.raporlamaToolService = new RaporlamaToolService(jdbcTemplate);  // JdbcTemplate ile DB'ye bagli tool
        this.calculatorToolService = new CalculatorToolService();  // Bagimsizsiz matematiksel hesaplama servisi
        this.raporlamaToolService.initializeSchema();  // b63_expense tablosu yoksa olustur — fail-safe strateji
        this.chatClient = builder
            .defaultSystem("Sen finans operasyon asistanisin. Gerektiginde tool kullan, sonucu net ve kisa acikla.")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(HarcamaOzetAsistanDemo.class, args);
    }


    @GetMapping("/calculator")
    public Map<String, Object> calculator(
        @RequestParam(defaultValue = "toplama") String operation,  // Gönderilmezse "toplama" kullan
        @RequestParam(defaultValue = "10") double left,  // Gönderilmezse 10
        @RequestParam(defaultValue = "5") double right) {  // Gönderilmezse 5
        return Map.of("lesson", "6.3B", "operation", "hesapla", "result",
            calculatorToolService.calculate(operation, left, right));  // Tool doğrudan çağrı
    }

    @GetMapping("/expense-summary")
    public Map<String, Object> expenseSummary(@RequestParam(defaultValue = "TRY") String base) {  // Gönderilmezse TRY varsayılan
        Map<String, Object> summary = raporlamaToolService.summarizeExpenses(base);  // Tool doğrudan çağrı — ham özet
        String modelAnswer = chatClient
            .prompt()  // Yeni bir prompt zinciri başlat
            .tools(raporlamaToolService)  // Raporlama tool'unu bu istekte aktif et
            .user(String.format("%s bazinda harcama ozetini cikar ve 2 cumlede yorumla. Gerekliyse expense_summary tool'unu kullan.",
                base.trim()))  // Kullanıcı görevi: özetleyip yorumla
            .call()  // Modeli senkron çağır
            .content();  // ChatResponse'dan sadece metin içeriğini al
        return Map.of("lesson", "6.3B", "summary", summary, "modelAnswer", modelAnswer);  // Hem ham veri hem model yorumu döndür
    }

    @GetMapping("/assistant")
    public Map<String, Object> assistant(
        @RequestParam(defaultValue = "Harcamalari TRY bazinda ozetle ve 144 bolu 12 hesapla") String message) {  // Varsayılan: iki görev birden
        String answer = chatClient
            .prompt()  // Yeni bir prompt zinciri başlat
            .tools(raporlamaToolService, calculatorToolService)  // İKİ TOOL BİRDEN AKTIF — model ikisi arasında seçim yapacak
            .user(String.format("Asagidaki istegi yerine getirirken gerekiyorsa uygun tool'lari kullan. Mesaj: %s",
                message.trim()))  // Görev: kullanıcı mesajını analiz et, gerekli tool'ları çağır
            .call()  // Modeli senkron çağır — model mesajı parse edip uygun tool'ları sırayla çağıracak
            .content();  // Final yanıtı al — model tool sonuçlarını birleştirip doğal dilde yanıt verir
        log.info("B6.3B assistant cagrisi | message={}", message);  // Log: hangi mesaj geldi
        return Map.of("lesson", "6.3B", "message", message.trim(), "answer", answer);  // Kullanıcı mesajı + model yanıtı
    }


    static class RaporlamaToolService {

        private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);  // API çağrısı 10 saniye aşarsa timeout
        private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();  // Paylaşılan HTTP client
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();  // JSON parse/stringify için Jackson mapper
        private final JdbcTemplate jdbcTemplate;  // Spring JDBC template — SQL sorgularını basitleştirir

        RaporlamaToolService(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        void initializeSchema() {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS b63_expense (
                    id BIGSERIAL PRIMARY KEY,
                    amount NUMERIC(18,2) NOT NULL,
                    currency VARCHAR(3) NOT NULL,
                    note VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }

        @Tool(name = "expense_summary", description = "Harcamalari ozetler")
        public Map<String, Object> summarizeExpenses(@ToolParam(description = "Ozetin baz para birimi, ornek TRY") String base) {
            String baseCurrency = normalizeCurrency(base);  // Kullanıcı "try" veya "TRY" yazabilir — normalize et
            // SQL: GROUP BY currency ile her para birimindeki harcamaları grupla, SUM ile topla
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT currency, SUM(amount) AS total_amount, COUNT(*) AS item_count
                FROM b63_expense GROUP BY currency ORDER BY currency
                """);

            List<Map<String, Object>> items = new ArrayList<>();  // Her para birimi grubu için detay
            double totalInBase = 0.0;  // Tüm harcamaların baz para birimindeki toplamı

            // Her satırı işle: para birimi, tutar, adet bilgisi al ve baz para birimine çevir
            for (Map<String, Object> row : rows) {
                String currency = String.valueOf(row.get("currency"));  // Para birimi kodu: USD, EUR, TRY...
                double totalAmount = toDouble(row.get("total_amount"));  // SUM(amount) sonucu
                long count = toLong(row.get("item_count"));  // COUNT(*) sonucu

                // Kur dönüşümü: Aynı para birimiyse direk kullan, değilse Frankfurter API ile çevir
                double convertedTotal =
                    currency.equalsIgnoreCase(baseCurrency) ? totalAmount : toDouble(fxConvert(currency, baseCurrency, totalAmount).get("converted"));
                totalInBase += convertedTotal;  // Genel toplama ekle
                items.add(
                    Map.of("currency", currency, "itemCount", count, "totalAmount", round(totalAmount),
                        "totalInBase", round(convertedTotal), "base", baseCurrency));  // Detay listesine ekle
            }

            return Map.of("base", baseCurrency, "groupCount", items.size(), "totalInBase", round(totalInBase), "items", items);
        }

        // KUR DONUSUMU HELPER (6.3A kopyasi)
        private Map<String, Object> fxConvert(String from, String to, double amount) {
            String source = normalizeCurrency(from);  // Kaynak para birimi normalize
            String target = normalizeCurrency(to);  // Hedef para birimi normalize
            String url = FRANKFURTER_BASE_URL + "?base=" + encode(source) + "&symbols=" + encode(target);  // Query string oluştur
            try {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(HTTP_TIMEOUT).GET().build();  // HTTP GET request
                HttpResponse<String> res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());  // Senkron HTTP çağrısı
                if (res.statusCode() < 200 || res.statusCode() >= 300)  // 2xx dışı -> hata
                {
                    throw new IllegalArgumentException("FX API hatasi: HTTP " + res.statusCode());
                }
                double rate = extractRate(res.body(), target);  // JSON'dan kur oranını çıkar
                if (Double.isNaN(rate))  // Oran bulunamadıysa -> hata
                {
                    throw new IllegalArgumentException("Kur bulunamadi");
                }
                return Map.of("from", source, "to", target, "amount", round(amount), "converted", round(amount * rate), "rate",
                    round(rate));  // Sonuç map
            } catch (Exception ex) {
                throw new IllegalArgumentException("FX donusumu hatasi: " + ex.getMessage(), ex);  // Tüm hataları sarmalayıp fırlat
            }
        }

        private String normalizeCurrency(String c) {  // Para birimi kodunu normalize et: trim + uppercase + 3 harf kontrolü
            if (c == null || c.isBlank()) {
                throw new IllegalArgumentException("currency bos olamaz");
            }
            String n = c.trim().toUpperCase(Locale.ROOT);  // Locale.ROOT: platform bağımsız uppercase
            if (n.length() != CURRENCY_CODE_LENGTH) {
                throw new IllegalArgumentException("currency 3 harf olmali");
            }
            return n;
        }

        private String encode(String v) {
            return URLEncoder.encode(v, StandardCharsets.UTF_8);
        }  // URL query string güvenli hale getir

        private double round(double v) {
            return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
        }  // 4 ondalık basamağa yuvarla

        private double extractRate(String body, String target) {  // JSON body'den kur oranını çıkar
            if (body == null || body.isBlank()) {
                return Double.NaN;
            }
            try {
                JsonNode rates = OBJECT_MAPPER.readTree(body).path("rates");  // "rates" objesine git
                return rates.has(target) ? rates.path(target).asDouble(Double.NaN) : Double.NaN;  // target anahtarı varsa değerini al
            } catch (Exception ex) {
                return Double.NaN;
            }  // Parse hatası -> NaN döndür
        }

        private double toDouble(Object v) {
            return v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v));
        }  // Object -> double cast

        private long toLong(Object v) {
            return v instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(v));
        }  // Object -> long cast
    }


    static class CalculatorToolService {

        // @Tool annotation: Bu metodu modele "hesapla" tool'u olarak kaydet.
        @Tool(name = "hesapla", description = "Basit aritmetik islem yapar")
        public Map<String, Object> calculate(
            @ToolParam(description = "Islem tipi: toplama|cikarma|carpma|bolme") String operation,  // İşlem türü — model bu 4 seçenekten birini seçer
            @ToolParam(description = "Sol sayi") double left,  // Sol operand
            @ToolParam(description = "Sag sayi") double right) {  // Sağ operand
            String normalized = operation == null ? "" : operation.trim().toLowerCase(Locale.ROOT);  // Normalize: "TOPLAMA" -> "toplama"
            // Switch expression pattern: Java 14+ modern syntax
            // Her case bir işlem tipi — yield ile return ediyoruz
            double result = switch (normalized) {
                case "toplama" -> left + right;  // Toplama işlemi
                case "cikarma" -> left - right;  // Çıkarma işlemi
                case "carpma" -> left * right;  // Çarpma işlemi
                case "bolme" -> {  // Bölme işlemi — özel kontrol gerekir
                    if (right == 0.0) {
                        throw new IllegalArgumentException("Sifira bolme yapilamaz");  // Validation: sıfıra bölme hatası
                    }
                    yield left / right;  // yield: switch expression içinde return gibi
                }
                default -> throw new IllegalArgumentException("Gecersiz operation: " + operation);  // Geçersiz işlem tipi
            };
            // Sonucu 4 ondalık basamağa yuvarlayıp Map içinde döndür
            return Map.of("operation", normalized, "left", left, "right", right, "result",
                BigDecimal.valueOf(result).setScale(4, RoundingMode.HALF_UP).doubleValue());
        }
    }
}