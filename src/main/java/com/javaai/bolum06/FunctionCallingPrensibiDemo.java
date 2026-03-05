package com.javaai.bolum06;

import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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
@RequestMapping("/api/b61")

public class FunctionCallingPrensibiDemo {

    private static final Logger log = LoggerFactory.getLogger(FunctionCallingPrensibiDemo.class);

    private final ChatClient chatClient;
    private final IntentClassifier intentClassifier;

    public FunctionCallingPrensibiDemo(ChatClient.Builder builder) {
        this.intentClassifier = new IntentClassifier();
        this.chatClient = builder
            .defaultSystem(
                "Sen function calling konusunda uzman bir asistansın. Kısa, net ve teknik olarak dogru Turkce cevap ver")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(FunctionCallingPrensibiDemo.class, args);
    }

    // IntentClassifier // IF ELSE yapısı
    @GetMapping("/explain")
    public Map<String, Object> explain(
        @RequestParam(required = false) String task) {
        String effectiveTask = task == null ? "Kur cevirisi istiyorum" : task;

        String explanation = chatClient.prompt()
            .user(String.format(
                "Asagidaki gorev icin function calling gerekir mi? Gerekceyi 2 cumleyle acikla. Gorev %s",
                effectiveTask.trim()))
            .call()
            .content();

        return Map.of(
            "task:", effectiveTask.trim(),
            "explanation", explanation
        );
    }

    // ChatClient // Niyet Etiketi
    @GetMapping("/classify-intent")
    public Map<String, Object> classifyIntent(
        @RequestParam(defaultValue = "100 USD TRY'ye çevir") String message) {
        Map<String, String> ruleBased = intentClassifier.classify(
            message.trim()); //Kural Bazlı Sınıflandırma

        String modelDecision = chatClient.prompt()
            .user(String.format(
                "Asagidaki mesaji sadece iki etiketten biriyle sinifla: TOOL_GEREKLI, CHAT_YETERLI. Sadece etiketi yaz. Mesaj: %s",
                message.trim()))
            .call()
            .content();

        log.info("message: {}, rule-based: {}", message, ruleBased.get("intent"));

        return Map.of(
            "message", message.trim(),
            "rulebased:", ruleBased,
            "modelDecision", modelDecision == null ? "" : modelDecision.trim()
        );
    }

    // Tool calling = tek eylem, cagir ve cik
    // Agent = cok adimli planlama + zincir
    @GetMapping("tool-vs-agent")
    public Map<String, Object> toolVsAgent(
        @RequestParam(defaultValue = "100 USD TRY'ye çevir") String message) {
        Map<String, String> ruleBased = intentClassifier.classify(message.trim());

        boolean toolNeeded = "TOOL_GEREKLI".equals(ruleBased.get("intent"));

        // Tool Calling perspektifi
        String toolCallingPerspective = toolNeeded
            ? "Tek bir gorevi cozen uygun tool secilir ve sonuc geri getirilir"
            : "Tool tetiklenmeden model kendi bilgisiyle cevap verebilir";

        // Agent Perspektifi
        String agentPerspective = toolNeeded
            ? "Agent akisi birden fazla adim planlayip birden cok tool'u sirayla cagirabilir"
            : "Agent, bu durumda da planlama yapabilir ama maliyet gereksiz olur";

        return Map.of(
            "message", message.trim(),
            "ruleBasedIntent", ruleBased.get("intent"),
            "toolCallingPerspective", toolCallingPerspective,
            "agentPerspective", agentPerspective
        );
    }

    static class IntentClassifier {

        public Map<String, String> classify(String message) {
            String normalized = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains("cevir") || normalized.contains("kur") ||
                normalized.contains("usd") || normalized.contains("eur") ||
                normalized.contains("api") || normalized.contains("veritabani") ||
                normalized.contains("anlik")) {
                return Map.of(
                    "intent", "TOOL_GEREKLI",
                    "reason", "Dis kaynak veya hesaplama ihtiyaci var");
            }

            return Map.of(
                "intent", "CHAT_YETERLI",
                "reason", "Modelin kendi bilgisiyle cevaplanabilir");
        }
    }
}
