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
@RequestMapping("api/b41")

public class PrompTemplateDemo {

    private static final Logger log = LoggerFactory.getLogger(PrompTemplateDemo.class);

    private static final String DYNAMIC_TEMPLATE = """
        {konu} hakkinda {dil} dilinde {uzunluk} paragraflik bir aciklama yaz.""";

    // System katmani "nasil konus" kuralini tanimlar, user katmani ise "neyi yap" gorevini verir.
    private static final String SYSTEM_TEMPLATE = """
        Sen bir {rol} uzmanisin. Yanitlarini {format} formatinda ver. Dil: Turkce.""";

    private static final String USER_TEMPLATE = """
        Asagidaki soruyu yanitla:
        {soru}
        Cevapta en az 3 madde olsun.""";

    // System: Politika // Rol, ton, format
    // User: Görev // Soru

    private final ChatClient chatClient;

    public PrompTemplateDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("Sen bir bilgisayar muhendisisin. Net, kisa ve aciklayici ol.")
            .build();
    }


    public static void main(String[] args) {
        SpringApplication.run(PrompTemplateDemo.class, args);
    }

    // Temel Kullanım
    @GetMapping("/dynamic")
    public Map<String, Object> dynamicPrompt(
        @RequestParam(required = false) String konu,
        @RequestParam(required = false) String dil,
        @RequestParam(required = false) String uzunluk
    ) {
        String effectiveKonu = konu == null ? "Prompt Mühendisligi" : konu;
        String effectiveDil = dil == null ? "Turkce" : dil;
        String effectiveUzunluk = uzunluk == null ? "2" : uzunluk;

        validateText(effectiveKonu, "konu");
        validateText(effectiveDil, "dil");
        validateText(effectiveUzunluk, "uzunluk");

        PromptTemplate template = new PromptTemplate(DYNAMIC_TEMPLATE);
        Prompt prompt = template.create(Map.of(
            "konu", effectiveKonu,
            "dil", effectiveDil,
            "uzunluk", effectiveUzunluk
        ));

        String response = chatClient
            .prompt(prompt)
            .call()
            .content();

        return Map.of(
            "konu", effectiveKonu,
            "dil", effectiveDil,
            "uzunluk", effectiveUzunluk,
            "yanit", response
        );
    }

    // Gelişmiş Kullanım
    @GetMapping("/system-user")
    public Map<String, Object> systemAndUserPrompt(
        @RequestParam(required = false) String rol,
        @RequestParam(required = false) String format,
        @RequestParam(required = false) String soru
    ) {
        String effectiveRol = rol == null ? "Java" : rol;
        String effectiveFormat = format == null ? "madde madde" : format;
        String effectiveSoru = soru == null ? "Spring AI neden onemlidir" : soru;

        validateText(effectiveRol, "rol");
        validateText(effectiveFormat, "format");
        validateText(effectiveSoru, "soru");

        String response = chatClient.prompt()
            .system(s -> s
                .text(SYSTEM_TEMPLATE)
                .param("rol", effectiveRol)
                .param("format", effectiveFormat))
            .user(u -> u
                .text(USER_TEMPLATE)
                .param("soru", effectiveSoru))
            .call()
            .content();

        return Map.of(
            "rol", effectiveRol,
            "format", effectiveFormat,
            "soru", effectiveSoru,
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
