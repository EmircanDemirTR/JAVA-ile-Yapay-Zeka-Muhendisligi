package com.javaai.bolum06;

import static org.springframework.ai.model.ModelOptionsUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b63")

public class KurVeHarcamaDemo {

    private static final Logger log = LoggerFactory.getLogger(KurVeHarcamaDemo.class);

    private static final String FRANKFURTER_BASE_URL = "https://api.frankfurter.dev/v1/latest";
    private static final int CURRENCY_CODE_LENGTH = 3;

    private final ChatClient chatClient;
    private final FinanceToolService financeToolService; // @Tool metotlari tasiyan servis

    public KurVeHarcamaDemo(ChatClient.Builder builder, JdbcTemplate jdbcTemplate) {
        this.financeToolService = new FinanceToolService(jdbcTemplate);
        this.financeToolService.initializeSchema(); // expense tablosu oluşturma

        this.chatClient = builder
            .defaultSystem("Sen finans operasyon asistanisin. Gerektiginde tool kullan, sonucu net ve kisa acikla. Turkce olarak cevap ver")
            .build();
    }

    private static String encode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static String normalizeCurrency(String c) {
        if (c == null || c.isBlank()) {
            throw new IllegalArgumentException("currency bos olamaz");
        }
        String n = c.trim().toUpperCase(Locale.ROOT);  // Buyuk harf — "usd" → "USD"
        if (n.length() != CURRENCY_CODE_LENGTH) {
            throw new IllegalArgumentException("currency 3 harf olmali");  // ISO 4217
        }
        return n;
    }

    public static void main(String[] args) {
        SpringApplication.run(KurVeHarcamaDemo.class, args);
    }

    private static double extractRate(String body, String target) {
        if (body == null || body.isBlank()) {
            return Double.NaN;
        }
        try {
            JsonNode rates = OBJECT_MAPPER.readTree(body).path("rates");  // JSON parse — {"rates":{"TRY":34.52}}
            return rates.has(target) ? rates.path(target).asDouble(Double.NaN) : Double.NaN;  // Kur var mi?
        } catch (Exception ex) {
            log.warn("FX rate parse hatasi: {}", ex.getMessage());  // Konsola uyari
            return Double.NaN;
        }
    }

    // ENDPOINTLER
    // 1- DIS API KUR CEKME
    @GetMapping("/fx-convert")
    public Map<String, Object> fxConvert(
        @RequestParam(defaultValue = "100") double amount,  // Parametre yoksa 100
        @RequestParam(defaultValue = "USD") String from,  // Parametre yoksa USD
        @RequestParam(defaultValue = "TRY") String to,  // Parametre yoksa TRY
        @RequestParam(defaultValue = "true") boolean includeModel) // Parametre yoksa model modu aktif
    {
        Map<String, Object> directResult = financeToolService.fxConvert(from, to, amount);

        String modelAnswer = includeModel ?
            chatClient.prompt()
                .tools(financeToolService)
                .user(String.format("%s para biriminden %s para birimine %.2f tutari cevir."
                    + "Gerekliyse fx_convert tool'unu kullan ve kisaca acikla", from.trim(), to.trim(), amount))
                .call()
                .content()
            : "Model carisi includeModel=false oldugu icin pas gecildi";

        return Map.of(
            "includeModel", includeModel,
            "direct", directResult,
            "modelAnswer", modelAnswer
        );
    }

    // 2- DB'YE HARCAMA KAYDI
    @PostMapping("/expense")
    public Map<String, Object> saveExpense(@RequestBody ExpenseRequest request) {
        Map<String, Object> result = financeToolService.saveExpense(
            request.amount(), request.currency(), request.note()
        );

        return Map.of(
            "operation", "expense_save",
            "result", result
        );
    }

    public record ExpenseRequest(double amount, String currency, String note) {

        public ExpenseRequest {
            if (amount <= 0.0) {
                throw new IllegalArgumentException("amount pozitif olmali");  // Sifir/negatif reddet
            }
            if (currency == null || currency.isBlank()) {
                throw new IllegalArgumentException("currency bos olamaz");
            }
            if (currency.trim().length() != CURRENCY_CODE_LENGTH) {
                throw new IllegalArgumentException("currency 3 harf olmali");  // ISO 4217
            }
            if (note == null || note.isBlank()) {
                throw new IllegalArgumentException("note bos olamaz");  // Aciklama zorunlu
            }
        }
    }

    static class FinanceToolService {

        private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10); // Dıs API max bekleme
        private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build(); // Thread-safe HTTP client
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(); // JSON parse icin
        private final JdbcTemplate jdbcTemplate; // SQL çalıştırmak için

        FinanceToolService(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        // Schema Oluşturma
        void initializeSchema() {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS b63_expense(
                    id BIGSERIAL PRIMARY KEY, -- otomatik artan benzersiz ID
                    amount NUMERIC(18,2) NOT NULL, -- harcama tutari (max 18 hane, 2 ondalık)
                    currency VARCHAR(3) NOT NULL, -- para birimi
                    note VARCHAR(255) NOT NULL, -- harcama aciklamasi
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP -- kayit zamani
                )
                """);
        }


        // Tool 1 - Kur Cevirisi (Okuma)
        @Tool(name = "fx_convert", description = "Para birimi donusumu yapar")
        public Map<String, Object> fxConvert(
            @ToolParam(description = "Kaynak para birimi, ornek USD") String from,
            @ToolParam(description = "Hedef para birimi, ornek TRY") String to,
            @ToolParam(description = "Donusturulecek tutar") double amount) {
            if (amount <= 0.0) {
                throw new IllegalArgumentException("amount pozitif olmali");  // Pozitiflik kontrolu
            }
            String source = normalizeCurrency(from);  // "usd" → "USD", 3 harf kontrolu
            String target = normalizeCurrency(to);
            String requestUrl = FRANKFURTER_BASE_URL + "?base=" + encode(source) + "&symbols=" + encode(target);  // URL olustur

            try {
                HttpRequest request = HttpRequest.newBuilder() // HTTP Get Builder
                    .uri(URI.create(requestUrl)) // Frankfurter endpoint
                    .timeout(HTTP_TIMEOUT)
                    .GET().build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()); // Senkron istek -- Json Text

                if (response.statusCode() < 200 || response.statusCode() > 299) {
                    throw new IllegalArgumentException("FX API Hatasi" + response.statusCode());
                }

                double rate = extractRate(response.body(), target); // JSON'dan kur çıkar

                double converted = amount * rate; // Cevirilen tutar hesaplanır

                return Map.of(
                    "from", source,
                    "to", target,
                    "amount", amount,
                    "converted", converted,
                    "rate", rate
                );
            } catch (Exception e) {
                throw new IllegalArgumentException("FX donusumu hatasi");
            }
        }

        // Tool 2 - Harcama Kaydi (Yazma)
        @Tool(name = "expense_save", description = "Harcama kaydi olusturur")
        public Map<String, Object> saveExpense(
            @ToolParam(description = "Harcama tutari") double amount,
            @ToolParam(description = "Para birimi") String currency,
            @ToolParam(description = "Harcama aciklamasi") String note
        ) {

            int affected = jdbcTemplate.update("""
                INSERT INTO b63_expense(amount, currency, note) VALUES (?,?,?)
                """, amount, currency, note.trim());

            return Map.of(
                "Saved", affected == 1,
                "amount", amount,
                "currency", currency,
                "note", note.trim()
            );
        }
    }
}
