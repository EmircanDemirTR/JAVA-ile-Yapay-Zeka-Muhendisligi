package com.javaai.bolum13;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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

@Profile("pgvector")
@EnableAutoConfiguration
@RestController
@RequestMapping(value = "/api/b134", produces = "application/json;charset=UTF-8")

public class GorselUretimiMultimodalRagDemo {

    private final ChatClient chatClient;
    private final ImageModel imageModel;   // DALL-E 3 ile gorsel uretim — ImagePrompt → ImageResponse
    private final VectorStore vectorStore; // pgvector — gorsel aciklamalarini embed edip saklar

    public GorselUretimiMultimodalRagDemo(ChatClient.Builder builder,
        ImageModel imageModel,
        VectorStore vectorStore) {
        this.chatClient = builder
            .defaultSystem("Sen bir gorsel analiz asistanisin. Detayli Turkce aciklamalar yap.")
            .build();
        this.imageModel = imageModel;   // Spring AI auto-wire: OpenAiImageModel (DALL-E 3 destekli)
        this.vectorStore = vectorStore; // Spring AI auto-wire: PgVectorStore — embedding + saklama
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        SpringApplication.run(GorselUretimiMultimodalRagDemo.class, args);
    }


    @GetMapping("/uretim-parametreleri")
    public Map<String, Object> uretimParametreleri() {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", "dall-e-3");   // Kullanilan gorsel uretim modeli

        result.put("sizes", List.of(       // DALL-E 3'un destekledigi boyutlar
            Map.of("value", "1024x1024", "use", "Kare — genel amacli, en dusuk maliyet"),
            Map.of("value", "1024x1792", "use", "Dikey — poster, portre, mobil banner"),
            Map.of("value", "1792x1024", "use", "Yatay — sinema formati, manzara, header")
        ));

        result.put("quality", List.of(     // Kalite seviyeleri ve yaklasik maliyetler
            Map.of("value", "standard", "cost", "~$0.04/gorsel", "use", "Hizli prototipleme, yuksek hacim"),
            Map.of("value", "hd", "cost", "~$0.08/gorsel", "use", "Yuksek detay, final uretim, sunum materyali")
        ));

        result.put("style", List.of(      // Stil secimleri
            Map.of("value", "vivid", "use", "Canli, dramatik, hiperrealist — dikkat cekici gorseller"),
            Map.of("value", "natural", "use", "Gercekci, fotografik — urun fotografi, belgesel tarzi")
        ));

        return result;
    }

    @PostMapping("/gorsel-uret")
    public Map<String, Object> gorselUret(
        @RequestParam String prompt,
        @RequestParam(defaultValue = "1024x1024") String size,
        @RequestParam(defaultValue = "hd") String quality,
        @RequestParam(defaultValue = "vivid") String style
    ) {
        String[] dims = size.split("x");
        int width = Integer.parseInt(dims[0]); // genislik piksel degeri
        int height = Integer.parseInt(dims[1]); // yukseklik piksel degeri

        OpenAiImageOptions options = OpenAiImageOptions.builder()
            .model("dall-e-3")
            .width(width)
            .height(height)
            .quality(quality)
            .style(style)
            .build();

        ImagePrompt imagePrompt = new ImagePrompt(prompt, options); // Metin + options birlesimi

        ImageResponse response = imageModel.call(imagePrompt); // Senkron cagri - gorsel hazir olunca doner

        String imageUrl = response.getResult().getOutput().getUrl(); // Uretilen gorsel URL'i 1 saat gecerli

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("prompt", prompt);
        result.put("imageUrl", imageUrl);
        result.put("size", size);
        result.put("quality", quality);

        return result;
    }


    @PostMapping(value = "/multimodal-indexle", consumes = "multipart/form-data")
    public Map<String, Object> multimodalIndexle(
        @RequestParam("image") MultipartFile image,
        @RequestParam(defaultValue = "Genel gorsel") String source
    ) {
        // ADIM 1 - Dosyayı Media nesnesine cevirme
        MimeType mimeType = resolveMimeType(image);
        Media media = new Media(mimeType, image.getResource());

        // ADIM 2 - Gorsel Analiz
        String aciklama = chatClient.prompt()
            .user(u -> u
                .text("Bu gorseli detaylica Turkce olarak acikla."
                    + "Icerik, renkler, kompozisyon ve dikkat ceken unsurlar dahil.")
                .media(media))
            .call()
            .content();

        // ADIM 3 - Acıklamayı Document'a sar ve metadata ekle
        String docId = UUID.randomUUID().toString();
        Document doc = new Document(
            docId,
            aciklama,
            Map.of(
                "source", source,
                "fileName", image.getOriginalFilename()
            )
        );

        // ADIM 4 - VectorStore'a ekle
        vectorStore.add(List.of(doc));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", image.getOriginalFilename());
        result.put("source", source);
        result.put("generatedDescription", aciklama);
        result.put("indexedDocumentId", docId);                // VectorStore'daki belge ID'si
        return result;
    }


    @GetMapping("/multimodal-rag")
    public Map<String, Object> multimodalRag(
        @RequestParam String question
    ) {
        List<Document> found = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(question)
                .topK(5)
                .similarityThreshold(0.5)
                .build()
        );

        List<Map<String, Object>> results = found.stream()
            .map(d -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("score", d.getScore());
                item.put("text", d.getText());
                item.put("metadata", d.getMetadata());
                return item;
            })
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);
        result.put("resultCount", found.size());
        result.put("results", results);
        return result;

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