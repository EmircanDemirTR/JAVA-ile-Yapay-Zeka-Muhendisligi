package com.javaai.bolum13;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;


@Profile("openai")
@EnableAutoConfiguration
@RestController
@RequestMapping(value = "/api/b131", produces = "application/json;charset=UTF-8")

public class MultimodalApiKesfetmeDemo {


    private static final int MAX_REMOTE_IMAGE_BYTES = 20 * 1024 * 1024; // Vision limiti: 20 MB
    private static final int READ_BUFFER_SIZE = 8 * 1024; // Stream okuma tamponu
    private static final int REMOTE_IMAGE_TIMEOUT_SECONDS = 20; // Uzak URL timeout
    private final ChatClient chatClient;


    public MultimodalApiKesfetmeDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("Sen bir gorsel analiz asistanisin. Gorseldeki detaylari Turkce acikla.")
            .build();
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai");
        SpringApplication.run(MultimodalApiKesfetmeDemo.class, args);
    }

    @GetMapping("/api-rehber")
    public Map<String, Object> apiRehber() {

        Map<String, Object> vision = new LinkedHashMap<>();
        vision.put("model", "GPT-4o");                              // Gorsel anlayan model
        vision.put("springAiClass", "ChatClient + Media");          // Spring AI'da kullanilan siniflar
        vision.put("destekGirdi", List.of("JPEG", "PNG", "GIF", "WebP")); // Desteklenen gorsel formatlar
        vision.put("ornek", "chatClient.prompt().user(u -> u.text(...).media(...)).call().content()"); // Kod kalibi

        Map<String, String> imageGen = new LinkedHashMap<>();
        imageGen.put("model", "DALL-E 3");                               // Gorsel ureten model
        imageGen.put("springAiClass", "ImageModel");                     // Spring AI'da kullanilan sinif
        imageGen.put("aciklama", "Metin prompt'undan gorsel uretir");    // Ne ise yarar

        Map<String, Object> tts = new LinkedHashMap<>();
        tts.put("modeller", List.of("tts-1", "tts-1-hd"));            // Kullanilabilir TTS modeller
        tts.put("springAiClass", "SpeechModel");                       // Spring AI sinifi
        tts.put("aciklama", "Metin -> MP3 ses dosyasi donusturur");    // Ne yapar

        Map<String, String> transcription = new LinkedHashMap<>();
        transcription.put("model", "Whisper");                           // OpenAI'nin transkripsiyon modeli
        transcription.put("springAiClass", "TranscriptionModel");        // Spring AI sinifi
        transcription.put("aciklama", "Ses dosyasi -> metin donusturur"); // Ne yapar

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("lesson", "B13.1");
        response.put("vision", vision);                  // Bu dersin konusu: gorsel anlama
        response.put("imageGeneration", imageGen);
        response.put("textToSpeech", tts);
        response.put("transcription", transcription);
        return response;
    }

    @GetMapping("/format-destegi")
    public Map<String, Object> formatDestegi() {

        Map<String, Map<String, String>> gorselFormatlari = new LinkedHashMap<>();
        gorselFormatlari.put("JPEG", Map.of("mimeType", "image/jpeg", "springAiSabiti", "MimeTypeUtils.IMAGE_JPEG"));  // En yaygin gorsel format
        gorselFormatlari.put("PNG", Map.of("mimeType", "image/png", "springAiSabiti", "MimeTypeUtils.IMAGE_PNG"));   // Saydam arka plan destekli
        gorselFormatlari.put("GIF", Map.of("mimeType", "image/gif", "springAiSabiti", "MimeTypeUtils.IMAGE_GIF"));   // Animasyonlu/statik
        gorselFormatlari.put("WebP", Map.of("mimeType", "image/webp", "springAiSabiti", "MimeType.valueOf(\"image/webp\")"));

        Map<String, Map<String, String>> sesFormatlari = new LinkedHashMap<>();
        sesFormatlari.put("MP3", Map.of("mimeType", "audio/mpeg", "kullanim", "Transkripsiyon + TTS cikti")); // En yaygin ses format
        sesFormatlari.put("MP4", Map.of("mimeType", "audio/mp4", "kullanim", "Transkripsiyon"));             // Video ses kanalı
        sesFormatlari.put("WAV", Map.of("mimeType", "audio/wav", "kullanim", "Transkripsiyon"));             // Ham ses, yüksek kalite
        sesFormatlari.put("FLAC", Map.of("mimeType", "audio/flac", "kullanim", "Transkripsiyon"));             // Kayipsiz sikistirma
        sesFormatlari.put("WEBM", Map.of("mimeType", "audio/webm", "kullanim", "Transkripsiyon"));             // Web tarayici kayit format

        Map<String, Integer> dosyaBoyutLimitleri = new LinkedHashMap<>();
        dosyaBoyutLimitleri.put("gorselMaxMB", 20);      // OpenAI Vision API limiti: 20 MB
        dosyaBoyutLimitleri.put("sesMaxMB", 25);         // Whisper API limiti: 25 MB
        dosyaBoyutLimitleri.put("springBootMaxMB", 25);  // Spring Boot multipart varsayilan — application.yml'de arttirilabilir

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("lesson", "B13.1");
        response.put("gorselFormatlari", gorselFormatlari);
        response.put("sesFormatlari", sesFormatlari);
        response.put("dosyaBoyutLimitleri", dosyaBoyutLimitleri);
        return response;
    }

    @PostMapping(value = "/upload-analiz", consumes = "multipart/form-data")
    public Map<String, Object> uploadAnaliz(
        @RequestParam("image") MultipartFile image,
        @RequestParam(defaultValue = "Bu gorselde ne goruyorsun?") String question
    ) {
        MimeType mimeType = resolveMimeType(image);
        Media media = new Media(mimeType, image.getResource());

        String analysis = chatClient.prompt()
            .user(u -> u
                .text(question)
                .media(media))
            .call()
            .content();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fileName", image.getOriginalFilename());
        response.put("fileSize", image.getSize());
        response.put("mimeType", mimeType.toString());
        response.put("question", question);
        response.put("answer", analysis);
        return response;
    }

    @GetMapping("/url-analiz")
    public Map<String, Object> urlAnaliz(
        @RequestParam String imageUrl,
        @RequestParam(defaultValue = "Bu gorselde ne goruyorsun? Detayli acikla") String question
    ) {
        Media media = downloadUrlMedia(imageUrl);

        String analysis = chatClient.prompt()
            .user(u -> u
                .text(question)
                .media(media))
            .call()
            .content();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("imageUrl", imageUrl);
        response.put("question", question);
        response.put("answer", analysis);
        return response;

    }

    private Media downloadUrlMedia(String imageUrl) {
        URI imageUri;
        imageUri = URI.create(imageUrl);

        String scheme = imageUri.getScheme();
        boolean isHttpOrHttps = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        if (!isHttpOrHttps) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sadece http/https gorsel URL'leri desteklenir.");
        }

        HttpRequest request = HttpRequest.newBuilder(imageUri)
            .GET() // URL'den gorseli cek
            .timeout(Duration.ofSeconds(REMOTE_IMAGE_TIMEOUT_SECONDS))
            .header("User-Agent", "JavaAIBot/1.0 (+https://emircandemir.com)") // Kaynaga kendimizi tanittik
            .header("Accept", "image/*")
            .build();

        HttpResponse<InputStream> response;
        try {
            response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL) // 301/302 takip et
                .connectTimeout(Duration.ofSeconds(REMOTE_IMAGE_TIMEOUT_SECONDS))
                .build() // Kisa omurlu, tek istek odakli client
                .send(request, HttpResponse.BodyHandlers.ofInputStream()); // Body'yi stream olarak al
        } catch (InterruptedException ex) {
            // Thread kesintisi geldiyse interrupt flag'i geri yaziyoruz.
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                "Gorsel indirme islemi zaman asimina ugradi: " + imageUri, ex);
        } catch (IOException ex) {
            // DNS, socket, TLS vb. ag problemlerini 400 ile netlestiriyoruz.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Gorsel URL'ine erisilemedi veya dosya indirilemedi: " + imageUri, ex);
        }

        MimeType mimeType = resolveMimeTypeFromUrl(imageUrl);
        String contentType = response.headers().firstValue("Content-Type").orElse(null);
        if (contentType != null && !contentType.isBlank()) {
            String normalized = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                // text/html gibi yanitlarin "gorsel" gibi islenmesini engelliyoruz.
                if (!normalized.startsWith("image/")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "URL bir gorsel donmuyor. Content-Type: " + contentType);
                }
                try {
                    // image/png, image/jpeg gibi tipleri dogrudan parse et.
                    mimeType = MimeType.valueOf(normalized);
                } catch (IllegalArgumentException ignored) {
                    // Taninmayan image/* gelirse uzantiya gore fallback kullanilir.
                }
            }
        }

        // Stream'i parca parca okuyarak RAM'i kontrollu kullaniriz.
        // Her parca sonrasi boyut limiti kontrolu yapilir.
        try (InputStream inputStream = response.body();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[READ_BUFFER_SIZE];
            int totalBytesRead = 0;
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalBytesRead += bytesRead;
                if (totalBytesRead > MAX_REMOTE_IMAGE_BYTES) {
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "Gorsel boyutu 20 MB limitini asiyor.");
                }
                outputStream.write(buffer, 0, bytesRead);
            }

            // 0 byte donen yanitlar gecerli gorsel kabul edilmez.
            if (totalBytesRead == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gorsel URL'inden veri okunamadi.");
            }
            // Adim 8: Byte array + MIME bilgisinden Media olusturup caller'a donuyoruz.
            return new Media(mimeType, new ByteArrayResource(outputStream.toByteArray()));
        } catch (IOException ex) {
            // Stream okuma asamasindaki hatalari da ayri mesajla raporluyoruz.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Gorsel verisi okunurken hata olustu: " + imageUri, ex);
        }
    }

    private MimeType resolveMimeTypeFromUrl(String imageUrl) {
        String lower = imageUrl.toLowerCase();
        if (lower.contains(".png")) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        if (lower.contains(".gif")) {
            return MimeTypeUtils.IMAGE_GIF;
        }
        if (lower.contains(".webp")) {
            return MimeType.valueOf("image/webp");
        }
        return MimeTypeUtils.IMAGE_JPEG;
    }


    private ResponseStatusException mapAiException(NonTransientAiException ex, String imageUrl) {
        String message = ex.getMessage();
        if (message != null && message.contains("invalid_image_url")) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "OpenAI gorseli dogrudan indiremedi. URL erisimi veya CDN kisitlari olabilir: " + imageUrl, ex);
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gorsel analiz servisi hatasi.", ex);
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