package com.javaai.bolum13;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Profile("ollama")
@EnableAutoConfiguration
@RestController
@RequestMapping(value = "/api/b135", produces = "application/json;charset=UTF-8")

public class OllamaYerelMultimodalDemo {

    private static final Logger log = LoggerFactory.getLogger(OllamaYerelMultimodalDemo.class); // Loglama — hata ve durum takibi

    private final ChatClient ollamaClient;     // LLaVA/Moondream
    private final OllamaChatModel ollamaModel; // Ham model referansi — OllamaOptions ile runtime model secimi icin


    public OllamaYerelMultimodalDemo(
        @Autowired(required = false) OllamaChatModel ollamaModel) {

        this.ollamaClient = ChatClient.builder(ollamaModel)
            .defaultSystem("Sen bir gorsel analiz asistanisin. Turkce acikla.")
            .build();

        this.ollamaModel = ollamaModel;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "ollama");
        SpringApplication.run(OllamaYerelMultimodalDemo.class, args);
    }

    @GetMapping("/model-rehber")
    public Map<String, Object> modelRehber() {

        // LLaVA 7B
        Map<String, Object> llava = new LinkedHashMap<>();
        llava.put("tip", "Yerel (Ollama)");
        llava.put("kalite", "Iyi — genel sahne ve nesne tanimasi (8 GB VRAM+ onerilen)");

        // Moondream 1.8B
        Map<String, Object> moondream = new LinkedHashMap<>();
        moondream.put("tip", "Yerel (Ollama)");
        moondream.put("kalite", "Temel — basit nesneler, genel tanim");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("lesson", "B13.5");
        response.put("llava7b", llava);
        response.put("moondream", moondream);
        response.put("kritikNot", "LLaVA ve Moondream tool calling DESTEKLEMEZ. @Tool veya advisor kullanilamaz.");
        return response;
    }


    @PostMapping(value = "/yerel-analiz", consumes = "multipart/form-data")
    public Map<String, Object> yerelAnaliz(
        @RequestParam("image") MultipartFile image,
        @RequestParam(defaultValue = "llava:7b") String model,
        @RequestParam(defaultValue = "Bu gorselde ne goruyorsun?") String question
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("model", model);
        response.put("question", question);

        MimeType mimeType = resolveMimeType(image);
        Media media = new Media(mimeType, image.getResource());

        long start = System.nanoTime();

        String answer = ollamaClient.prompt()
            .user(u -> u
                .text(question)
                .media(media))
            .options(OllamaChatOptions.builder().model(model).build())
            .call()
            .content();

        response.put("latencyMs", (System.nanoTime() - start) / 1_000_000);
        response.put("answer", answer);
        return response;
    }

    private MimeType resolveMimeType(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase().endsWith(".png")) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        if (name != null && name.toLowerCase().endsWith(".gif")) {
            return MimeTypeUtils.IMAGE_GIF;
        }
        return MimeTypeUtils.IMAGE_JPEG;
    }
}