package com.javaai.bolum06;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b64")
public class ToolDonusVeHataYonetimiDemo {

    private static final Logger log = LoggerFactory.getLogger(ToolDonusVeHataYonetimiDemo.class);
    private final ChatClient chatClient;
    private final ReturnTypeToolService returnTypeToolService;

    public ToolDonusVeHataYonetimiDemo(ChatClient.Builder builder) {
        this.returnTypeToolService = new ReturnTypeToolService();
        this.chatClient = builder
            .defaultSystem("Sen teknik asistan rolündesin. Gerektiginde tool kullan. Cevabi net, kisa ve Turkce olarak ver")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(ToolDonusVeHataYonetimiDemo.class, args);
    }

    @GetMapping("/string-tool")
    public Map<String, Object> stringTool(@RequestParam(defaultValue = "algoritma") String word) {
        String modelAnswer = chatClient.prompt()
            .tools(returnTypeToolService)
            .user(String.format("\"%s\" kelimesini aciklamak icin gerekliyse kelime_anlami tool'unu kullan. Sonucu tek paragrafta yaz.", word.trim()))
            .call()
            .content();
        return Map.of("lesson", "6.4", "type", "string", "word", word.trim(),
            "directResult", returnTypeToolService.wordMeaning(word.trim()),
            "modelAnswer", modelAnswer);
    }

    @GetMapping("/record-tool")
    public Map<String, Object> recordTool(@RequestParam(defaultValue = "Istanbul") String city) {
        String modelAnswer = chatClient.prompt()
            .tools(returnTypeToolService)
            .user(String.format("%s sehri icin sehir_bilgisi tool'unu kullan ve sonucu kisaca yorumla.", city.trim()))
            .call()
            .content();
        return Map.of("lesson", "6.4", "type", "record", "city", city.trim(),
            "directResult", returnTypeToolService.cityInfo(city.trim()),
            "modelAnswer", modelAnswer);
    }

    @GetMapping("/list-tool")
    public Map<String, Object> listTool(@RequestParam(defaultValue = "spring") String topic) {
        String modelAnswer = chatClient.prompt()
            .tools(returnTypeToolService)
            .user(String.format("%s konusu icin kutuphane_listesi tool'unu kullan. Sonucu maddeler halinde ozetle.", topic.trim()))
            .call()
            .content();
        return Map.of("lesson", "6.4", "type", "list", "topic", topic.trim(),
            "directResult", returnTypeToolService.topLibraries(topic.trim()),
            "modelAnswer", modelAnswer);
    }

    @GetMapping("/compare")
    public Map<String, Object> compare(@RequestParam(defaultValue = "algoritma") String word) {
        String direct = returnTypeToolService.wordMeaning(word.trim());
        String modelAnswer = chatClient.prompt()
            .tools(returnTypeToolService)
            .user(String.format("\"%s\" kelimesini acikla. Gerekliyse kelime_anlami tool'unu kullan.", word.trim()))
            .call()
            .content();
        return Map.of("lesson", "6.4", "input", word.trim(),
            "directTool", direct,
            "modelWithTool", modelAnswer);
    }

    @GetMapping("/error-tool")
    public Map<String, Object> errorTool(@RequestParam(defaultValue = "maintenance") String mode,
        @RequestParam(defaultValue = "1200") long timeoutMs) {
        String directResult = returnTypeToolService.systemHealthWithTimeoutGuard(mode.trim(), Duration.ofMillis(timeoutMs));

        if (directResult.startsWith("ERROR")) {
            return Map.of("lesson", "6.4", "mode", mode.trim(), "timeoutMs", timeoutMs,
                "status", "error",
                "errorCode", "TOOL_EXECUTION_FAILED",
                "errorMessage", directResult);
        }

        String modelAnswer;
        try {
            modelAnswer = chatClient.prompt()
                .tools(returnTypeToolService)
                .user(String.format("Sistem durumunu kontrol et. Mode: %s. Gerekirse sistem_kontrol tool'unu kullan.", mode.trim()))
                .call()
                .content();
        } catch (Exception modelEx) {
            log.warn("B6.4 model yaniti alinamadi: {}", modelEx.getMessage());
            modelAnswer = "Model cevabi alinamadi, direct tool sonucu kullanildi.";
        }
        return Map.of("lesson", "6.4", "mode", mode.trim(), "timeoutMs", timeoutMs,
            "status", "success",
            "directResult", directResult,
            "modelAnswer", modelAnswer);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleError(Exception ex) {
        String message = ex.getMessage() == null ? "Bilinmeyen hata" : ex.getMessage();
        log.error("B6.4 global hata: {}", message, ex);
        return Map.of("lesson", "6.4", "error", message, "errorType", ex.getClass().getSimpleName(),
            "timestamp", LocalDateTime.now().toString());
    }

    public record CityInfo(String city, int population, String region) {

    }

    public static class ReturnTypeToolService {

        @Tool(name = "kelime_anlami", description = "Verilen teknik kelimeyi kisaca aciklar")
        public String wordMeaning(@ToolParam(description = "Aciklanacak kelime") String word) {
            return word.trim() + ": teknik baglamda temel aciklama metni";
        }

        @Tool(name = "sehir_bilgisi", description = "Sehir icin nufus ve bolge bilgisini dondurur")
        public CityInfo cityInfo(@ToolParam(description = "Sehir adi") String city) {
            return switch (city.trim().toLowerCase()) {
                case "istanbul" -> new CityInfo("Istanbul", 16000000, "Marmara");
                case "ankara" -> new CityInfo("Ankara", 5800000, "Ic Anadolu");
                default -> new CityInfo(city.trim(), 1000000, "Genel");
            };
        }

        @Tool(name = "kutuphane_listesi", description = "Konuya gore populer kutuphane listesi dondurur")
        public List<String> topLibraries(@ToolParam(description = "Konu adi") String topic) {
            String normalized = topic.trim().toLowerCase();
            if (normalized.contains("spring")) {
                return List.of("spring-boot", "spring-ai", "spring-data");
            }
            if (normalized.contains("java")) {
                return List.of("jackson", "mapstruct", "lombok");
            }
            return List.of("tool-a", "tool-b", "tool-c");
        }

        @Tool(name = "sistem_kontrol", description = "Sistem saglik durumunu kontrol eder")
        public String systemHealth(@ToolParam(description = "Mod: ok veya maintenance") String mode) {
            return switch (mode.trim().toLowerCase()) {
                case "maintenance" -> "ERROR: Sistem bakim modunda, tool gecici devre disi";
                case "timeout" -> "ERROR_TIMEOUT: Sistem beklenen surede yanit vermedi";
                default -> "Sistem saglikli";
            };
        }

        public String systemHealthWithTimeoutGuard(String mode, Duration timeout) {
            try {
                return CompletableFuture.supplyAsync(() -> {
                    String normalized = mode == null ? "" : mode.trim().toLowerCase();
                    if ("timeout".equals(normalized)) {
                        try {
                            Thread.sleep(timeout.toMillis() + 300L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return "ERROR: Bekleme kesintiye ugradi";
                        }
                    }
                    return systemHealth(mode);
                }).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join();
            } catch (Exception e) {
                if (e.getCause() instanceof TimeoutException) {
                    return "ERROR_TIMEOUT: Tool zaman asimina ugradi";
                }
                return "ERROR: Tool calisirken beklenmeyen bir sorun olustu";
            }
        }
    }
}