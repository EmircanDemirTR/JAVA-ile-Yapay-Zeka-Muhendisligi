package com.javaai.bolum08;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/b83")

public class ChainRoutingWorkflowDemo {
    // Chain: Deterministik sirali donusum
    // Routing: Karar noktası olan dinamik yol secimi

    private final ChatClient chatClient;

    public ChainRoutingWorkflowDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("Sen cok yetenekli bir asistansin")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(ChainRoutingWorkflowDemo.class, args);
    }

    // Chain Workflow - Sıralı işlem zinciri
    @GetMapping("/chain")
    public Map<String, Object> chainWorkflow(
        @RequestParam(defaultValue = "Yapay zeka, modern dunyanın en onemli teknolojilerinden biridir") String text) {

        // Adım 1
        String translatedText = chatClient.prompt()
            .user(String.format("Bu Turkce metni Ingilizce'ye cevir: %s", text))
            .call()
            .content();

        // Adım 2
        String summarizedText = chatClient.prompt()
            .user(String.format("Bu metni 1 cumlede ozetle: %s", translatedText))
            .call()
            .content();

        // Adım 3
        String formattedText = chatClient.prompt()
            .user(String.format("Bu ozeti Markdown formatinda yaz (baslik + icreik): %s", summarizedText))
            .call()
            .content();

        List<Map<String, String>> steps = new ArrayList<>();
        steps.add(Map.of("step", "1-Translation", "output", translatedText));
        steps.add(Map.of("step", "2-Summary", "output", summarizedText));
        steps.add(Map.of("step", "3-Formatsss", "output", formattedText));

        return Map.of(
            "originalText", text,
            "steps", steps,
            "finalResult", formattedText
        );
    }

    // Routing Workflow - Kategori bazlı yonlendirme
    @GetMapping("/route")
    public Map<String, Object> routingWorkflow(@RequestParam(defaultValue = "Java'da thread nedir?") String question) {

        // Adım 1 - Kategori Tespiti
        String categoryRaw = chatClient.prompt()
            .user(String.format("Bu soruyu kategorize et. Sadece su kelimelerden birini yaz. TEKNIK, FINANS, GENEL \n\n Soru: %s", question))
            .call()
            .content()
            .trim()
            .toUpperCase();

        // Adım 2 - Kategori Normalizasyon
        String category = "GENEL";
        if (categoryRaw.contains("TEKNIK")) {
            category = "TEKNIK";
        } else if (categoryRaw.contains("FINANS")) {
            category = "FINANS";
        }

        // Adım 3 - Kategoriye uygun system promp seçimi
        String systemPrompt = switch (category) {
            case "TEKNIK" -> "Sen uzman bir yazilim muhendisisin. Teknik soruları kod ornekleriyle acikla";
            case "FINANS" -> "Sen deneyimli bir finans danısmanısın. Ekonomik terimleri basit dille acikla";
            default -> "Sen genel kultur uzmanisin. Soruları herkesin anlayacagi sekilde acikla";
        };

        // Adım 4
        ChatClient routedClient = chatClient.mutate()
            .defaultSystem(systemPrompt)
            .build();

        String answer = routedClient.prompt()
            .user(question)
            .call()
            .content();

        return Map.of(
            "question", question,
            "category", category,
            "answer", answer
        );
    }

    // Chain + Routing Birlestirme
    @GetMapping("/chain-route")
    public Map<String, Object> chainRouteWorkflow(
        @RequestParam(defaultValue = "Blockchain teknolojisi nedir?") String question
    ) {

        // Adim 1: Kategori tespit (routing kismi) — once triaj yap.
        String categoryRaw = chatClient.prompt() // Yeni prompt
            .user(String.format("Bu soruyu kategorize et. Sadece su kelimelerden birini yaz: TEKNIK, FINANS, GENEL\n\nSoru: %s",
                question)) // Kategorizasyon
            .call()
            .content()
            .trim()
            .toUpperCase();

        // Adim 2: Kategori normalizasyon
        String category = "GENEL";
        if (categoryRaw.contains("TEKNIK")) {
            category = "TEKNIK";
        } else if (categoryRaw.contains("FINANS")) {
            category = "FINANS";
        }

        // Adim 3: Kategoriye uygun system prompt
        String systemPrompt = switch (category) {
            case "TEKNIK" -> "Sen uzman bir yazilim muhendisisin. Teknik sorulari detayli acikla.";
            case "FINANS" -> "Sen deneyimli bir finans danismanisin. Ekonomik kavramlari anlat.";
            default -> "Sen genel kultur uzmanisin. Basit ve anlasilir acikla.";
        };

        // Adim 4: mutate() ile kategoriye ozel client olustur
        ChatClient routedClient = chatClient.mutate()
            .defaultSystem(systemPrompt)
            .build();

        // Adim 5-8: Chain workflow — routing bittikten sonra 4 adimli is akisi basliyor.
        String answer = routedClient.prompt()
            .user(question)
            .call()
            .content();
        String translatedAnswer = routedClient.prompt()
            .user(String.format("Bu metni Ingilizce'ye cevir: %s", answer))
            .call()
            .content();
        String summarizedAnswer = routedClient.prompt()
            .user(String.format("Bu metni 2 cumlede ozetle: %s", translatedAnswer))
            .call()
            .content();
        String formattedAnswer = routedClient.prompt()
            .user(String.format("Bu ozeti Markdown formatinda yaz (baslik + icerik): %s", summarizedAnswer))
            .call()
            .content();

        List<Map<String, String>> chainSteps = new ArrayList<>();
        chainSteps.add(Map.of("step", "1-Answer", "output", answer));
        chainSteps.add(Map.of("step", "2-Translation", "output", translatedAnswer));
        chainSteps.add(Map.of("step", "3-Summary", "output", summarizedAnswer));
        chainSteps.add(Map.of("step", "4-Format", "output", formattedAnswer));

        return Map.of(
            "lesson", "B8.3 - Chain + Routing Combined",
            "question", question,
            "category", category,
            "chainSteps", chainSteps,
            "finalResult", formattedAnswer
        );
    }

}
