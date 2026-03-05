package com.javaai.bolum05;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b53")

public class EntityDonusumDemo {

    private static final Logger log = LoggerFactory.getLogger(EntityDonusumDemo.class);

    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 8;
    private final ChatClient chatClient;

    public EntityDonusumDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem(
                "Sen bir kutuphane ve urun katalog asistanisin. Cevaplari Turkce acikla.")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(EntityDonusumDemo.class, args);
    }

    // Temel Kullanım
    @GetMapping("/book")
    public Map<String, Object> book(
        @RequestParam(defaultValue = "Crime and Punishment") String title) {

        BookInfo bookInfo = chatClient.prompt()
            .user("""
                "%s" kitabı icin su alanları doldur:
                title, author, publishYear, pageCount, genre.""".formatted(title.trim()))
            .call()
            .entity(BookInfo.class);

        return Map.of(
            "mode", "single-entity",
            "requestedTitle", title.trim(),
            "book", bookInfo
        );
    }

    // Gelişmiş Kullanım
    @GetMapping("/top-books")
    public Map<String, Object> topBooks(
        @RequestParam(defaultValue = "world classics") String topic,
        @RequestParam(defaultValue = "3") int count
    ) {
        List<BookInfo> books = chatClient.prompt()
            .user("""
                "%s" konusu icin %d kitap oner.
                Her kitapta su alanlari doldur:
                title, author, publishYear, pageCount, genre.
                """.formatted(topic.trim(), count))
            .call()
            .entity(new ParameterizedTypeReference<List<BookInfo>>() {
            });

        return Map.of(
            "mode", "list-entity",
            "topic", topic.trim(),
            "requestCount", count,
            "books", books.stream().limit(count).toList()
        );
    }

    /**
     * Kitap verisini tasiyan record.
     *
     * @param title       Kitap adi
     * @param author      Yazar
     * @param publishYear Yayin yili (1450+ olmali)
     * @param pageCount   Sayfa sayisi (pozitif olmali)
     * @param genre       Tur
     */
    public record BookInfo(String title, String author, int publishYear, int pageCount,
                           String genre) {

        public BookInfo {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("title bos olamaz");
            }
            if (author == null || author.isBlank()) {
                throw new IllegalArgumentException("author bos olamaz");
            }
            if (publishYear < 1450) {
                throw new IllegalArgumentException("publishYear gecersiz");
            }
            if (pageCount <= 0) {
                throw new IllegalArgumentException("pageCount pozitif olmali");
            }
            if (genre == null || genre.isBlank()) {
                throw new IllegalArgumentException("genre bos olamaz");
            }
        }
    }

    /**
     * Urun katalog verisini tasiyan record.
     *
     * @param name     Urun adi
     * @param category Kategori
     * @param price    Fiyat (negatif olamaz)
     * @param inStock  Stok durumu
     */
    public record ProductInfo(String name, String category, double price, boolean inStock) {

        public ProductInfo {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name bos olamaz");
            }
            if (category == null || category.isBlank()) {
                throw new IllegalArgumentException("category bos olamaz");
            }
            if (price < 0.0) {
                throw new IllegalArgumentException("price negatif olamaz");
            }
        }
    }
}
