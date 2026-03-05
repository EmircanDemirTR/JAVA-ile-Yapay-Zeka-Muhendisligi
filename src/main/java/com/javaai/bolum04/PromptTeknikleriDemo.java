package com.javaai.bolum04;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("api/b43")

// Zero-Shot: Öğrenciye direkt soru verilir
// Few-Shot: Önce 2-3 örnek çözüm gösterilir.
// CoT (Chain-Of-Thought): "Adım adım düşünerek çöz" talimatı verilir.
public class PromptTeknikleriDemo {


    private static final Logger log = LoggerFactory.getLogger(PromptTeknikleriDemo.class);


    // Zero-Shot
    private static final String ZERO_SHOT_TEMPLATE = """
        Asagidaki destek mesajının niyetini belirle.
        Sadece su etiketlerden birini don:
        fatura, teknik, hesap, genel.
        
        Mesaj: "{mesaj}
        Etiket:
        """;

    // Few-Shot
    private static final String FEW_SHOT_TEMPLATE = """
        Aşağıdaki örnekleri incele:
        
        Mesaj: "Bu ay faturam iki kat geldi, detay istiyorum."
        Etiket: fatura
        
        Mesaj: "Uygulama acilmiyor, surekli hata veriyor."
        Etiket: teknik
        
        Mesaj: "Sifremi unuttum, hesabima giremiyorum."
        Etiket: hesap
        
        Mesaj: "Kampanyalar hakkinda bilgi verir misiniz?"
        Etiket: genel
        
        Şimdi bu mesajı sınıflandır:
        Mesaj: "{mesaj}
        Etiket:
        """;

    // CoT (Chain-Of-Thought)
    private static final String COT_TEMPLATE = """
        Asagidaki problemi adim adim coz.
        Cevabi su formatta ver:
        1) Verilenler
        2) Islem
        3) Sonuc
        
        Problem:
        {problem}
        """;

    private final ChatClient chatClient;

    public PromptTeknikleriDemo(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(
            "Sen bir egitmen asistanisin. Yanitlari net ve kisa ver. Turkce olsun.").build();
    }

    public static void main(String[] args) {
        SpringApplication.run(PromptTeknikleriDemo.class, args);
    }

    // Temel Kullanım
    @GetMapping("/intent/zero-shot")
    public Map<String, Object> zeroShot(@RequestParam String mesaj) {
        validateText(mesaj, "mesaj");
        PromptTemplate template = new PromptTemplate(ZERO_SHOT_TEMPLATE); // Şablonu parse et
        Prompt promt = template.create(Map.of("mesaj", mesaj)); // Mesaj placeholder'ını doldur

        long start = System.currentTimeMillis();
        String response = chatClient
            .prompt(promt)
            .call()
            .content();
        long latency = System.currentTimeMillis() - start;

        return Map.of(
            "teknik", "zero-shot",
            "mesaj", mesaj,
            "etiket", response.trim(),
            "lantecy", latency
        );
    }

    @GetMapping("/intent/few-shot")
    public Map<String, Object> fewShot(@RequestParam String mesaj) {
        validateText(mesaj, "mesaj");
        PromptTemplate template = new PromptTemplate(FEW_SHOT_TEMPLATE); // Örnekli şablonu parse et
        Prompt promt = template.create(Map.of("mesaj", mesaj)); // Gerçek mesajı yerleştir.

        long start = System.currentTimeMillis();
        String response = chatClient
            .prompt(promt)
            .call()
            .content();
        long latency = System.currentTimeMillis() - start;

        return Map.of(
            "teknik", "few-shot",
            "mesaj", mesaj,
            "etiket", response.trim(),
            "lantecy", latency
        );
    }

    // Gelişmiş Kullanım
    @GetMapping("/math/cot")
    public Map<String, Object> chainOfThought(@RequestParam String problem) {
        validateText(problem, "problem");
        PromptTemplate template = new PromptTemplate(COT_TEMPLATE); // CoT şablonunu parse et
        Prompt promt = template.create(Map.of("problem", problem)); // Gerçek mesajı yerleştir.

        long start = System.currentTimeMillis();
        String response = chatClient
            .prompt(promt)
            .call()
            .content();
        long latency = System.currentTimeMillis() - start;

        return Map.of(
            "teknik", "chain-of-thought",
            "problem", problem,
            "etiket", response,
            "lantecy", latency
        );
    }

    private void validateText(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " parametresi bos olamaz");
        }
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleError(Exception ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "bilinmeyen hata";
        log.error("Hata", message);
        return Map.of("error", message);
    }


}
