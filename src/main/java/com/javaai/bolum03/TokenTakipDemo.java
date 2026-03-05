// Swagger UI : http://localhost:8080/swagger-ui.html
// Test (POST) : http://localhost:8080/api/b35/chat/tracked — Body: {"message":"Java nedir?"}
// Test (POST) : http://localhost:8080/api/b35/chat/detailed — Body: {"message":"Spring AI nedir?"}


package com.javaai.bolum03;

import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b35")

public class TokenTakipDemo {

    private static final Logger log = LoggerFactory.getLogger(StreamingDemo.class);

    // Dizi formati: [input_price, output_price] — 1 milyon token basi USD
    private static final Map<String, double[]> MODEL_PRICES =
            Map.of("gpt-4o", new double[] {2.50, 10.00}, "gpt-4o-mini", new double[] {0.15, 0.60},
                    "llama3.2", new double[] {0.0, 0.0}, "qwen3:4b", new double[] {0.0, 0.0});
    private static final double[] DEFAULT_PRICES = new double[] {1.00, 3.00};
    private static final double PER_MILLION = 1_000_000.0;

    private final ChatClient chatClient;

    public TokenTakipDemo(ChatClient.Builder builder) {
        this.chatClient =
                builder.defaultSystem("Sen yardimci bir asistansin. Turkce yanit ver").build();
    }

    public static void main(String[] args) {
        SpringApplication.run(TokenTakipDemo.class, args);
    }

    // TEMEL KULLANIM — Izlemeli Sohbet
    @PostMapping("/chat/tracked")
    // @PostMapping: HTTP POST isteklerine baglar. POST = veri gonderme istegi (JSON body ile)
    // @RequestBody: Gelen JSON'i otomatik olarak Java objesine (burada Map) donusturur
    public Map<String, String> chatTracked(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message bos olamaz");
        }

        ChatResponse chatResponse = chatClient.prompt().user(message).call().chatResponse();
        // chatResponse() bize tam ChatResponse objesi veriyor


        // Token bilgisini arka planda logluyoruz
        TokenUsageInfo usage = extractUsage(chatResponse);
        log.info(
                "Token Kullanimi | Model: {} | Prompt: {} | Completion: {} | Tpolam: {} | Maliyet: {} | Mesaj: {}",
                usage.model(), usage.promptTokens(), usage.completionTokens(), usage.totalTokens(),
                String.format("%.6f", usage.estimatedCostUsd()), message);

        // Kullaniciya sadece metin yaniti don
        String content = chatResponse.getResult().getOutput().getText();
        return Map.of("response", content);
    }


    // GELISMIS KULLANIM — Detayli Yanit
    // yanit + token istatistikleri + maliyet
    @PostMapping("/chat/detailed")
    public Map<String, Object> chatDetailed(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message bos olamaz");
        }

        ChatResponse chatResponse = chatClient.prompt().user(message).call().chatResponse();

        TokenUsageInfo usage = extractUsage(chatResponse);
        String content = chatResponse.getResult().getOutput().getText();

        return Map.of("response", content, "model", usage.model(), "promptTokens",
                usage.promptTokens(), "completionTokens", usage.completionTokens(), "totalTokens",
                usage.totalTokens(), "finishReason", usage.finishReason(), "estimatedCostUsd",
                String.format("%.6f", usage.estimatedCostUsd()));

    }

    // ChatResponse'dan TokenUsageInfo cikarir


    // getMetadata() → ChatResponseMetadata: model adi, request id, usage
    // getResult() → Generation: yanit metni, finish reason
    // Usage → promptTokens, completionTokens, totalTokens

    private TokenUsageInfo extractUsage(ChatResponse chatResponse) {
        ChatResponseMetadata metadata = chatResponse.getMetadata();
        Usage usage = metadata != null ? metadata.getUsage() : null;

        String model = "unknown";
        if (metadata != null && metadata.getModel() != null) {
            model = metadata.getModel();
        }

        String finishReason = "Unknown";
        if (chatResponse.getResult() != null && chatResponse.getResult().getMetadata() != null
                && chatResponse.getResult().getMetadata().getFinishReason() != null) {
            finishReason = chatResponse.getResult().getMetadata().getFinishReason();
        }

        if (usage == null) {
            return new TokenUsageInfo(model, 0L, 0L, 0L, finishReason, 0.0);
        }

        // Maliyet Formülü
        // (promptTokens * inputPrice / 1M) + (completionTokens * outputPrice / 1M)
        double[] prices = resolveModelPrices(model);
        double cost =
                (usage.getPromptTokens() * prices[0] + usage.getCompletionTokens() * prices[1])
                        / PER_MILLION;

        return new TokenUsageInfo(model, usage.getPromptTokens(), usage.getCompletionTokens(),
                usage.getTotalTokens(), finishReason, cost);
    }

    private double[] resolveModelPrices(String model) {
        if (model == null || model.isBlank()) {
            return DEFAULT_PRICES;
        }

        String normalizedModel = model.toLowerCase(Locale.ROOT).trim();
        double[] exactMatch = MODEL_PRICES.get(normalizedModel);
        if (exactMatch != null) {
            return exactMatch;
        }

        String bestKey = null;
        for (String key : MODEL_PRICES.keySet()) {
            boolean isVersionedOrTagged =
                    normalizedModel.startsWith(key + "-") || normalizedModel.startsWith(key + ":");
            if (!isVersionedOrTagged) {
                continue;
            }
            if (bestKey == null || key.length() > bestKey.length()) {
                bestKey = key;
            }
        }

        if (bestKey != null) {
            return MODEL_PRICES.get(bestKey);
        }
        return DEFAULT_PRICES;
    }

    /** Token kullanim bilgilerini tutan kayit — her istekten sonra olusturulur */
    public record TokenUsageInfo(String model, long promptTokens, long completionTokens,
            long totalTokens, String finishReason, double estimatedCostUsd) {

    }

}
