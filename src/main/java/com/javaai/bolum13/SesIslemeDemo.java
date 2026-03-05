package com.javaai.bolum13;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Profile("openai")
@EnableAutoConfiguration
@RestController
@RequestMapping(value = "/api/b133", produces = "application/json;charset=UTF-8")

public class SesIslemeDemo {

    private final ChatClient chatClient;
    private final OpenAiAudioSpeechModel speechModel; // Metin to ses
    private final OpenAiAudioTranscriptionModel transcriptionModel; // ses to metin

    public SesIslemeDemo(
        ChatClient.Builder builder,
        OpenAiAudioSpeechModel speechModel,
        OpenAiAudioTranscriptionModel transcriptionModel
    ) {
        this.chatClient = builder
            .defaultSystem("Sen yardimci bir asistansin. Kisa ve net Turkce cevaplar ver.")
            .build();
        this.speechModel = speechModel;
        this.transcriptionModel = transcriptionModel;
    }


    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai");
        SpringApplication.run(SesIslemeDemo.class, args);
    }

    @GetMapping("/ses-secenekleri") // GET: TTS ses tonu, hiz ve model secenekleri — LLM cagrisi yok
    public Map<String, Object> sesSecenekleri() {
        List<Map<String, String>> voices = List.of(
            Map.of("name", "alloy", "description", "Dengeli, cinsiyet-notr"),         // Varsayilan ses
            Map.of("name", "echo", "description", "Erkek, sakin ve olgun"),           // Olgun, otoriter ton
            Map.of("name", "fable", "description", "Ingiliz aksanli, anlatici"),      // Hikaye anlatan ton
            Map.of("name", "onyx", "description", "Derin ve otoriter erkek sesi"),    // En derin ses
            Map.of("name", "nova", "description", "Genc, enerjik kadin sesi"),        // Dinamik ton
            Map.of("name", "shimmer", "description", "Sicak ve dostane kadin sesi")   // En sicak ton
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lesson", "B13.3");
        result.put("voices", voices);          // 6 ses tonu — /metin-to-ses'te voice parametresi ile kullanilir
        result.put("speedRange", "0.25 - 4.0");
        result.put("models", Map.of(
            "tts-1", "Hizli, dusuk kalite — prototip ve test icin",    // Dusuk gecikme
            "tts-1-hd", "Yavas, yuksek kalite — uretim ve son kullanici icin" // Daha net ses
        ));
        result.put("transcriptionModel", "whisper-1");                         // Tek Whisper modeli
        result.put("supportedLanguages", "tr, en, de, fr, es, it, pt, nl, pl, ru ve 40+ daha"); // ISO 639-1

        return result;
    }

    @GetMapping(value = "/metin-to-ses", produces = "audio/mpeg")
    public ResponseEntity<byte[]> metinToSes(
        @RequestParam String text,
        @RequestParam(defaultValue = "alloy") String voice,
        @RequestParam(defaultValue = "1.0") Double speed
    ) {
        OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
            .model("tts-1-hd")
            .voice(voice)
            .speed(speed)
            .build();

        TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, options);
        TextToSpeechResponse response = speechModel.call(prompt); // OpenAI TTS API'ye senkron istek
        byte[] audioBytes = response.getResult().getOutput();

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .body(audioBytes); // MP3 ses binary verisi, dinlenebilir
    }

    @PostMapping(value = "/ses-to-metin", consumes = "multipart/form-data")
    public Map<String, Object> sesToMetin(
        @RequestParam("audio") MultipartFile audio,
        @RequestParam(defaultValue = "tr") String language
    ) {
        OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
            .language(language)
            .model("whisper-1")
            .build();

        Resource resource = audio.getResource();
        AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource, options);
        AudioTranscriptionResponse response = transcriptionModel.call(prompt);
        String transcript = response.getResult().getOutput(); // Transkripsiyon metni

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transript", transcript);
        return result;
    }


    @PostMapping(value = "/sesli-asistan", consumes = "multipart/form-data")
    public Map<String, Object> sesliAsistan( // Sesli asistan endpoint'i — uc model, tek endpoint, uçtan uca pipeline
        @RequestParam("audio") MultipartFile audio
    ) {
        // ADIM 1: Ses → metin (Whisper) — tercuman ofisine ses kaydi geldi, not alici yaziyor
        OpenAiAudioTranscriptionOptions transcribeOpts = OpenAiAudioTranscriptionOptions.builder()
            .language("tr")        // Turkce ses bekleniyor — dil ipucu Whisper'i hizlandirir
            .model("whisper-1")    // Tek Whisper modeli
            .build();              // Immutable options

        AudioTranscriptionPrompt transcribePrompt =
            new AudioTranscriptionPrompt(audio.getResource(), transcribeOpts); // Ses + options → prompt
        String transcribedText = transcriptionModel                                 // Whisper'a istek
            .call(transcribePrompt)                                             // Senkron transkripsiyon
            .getResult()                                                        // AudioTranscriptionResult
            .getOutput();                                                       // Transkripsiyon metni

        // ADIM 2: Metin → LLM cevabi — uzman danisan soruyu anladi, cevap uretecek
        String llmAnswer = chatClient.prompt()  // Yeni prompt zinciri
            .user(transcribedText)          // Whisper'dan gelen metin kullanici sorusu olarak iletiliyor
            .call()                         // GPT-4o'ya senkron istek
            .content();                     // LLM cevap metni

        // ADIM 3: Cevap metni → ses (TTS) — dil ogretmeni cevabi sesli okuyor
        OpenAiAudioSpeechOptions ttsOpts = OpenAiAudioSpeechOptions.builder()
            .model("tts-1-hd")        // Kaliteli ses: kullaniciya sunulan cevap net olsun
            .voice("nova")            // Nova: genc, enerjik kadin sesi — asistan tonu icin uygun
            .speed(1.0)               // Normal hiz: anlasilir konusma temposu
            .build();                 // Immutable options

        byte[] audioBytes = speechModel
            .call(new TextToSpeechPrompt(llmAnswer, ttsOpts)) // LLM cevabini TTS'e gonder
            .getResult()                                        // AudioSpeechResponse result
            .getOutput();                                       // MP3 binary veri

        // Base64: JSON icinde binary veri tasimak icin — tarayici audioBase64'u decode edip oynatabilir
        String audioBase64 = Base64.getEncoder()
            .encodeToString(audioBytes); // byte[] → Base64 String: JSON icinde binary tasimak icin — direkt byte[] JSON'a girmez

        Map<String, Object> result = new LinkedHashMap<>(); // LinkedHashMap: pipeline adim sirasi korunur

        result.put("transcribedText", transcribedText); // ADIM 1 ciktisi: Whisper'in yaziya cevirdigi soru
        result.put("llmAnswer", llmAnswer);             // ADIM 2 ciktisi: GPT-4o'nun metin cevabi
        result.put("audioBase64", audioBase64);         // ADIM 3 ciktisi: MP3 ses Base64 kodlanmis
        result.put("audioFormat", "mp3");               // Ses formati — decode ederken bunu kullan
        return result; // JSON: lesson, transcribedText (ADIM1), llmAnswer (ADIM2), audioBase64 (ADIM3), audioFormat
    }


}