package com.javaai.bolum04;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("api/b42")

public class PromptDosyaSablonDemo {

    private static final Logger log = LoggerFactory.getLogger(PromptDosyaSablonDemo.class);
    private final ChatClient chatClient;
    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemPromptResource;
    @Value("classpath:/prompts/code-review.st")
    private Resource codeReviewResource;
    @Value("classpath:/prompts/summarize-v1.st")
    private Resource summarizeV1Resource;
    @Value("classpath:/prompts/summarize-v2.st")
    private Resource summarizeV2Resource;

    public PromptDosyaSablonDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem(
                "Sen dikkatli bir kod ve icerik asistanisin. Cevaplari Turkce olarak ver.")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(PromptDosyaSablonDemo.class, args);
    }

    // Temel Kullanım
    @PostMapping("/code-review")
    public Map<String, Object> codeReviewFromFile(@RequestBody CodeReviewRequest request) {
        String systemTemplateText = loadTemplate(systemPromptResource,
            "system-prompt.st"); // Global Kural Seti
        String reviewTemplateText = loadTemplate(codeReviewResource,
            "code-review.st"); // Görev Şablonu

        String response = chatClient.prompt()
            .system(s -> s.text(systemTemplateText)
                .param("rol", "Yazilim")
                .param("format", "madde madde")
                .param("dil", "Turkce"))
            .user(u -> u
                .text(reviewTemplateText)
                .param("dil", request.dil())
                .param("kod", request.kod()))
            .call()
            .content();

        return Map.of(
            "template", "code-review.st",
            "dil", request.dil(),
            "yanit", response
        );
    }

    // Gelişmiş Kullanım
    @PostMapping("/summarize")
    public Map<String, Object> summarizeWithVersion(
        @RequestBody SummarizeRequest request,
        @RequestParam(name = "v", defaultValue = "v1") String version
    ) {
        String normalizedVersion = normalizeVersionWithFallback(version);
        Resource selectedResource =
            "v2".equals(normalizedVersion) ? summarizeV2Resource : summarizeV1Resource;

        PromptTemplate template = new PromptTemplate(
            selectedResource); // Seçilen şablonu parse ediyor.
        Prompt prompt = template.create(Map.of("metin", request.metin()));

        long start = System.currentTimeMillis();
        String response = chatClient
            .prompt(prompt)
            .call()
            .content();
        long latency = System.currentTimeMillis() - start;

        return Map.of(
            "version", normalizedVersion,
            "latencyMs", latency,
            "ozet", response
        );
    }

    @GetMapping("/template/info")
    public Map<String, Object> templateInfo() {
        return Map.of(
            "system", "prompts/system-prompt.st",
            "codeReview", "prompts/code-review.st",
            "summarizeV1", "prompts/summarize-v1.st",
            "summarizeV2", "prompts/summarize-v2.st"
        );
    }

    private String loadTemplate(Resource resource, String fileName) {
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Prompt dosyasi okunamadi" + fileName, e);
        }
    }

    private String normalizeVersionWithFallback(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("v bos olamaz");
        }
        String normalized = version.trim().toLowerCase(Locale.ROOT);
        if (!"v1".equals(normalized) && !"v2".equals(normalized)) {
            log.warn("Gecersiz versiyon alındı. v1 fallback uygulandi.");
            return "v1";
        }
        return normalized;
    }

    public record CodeReviewRequest(String dil, String kod) {

        public CodeReviewRequest {
            if (dil == null || dil.isBlank()) {
                throw new IllegalArgumentException("dil bos olamaz");
            }
            if (kod == null || kod.isBlank()) {
                throw new IllegalArgumentException("kod bos olamaz");
            }
        }
    }

    public record SummarizeRequest(String metin) {

        public SummarizeRequest {
            if (metin == null || metin.isBlank()) {
                throw new IllegalArgumentException("metin bos olamaz");
            }
        }
    }
}
