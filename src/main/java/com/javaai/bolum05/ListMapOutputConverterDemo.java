package com.javaai.bolum05;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b52")

public class ListMapOutputConverterDemo {

    private static final Logger log = LoggerFactory.getLogger(ListMapOutputConverterDemo.class);

    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 10;
    private static final int MAX_PREVIEW_LENGTH = 280;


    private final ChatClient chatClient;

    public ListMapOutputConverterDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("Sen yazilim egitmenisin. Cevaplari net ve ogrenci dostu ver")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(ListMapOutputConverterDemo.class, args);
    }

    // Temel Kullanım -- Liste Formatı
    @GetMapping("/frameworks")
    public Map<String, Object> frameworks(@RequestParam(defaultValue = "5") int count) {
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new IllegalArgumentException("count 1 ile 10 arasında olmalıdır.");
        }

        ListOutputConverter listConverter = new ListOutputConverter(new DefaultConversionService());
        String formatInstructions = listConverter.getFormat();

        String rawResponse = chatClient.prompt()
            .user(user -> user
                .text("""
                    En populer {count} Java Framework adini listele.
                    Her eleman sade ad olsun.
                    {format}
                    """)
                .param("count", count)
                .param("format", formatInstructions))
            .call()
            .content();

        List<String> frameworks = listConverter.convert(rawResponse).stream()
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .limit(count)
            .toList();

        if (frameworks.isEmpty()) {
            throw new IllegalArgumentException("framework listesi bos dondu");
        }

        return Map.of(
            "requestedCount", count,
            "actualCount", frameworks.size(),
            "items", frameworks,
            "rawPreview", shorten(rawResponse)
        );

    }

    // Gelişmiş Kullanım -- Map
    @GetMapping("/language-comparison")
    public Map<String, Object> comparison(
        @RequestParam(defaultValue = "Java") String lang1,
        @RequestParam(defaultValue = "Python") String lang2
    ) {
        MapOutputConverter mapConverter = new MapOutputConverter();
        String formatInstructions = mapConverter.getFormat();

        String rawResponse = chatClient.prompt()
            .user(user -> user
                .text("""
                    {lang1} ve {lang2} dillerini şu başlıklarda karşılaştır:
                    type_system, performance, ecosystem, learning_curve.
                    Her baslik icin kisa aciklama ver.
                    {format}""")
                .param("lang1", lang1.trim())
                .param("lang2", lang2.trim())
                .param("format", formatInstructions))
            .call().content();

        Map<String, Object> comparison = mapConverter.convert(rawResponse);

        return Map.of(
            "lang1", lang1.trim(),
            "lang2", lang2.trim(),
            "comparison", comparison,
            "rawPreview", shorten(rawResponse)
        );
    }

    private String shorten(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_PREVIEW_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_PREVIEW_LENGTH) + "...";
    }

}
