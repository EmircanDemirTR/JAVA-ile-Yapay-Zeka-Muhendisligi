package com.javaai.bolum04;

import java.time.LocalDate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
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
@RequestMapping("api/b44")

public class AdvisorPromptDemo {

    // 1 defaultSystem -- global kurallar -- tüm isteklerin taban davranışı
    // 2 runtime override .system -- istek bazli override
    // 3 advisor zinciri -gozlemlenebilirlik ve pipeline davranisi

    private static final Logger log = LoggerFactory.getLogger(AdvisorPromptDemo.class);

    private static final String CHAIN_USER_TEMPLATE = """
        [KULLANICI CONTEXTİ]
        Bugun: {today}
        Kullanıcı tipi: {kullanici}
        
        [SORU]
        {soru}
        """;

    private final ChatClient chatClient;

    public AdvisorPromptDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem(
                "Sen bir bilgisayar muhendisligi asistanisin. Yanitlari Turkce olarak madde madde ver")
            .defaultAdvisors(new SimpleLoggerAdvisor())
            .build();
    }


    public static void main(String[] args) {
        SpringApplication.run(AdvisorPromptDemo.class, args);
    }

    // Temel Kullanım
    @GetMapping("/default")
    public Map<String, Object> withDefaultSystem(@RequestParam String soru) {
        validateText(soru, "soru");
        String response = chatClient.prompt().user(soru).call().content();

        return Map.of(
            "mod", "default-system",
            "soru", soru,
            "yanit", response
        );
    }

    @GetMapping("/override")
    public Map<String, Object> withRuntimeOverride(
        @RequestParam String soru,
        @RequestParam(defaultValue = "sair") String rol
    ) {
        validateText(soru, "soru");
        validateText(rol, "rol");

        String response = chatClient.prompt()
            .system("Sen bir " + rol + " gibi yanit ver. Cevap dili Turkce olsun")
            .user(soru)
            .call()
            .content();

        return Map.of(
            "mod", "runtime-override",
            "rol", rol,
            "soru", soru,
            "yanit", response
        );

    }

    // Gelişmiş Kullanım
    @GetMapping("/chain")
    public Map<String, Object> withAdvisorChain(
        @RequestParam String soru,
        @RequestParam(defaultValue = "ogrenci") String kullanici
    ) {
        validateText(soru, "soru");
        validateText(kullanici, "kullanici");

        String response = chatClient.prompt()
            .system(s -> s
                .text("Sen bir egitmen asistanisin. Yanitlari basitten zora dogru acikla")
                .param("kullanici", kullanici))
            .user(u -> u
                .text(CHAIN_USER_TEMPLATE)
                .param("today", LocalDate.now().toString())
                .param("kullanici", kullanici)
                .param("soru", soru))
            .advisors(spec -> spec.param("kullaniciTipi", kullanici))
            .advisors(new SimpleLoggerAdvisor())
            .call().content();

        return Map.of(
            "mod", "advisor-chain",
            "kullanici", kullanici,
            "soru", soru,
            "yanit", response
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
