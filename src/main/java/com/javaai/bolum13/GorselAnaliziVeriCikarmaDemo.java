package com.javaai.bolum13;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Profile("openai")
@EnableAutoConfiguration
@RestController
@RequestMapping(value = "/api/b132", produces = "application/json;charset=UTF-8")

public class GorselAnaliziVeriCikarmaDemo {

    private final ChatClient chatClient;

    public GorselAnaliziVeriCikarmaDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem(
                "Sen bir gorsel analiz asistanisin. Gorsel icerikleri Turkce olarak yapilandirilmis formatta analiz et.")
            .build();
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai");
        SpringApplication.run(GorselAnaliziVeriCikarmaDemo.class, args);
    }


    @PostMapping(value = "/yapili-analiz", consumes = "multipart/form-data") // POST: gorsel → GorselRapor record — entity() kalibi
    public Map<String, Object> yapiliAnaliz(
        @RequestParam("image") MultipartFile image
    ) throws Exception {
        Media media = new Media(resolveMimeType(image), image.getResource()); // MIME tipi + dosya verisi → Media

        GorselRapor rapor = chatClient.prompt()
            .user(u -> u
                .text("Bu gorseli analiz et. aciklama, nesneler (liste), baskinRenk ve kategori alanlarini doldur.")
                .media(media))
            .call()
            .entity(GorselRapor.class);

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("fileName", image.getOriginalFilename());
        response.put("rapor", rapor);

        return response;
    }


    @PostMapping(value = "/fatura-analiz", consumes = "multipart/form-data") // POST: fatura gorseli → FaturaAnaliz record — OCR benzeri veri cikarma
    public Map<String, Object> faturaAnaliz(
        @RequestParam("image") MultipartFile image
    ) throws Exception {
        Media media = new Media(resolveMimeType(image), image.getResource());

        // FaturaAnaliz record: model fatura icerigini tarih, tutar, kalemler olarak ayristirir
        FaturaAnaliz fatura = chatClient.prompt()
            .user(u -> u
                .text("Bu fatura veya fis gorselini analiz et. " +
                    "firmaAdi, tarih, toplamTutar (sayisal), paraBirimi ve kalemler (liste) alanlarini doldur. " +
                    "Gorsel fatura degilse alanlari bos birak.")
                .media(media))
            .call()
            .entity(FaturaAnaliz.class);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fileName", image.getOriginalFilename());
        response.put("fatura", fatura);
        return response;
    }


    @PostMapping(value = "/coklu-gorsel", consumes = "multipart/form-data")
    public Map<String, Object> cokluGorsel(
        @RequestParam("images") List<MultipartFile> images
    ) throws Exception {

        List<Media> mediaListesi = new ArrayList<>();
        for (MultipartFile img : images) {
            mediaListesi.add(new Media(resolveMimeType(img), img.getResource())); // MIME + binary → Media
        }

        // Coklu Media: .media() her cagrisinda bir gorsel ekleniyor — GPT-4o hepsini birlikte goruyor
        String karsilastirma = chatClient.prompt() // Yeni prompt zinciri
            .user(u -> {                        // u → PromptUserSpec: lambda ile dinamik icerik
                u.text(String.format("%d gorsel yuklendi. Bu gorselleri karsilastir: benzerlikler, farkliliklar, icerik, ton ve kategori.",
                    mediaListesi.size()));  // Kac gorsel oldugunu modele bildiriyoruz
                for (Media m : mediaListesi) {
                    u.media(m);                 // Her gorseli ayri .media() cagrisiyla ekle — coklu multimodal
                }
            })
            .call()
            .content();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("imageCount", images.size());
        response.put("karsilastirma", karsilastirma);
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

    record GorselRapor(
        String aciklama,
        List<String> nesneler,
        String baskinRenk,
        String kategori
    ) {

    }

    record FaturaAnaliz(
        String firmaAdi,
        String tarih,
        double toplamTutar,
        String paraBirimi,
        List<String> kalemler
    ) {

    }
}