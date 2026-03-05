package com.javaai.bolum14;

import jakarta.servlet.http.HttpServletResponse;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping(value = "/api/b141", produces = "application/json;charset=UTF-8")

public class PromptInjectionPiiDemo {

    // PII REGEX KALIPLARI
    private static final Pattern TC_PATTERN =
        Pattern.compile("\\b[1-9]\\d{10}\\b"); // TC Kimlik No — 11 rakam, ilk hane 0 olamaz
    private static final Pattern PHONE_PATTERN =
        Pattern.compile("\\b0?5\\d{2}[\\s-]?\\d{3}[\\s-]?\\d{2}[\\s-]?\\d{2}\\b"); // 05XX XXX XX XX
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"); // RFC 5321 uyumlu email
    private static final Pattern IBAN_PATTERN =
        Pattern.compile("\\bTR\\d{24}\\b"); // TR ile baslayan 26 karakterlik IBAN

    // Bilinen injection ifadeleri — kucuk harfe donusturulmus input ile karsilastirilir.
    private static final List<String> INJECTION_PATTERNS = List.of(
        "ignore previous",
        "ignore all",
        "forget your instructions",
        "disregard above",
        "override system",
        "reveal system prompt",
        "onceki talimatlari unut",
        "onceki komutlari yok say",
        "talimatlari yok say",
        "sistem promptunu goster",
        "system promptu goster",
        "kurallarini unut",
        "rol degistir"
    );


    private final ChatClient secureClient; // InjectionDetector + SafeGuardAdvisor zincirli client
    private final ChatClient plainClient;  // Advisor'suz sade client — karsilastirma icin


