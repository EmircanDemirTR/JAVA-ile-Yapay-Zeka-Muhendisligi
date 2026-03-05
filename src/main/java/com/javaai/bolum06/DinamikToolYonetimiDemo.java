package com.javaai.bolum06;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
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
@RequestMapping("/api/b65")

public class DinamikToolYonetimiDemo {

    private static final Logger log = LoggerFactory.getLogger(DinamikToolYonetimiDemo.class);
    private static final Set<String> VALID_DOMAINS = Set.of("finance", "weather", "general");

    private final ChatClient chatClient;
    private final FinanceTools financeTools;
    private final WeatherTools weatherTools;
    private final GeneralTools generalTools;

    private final ToolCallbackResolver toolCallbackResolver;

    public DinamikToolYonetimiDemo(ChatClient.Builder builder) {

        // Aşama 1
        this.financeTools = new FinanceTools();
        this.weatherTools = new WeatherTools();
        this.generalTools = new GeneralTools();

        // Aşama 2
        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
            .toolObjects(financeTools, weatherTools, generalTools)
            .build();

        // Aşama 3
        this.toolCallbackResolver = new StaticToolCallbackResolver(List.of(provider.getToolCallbacks())); // Tüm callback'leri resolver'a kaydet

        this.chatClient = builder
            .defaultSystem("Sen domain bazli arac secimi yapan bir asistansin. Sadece aktif tool kadrosunu kullan")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(DinamikToolYonetimiDemo.class, args);
    }

    // Endpoint 1 - Domain Bazlı Dynamic Routing
    @GetMapping("/route")
    public Map<String, Object> route(
        @RequestParam(defaultValue = "1200 TL icin KDV hesapla") String message,
        @RequestParam(defaultValue = "finance") String domain,
        @RequestParam(defaultValue = "acme") String tenant
    ) {
        String normalizedDomain = domain.trim().toLowerCase(Locale.ROOT);
        List<Object> activeTools = selectTools(normalizedDomain);

        List<String> activeToolNames = extractToolInfo(activeTools, false).stream().map(m -> m.get("name")).toList(); // Sadece isimleri al

        List<ToolCallback> resolvedCallbacks = activeToolNames.stream()
            .map(toolCallbackResolver::resolve)
            .filter(Objects::nonNull)
            .toList();

        String answer = chatClient.prompt()
            .toolCallbacks(resolvedCallbacks)
            .toolContext(Map.of("tenant", tenant.trim(), "domain", normalizedDomain))
            .user(message.trim())
            .call()
            .content();

        return Map.of(
            "domain", normalizedDomain,
            "tenant", tenant.trim(),
            "activeTools", activeToolNames,
            "message", message.trim(),
            "answer", answer);
    }

    // Endpoint 2 - Tool Chaining
    @GetMapping("/chain")
    public Map<String, Object> chain(
        @RequestParam(defaultValue = "1200") double amount,
        @RequestParam(defaultValue = "0.20") double vatRate) {
        Map<String, Object> vatResult = financeTools.calculateVat(amount, vatRate);
        String chainInput = String.format("Net: %.2f, KDV: %.2f, Brut: %.2f",  // Tool 1 ciktisini formatla
            (double) vatResult.get("amount"), (double) vatResult.get("vat"), (double) vatResult.get("gross"));
        String summary = generalTools.summarize(chainInput);  // Tool 2: Ozet olusturma — Tool 1 ciktisi Tool 2'ye girdi oldu

        return Map.of(
            "chain", List.of("kdv_hesapla", "kisa_ozet"),  // Zincir sirasi — debugging icin
            "vatResult", vatResult,  // Ilk tool sonucu
            "summary", summary);  // Ikinci tool sonucu
    }

    // Endpoint 3 - Active Tools Metadata
    @GetMapping("/active-tools")
    public Map<String, Object> activeTools(@RequestParam(defaultValue = "general") String domain) {
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        List<Map<String, String>> toolMeta = extractToolInfo(selectTools(normalized), true);

        return Map.of(
            "toolCount", toolMeta.size(),
            "tools", toolMeta
        );
    }

    private List<Object> selectTools(String normalizedDomain) {
        return switch (normalizedDomain) {  // Domain bazli tool secimi
            case "finance" -> List.of(financeTools);  // Finance domain: sadece KDV hesaplama
            case "weather" -> List.of(weatherTools);  // Weather domain: sadece hava durumu
            case "general" -> List.of(generalTools);  // General domain: sadece metin ozet
            default -> throw new IllegalArgumentException("Desteklenmeyen domain: " + normalizedDomain);  // Whitelist disi domain reddediliyor
        };
    }

    private List<Map<String, String>> extractToolInfo(List<Object> toolInstances, boolean withDescription) {
        List<Map<String, String>> result = new ArrayList<>();  // Sonuc listesi
        for (Object instance : toolInstances) {  // Her tool sinifi (financeTools, weatherTools vb.)
            for (Method method : instance.getClass().getDeclaredMethods()) {  // Sinifdaki tum metotlari tara
                Tool tool = method.getAnnotation(Tool.class);  // @Tool annotation'i var mi?
                if (tool == null) {
                    continue;  // @Tool yoksa atla
                }
                String name =
                    (tool.name() == null || tool.name().isBlank()) ? method.getName() : tool.name();  // Tool ismi al — bos ise metot ismi kullan
                if (!withDescription) {  // Sadece isim istendi
                    result.add(Map.of("name", name));  // Basit Map: {"name": "kdv_hesapla"}
                } else {  // Isim + aciklama istendi
                    ToolCallback cb = toolCallbackResolver.resolve(name);  // Resolver'dan callback al
                    String desc = cb != null ? cb.getToolDefinition().description() : "Aciklama bulunamadi";  // Callback'ten description cek
                    result.add(Map.of("name", name, "description", desc));  // {"name": "kdv_hesapla", "description": "..."}
                }
            }
        }
        return List.copyOf(result);  // Immutable liste doner
    }

    static class FinanceTools {

        @Tool(name = "kdv_hesapla", description = "Verilen tutara KDV eklenmis sonucu hesaplar")
        public Map<String, Object> calculateVat(
            @ToolParam(description = "Net tutar") double amount,
            @ToolParam(description = "KDV orani, ornek 0.20") double vatRate) {
            double vat = amount * vatRate;
            return Map.of(
                "amount", amount,
                "vatRate", vatRate,
                "vat", vat,
                "gross", amount + vat
            );
        }
    }

    static class WeatherTools {

        @Tool(name = "sehir_hava_ozet", description = "Sehir icin kisa hava ozeti dondurur")
        public String cityWeather(@ToolParam(description = "Sehir adi") String city) {
            return switch (city.trim().toLowerCase(Locale.ROOT)) {
                case "istanbul" -> "Istanbul: 18C, parcali bulutlu";
                case "ankara" -> "Ankara: 12C, acik";
                case "izmir" -> "Izmir: 22C, acik";
                default -> city.trim() + ": 16C, genel durum";
            };
        }
    }


    static class GeneralTools {

        @Tool(name = "kisa_ozet", description = "Metni 1 cumlelik ozetler")
        public String summarize(@ToolParam(description = "Ozetlenecek metin") String text) {
            String trimmed = text.trim();
            return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80) + "...";
        }
    }


}
