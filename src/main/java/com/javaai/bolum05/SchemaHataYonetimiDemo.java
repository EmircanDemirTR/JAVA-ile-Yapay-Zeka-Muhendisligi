package com.javaai.bolum05;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
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
@RequestMapping("/api/b55")

public class SchemaHataYonetimiDemo {

    // 1) Schema gorunurlugu: converter.getFormat() ile modelden ne bekledigimizi acikca goruruz.
    // 2) try-catch ile kontrol: parse hatasi olursa kullaniciya anlamli hata mesaji doneriz.
    // 3) Sinirli retry: gecici sorunlari absorbe ederiz (max 3 deneme — her retry token tuketir!)

    private static final Logger log = LoggerFactory.getLogger(SchemaHataYonetimiDemo.class);

    private static final int MAX_PREVIEW_LENGTH = 320;

    private static final int MIN_RETRY = 1;
    private static final int MAX_RETRY = 3;

    private final ChatClient chatClient;

    public SchemaHataYonetimiDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem(
                "Sen dikkatli bir veri cikarim asistanisin. Alan adlarini eksiksiz doldur.")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(SchemaHataYonetimiDemo.class, args);
    }

    //Schema Görünürlüğü
    @GetMapping("/schema")
    public Map<String, Object> schema() {
        BeanOutputConverter<MovieInfoStrict> converter = new BeanOutputConverter<>(
            MovieInfoStrict.class);
        return Map.of(
            "type", "MovieInfoStrict",
            "schema", converter.getFormat()
        );
    }

    // Temel Kullanım - Formatli Mutlu Yol
    @GetMapping("/strict-movie")
    public Map<String, Object> strictMovie(@RequestParam(defaultValue = "Inception") String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title bos olamaz");
        }
        BeanOutputConverter<MovieInfoStrict> converter = new BeanOutputConverter<>(
            MovieInfoStrict.class); // Hedef tip: MovieInfoStrict
        String formatInstructions = converter.getFormat(); // JSON schema metni

        String rawResponse = "";
        try {
            rawResponse = chatClient
                .prompt()
                .user(u -> u
                    .text("""
                        "{title}" filmi için su alanlari doldur:
                        title, year, genre, ratingTier, director, summary.
                        ratingTier degeri HIGH, MEDIUM veya LOW olsun.
                        {format}
                        """)
                    .param("title", title.trim())
                    .param("format", formatInstructions))
                .call()
                .content();

            MovieInfoStrict movie = converter.convert(rawResponse);

            return Map.of(
                "movie", movie,
                "rawPreview", rawResponse
            );

        } catch (Exception ex) {
            return buildConversionError("STRICT_PARSE_FAILED", ex, rawResponse);
        }
    }

    // Gelişmiş Kuallnım - Kaotik Prompt -- Format Olmadan
    @GetMapping("/chaos-test")
    public Map<String, Object> chaosTest(
        @RequestParam(defaultValue = "Bir seyler yaz") String prompt) {

        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt bos olamaz");
        }
        BeanOutputConverter<MovieInfoStrict> converter = new BeanOutputConverter<>(
            MovieInfoStrict.class);
        String rawResponse = "";

        try {
            rawResponse = chatClient
                .prompt()
                .user(prompt.trim())
                .call()
                .content();

            MovieInfoStrict movie = converter.convert(rawResponse);

            return Map.of(
                "movie", movie,
                "rawPreview", rawResponse
            );

        } catch (Exception ex) {
            return buildConversionError("CHAOS_PARSE_FAILED", ex, rawResponse);
        }

    }

    private Map<String, Object> buildConversionError(String code, Exception ex,
        String rawResponse) {
        String message = ex.getMessage() == null ? "Donusum hatasi" : ex.getMessage();
        return Map.of(
            "status", "error",
            "errorCode", code,
            "errorMesage:", message
        );
    }

    public record MovieInfoStrict(String title, int year, String genre, String ratingTier,
                                  String director, String summary) {

        public MovieInfoStrict {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("title bos olamaz");
            }
            if (year < 1888) {
                throw new IllegalArgumentException(
                    "year gecersiz (min 1888)");  // Ilk film 1888'de cekildi
            }
            if (genre == null || genre.isBlank()) {
                throw new IllegalArgumentException("genre bos olamaz");
            }
            if (ratingTier == null || ratingTier.isBlank()) {
                throw new IllegalArgumentException("ratingTier bos olamaz");
            }
            if (director == null || director.isBlank()) {
                throw new IllegalArgumentException("director bos olamaz");
            }
            if (summary == null || summary.isBlank()) {
                throw new IllegalArgumentException("summary bos olamaz");
            }
        }
    }

}