    public PromptInjectionPiiDemo(ChatClient.Builder builder) {
        InjectionDetectorAdvisor injectionAdvisor = new InjectionDetectorAdvisor();
        SafeGuardAdvisor safeGuard = new SafeGuardAdvisor(
            List.of("hack", "exploit", "bypass security", "sifre kir", "sisteme siz"), // Yasakli konular listesi
            "Bu konu hakkinda yardimci olamam. Guvenlik politikasi geregi engellendi.", // Kullaniciya gosterilecek mesaj
            1 // Advisor zinciri sirasi — 0 = injection'dan sonra calisir
        );
        this.secureClient = builder.clone()
            .defaultSystem("Sen guvenli bir AI asistansin. Hassas bilgi paylasma.")
            .defaultAdvisors(injectionAdvisor, safeGuard)       // Zincir: injection -> safeguard -> LLM
            .build();
        this.plainClient = builder.clone()
            .defaultSystem("Sen yardimci bir asistansin.")
            .build();
    }


    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai");
        SpringApplication.run(PromptInjectionPiiDemo.class, args);
    }


    // Turkce/ingilizce karisik metinlerde aksan farkini kaldirip injection karsilastirmasini tutarli hale getirir.
    private static String normalizeForSecurity(String text) {
        String normalized = text != null ? text : ""; // Null input gelirse bos stringe dusur
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD); // Harf + aksan isaretlerini ayir
        normalized = normalized.replaceAll("\\p{M}+", ""); // Aksan isaretlerini temizle: ö -> o, ş -> s
        normalized = normalized.toLowerCase(Locale.ROOT); // Locale bagimsiz kucuk harf
        normalized = normalized.replace('ı', 'i'); // Turkce dotless-i varyantini tek forma cek
        normalized = normalized.replaceAll("[^a-z0-9\\s]", " "); // Noktalama/isaret karakterlerini bosluk yap
        normalized = normalized.replaceAll("\\s+", " ").trim(); // Coklu bosluklari tekilleştir
        return normalized; // Pattern taramasina hazir normalize metin
    }


    @PostMapping("/sanitize")
    public Map<String, Object> sanitize(@RequestBody Map<String, String> body,
        HttpServletResponse httpResponse) { // HTTP status icin
        String text = body.getOrDefault("text", "");

        List<String> maskedTypes = new ArrayList<>(); // Hangi PII turleri maskelendi — sonuca eklenir
        String sanitized = text;                      // Her adim uzerine yazarak ilerler
        if (PHONE_PATTERN.matcher(sanitized).find()) {                                       // Telefon var mi?
            sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("[TELEFON-MASKELENDI]"); // Maskele
            maskedTypes.add("Telefon");                                                      // Tip kaydet
        }
        if (TC_PATTERN.matcher(sanitized).find()) {
            sanitized = TC_PATTERN.matcher(sanitized).replaceAll("[TC-KIMLIK-MASKELENDI]");
            maskedTypes.add("TC Kimlik No");
        }
        if (EMAIL_PATTERN.matcher(sanitized).find()) {
            sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[EMAIL-MASKELENDI]");
            maskedTypes.add("E-posta");
        }
        if (IBAN_PATTERN.matcher(sanitized).find()) {
            sanitized = IBAN_PATTERN.matcher(sanitized).replaceAll("[IBAN-MASKELENDI]");
            maskedTypes.add("IBAN");
        }
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("original", text);                                 // Gelen ham metin
        response.put("sanitized", sanitized);                           // Maskelenmis cikti
        response.put("maskedFieldCount", maskedTypes.size());           // Kac alan maskelendi
        response.put("maskedTypes", maskedTypes);                       // Maskelenen PII turleri listesi
        return response;
    }

    // Uc katmanli guvenlik zincirini LLM'e bagliyoruz:
    // Once maskPii() ile PII temizlenir, sonra advisor zinciri (injection -> safeguard) devreye girer.
    @PostMapping("/safe-chat")
    public Map<String, Object> safeChat(@RequestBody Map<String, String> body,
        HttpServletResponse httpResponse) { // HTTP status icin

        String message = body.getOrDefault("message", ""); // Kullanici mesajini al
        if (message.isBlank()) {                           // Bos mesaj kontrolu
            httpResponse.setStatus(400);                    // HTTP 400 Bad Request — bos mesaj gecersiz
            LinkedHashMap<String, Object> error = new LinkedHashMap<>();
            error.put("lesson", "B14.1");
            error.put("error", "Mesaj bos olamaz");
            return error;
        }

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();

        try {
            String sanitized = maskPii(message); // 1. katman: PII maskele
            String answer = secureClient.prompt()
                .user(sanitized)
                .call()                           // 2-3. katman: InjectionDetector -> SafeGuard -> LLM
                .content();
            response.put("answer", answer);
            response.put("inputSanitized", !message.equals(sanitized)); // PII maskeleme gerceklesti mi?
            response.put("blocked", false);                              // Zincir gecildi, engel yok

        } catch (IllegalArgumentException e) {
            response.put("answer", e.getMessage());  // Kullaniciya gonderilen engel mesaji
            response.put("blocked", true);            // Istek engellendi
            response.put("reason", "Injection pattern tespit edildi");
        }
        return response;
    }


    private String maskPii(String text) {
        String result = PHONE_PATTERN.matcher(text).replaceAll("[TEL-MASKELENDI]");
        result = TC_PATTERN.matcher(result).replaceAll("[TC-MASKELENDI]");
        result = EMAIL_PATTERN.matcher(result).replaceAll("[EMAIL-MASKELENDI]");
        result = IBAN_PATTERN.matcher(result).replaceAll("[IBAN-MASKELENDI]");
        return result;
    }


    static class InjectionDetectorAdvisor implements CallAdvisor {

        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            String content = request.prompt().getContents();           // Prompt'taki tum user mesajini al
            String userMessage = normalizeForSecurity(content); // Turkce karakter normalize edilmis guvenlik tarama metni
            for (String pattern : INJECTION_PATTERNS) {    // Her injection kalibini sirayla kontrol et
                if (userMessage.contains(pattern)) {       // Kucuk harfli icerik ile karsilastir
                    throw new IllegalArgumentException(    // LLM'e gitmeden zinciri kes — erken cikis
                        String.format("Prompt injection tespit edildi: '%s' kalibi iceriyor", pattern));
                }
            }
            return chain.nextCall(request); // Injection yok — sonraki advisor'a devam et (SafeGuard)
        }

        @Override
        public String getName() {
            return "InjectionDetectorAdvisor";
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }
}