package com.javaai.bolum06;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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
@RequestMapping("/api/b62")

public class IlkToolDemo {

    private static final Logger log = LoggerFactory.getLogger(FunctionCallingPrensibiDemo.class);

    // chatClient: prompt-level tools(...) baglama stratejisini gostermek icin kullanilir.
    // Her istekte endpoint kodunda hangi tool'larin aktif olacagini .tools(...) ile belirtiyoruz.
    private final ChatClient chatClient;

    // chatClientWithDefaultTool: builder-level defaultTools(...) stratejisini gostermek icin.
    // Tool builder asamasinda eklendiginden her prompt'ta otomatik olarak aktif olur.
    private final ChatClient chatClientWithDefaultTool;

    // weatherToolService: @Tool annotation ile isaretlenmis ilk arac sinifimiz.
    // Bu servis sehir ismi alip hava riski ve yolculuk tavsiyesi donduruyor (simulasyon verisi).
    private final WeatherToolService weatherToolService;

    public IlkToolDemo(ChatClient.Builder builder) {
        this.weatherToolService = new WeatherToolService();

        // Birinci ChatClient: prompt-level tools stratejisi
        this.chatClient = builder
            .clone()
            .defaultSystem("""
                Sen bir seyahat asistanisin.
                Gerektiginde tool kullan, sonucu net ve kisa olarak Turkce acikla.
                """)
            .build();

        // İkinci ChatClient: builder-level defaultTools stratejisi
        this.chatClientWithDefaultTool = builder
            .clone()
            .defaultSystem("""
                Sen bir seyahat asistanisin. Sonucu net ve kisa olarak Turkce acikla
                """)
            .defaultTools(weatherToolService)
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(IlkToolDemo.class, args);
    }

    // Weather Advice -- prompt level
    @GetMapping("/weather-advice")
    public Map<String, Object> weatherAdvice(
        @RequestParam(defaultValue = "istanbul") String city
    ) {
        String modelAnswer = chatClient.prompt()
            .tools(weatherToolService)
            .user(String.format("""
                %s sehri icin kisa bir gunluk plan tavsiyesi ver.
                Gerekliyse sehir_hava_tavsiyesi tool'unu cagir.
                """, city.trim()))
            .call().content();

        return Map.of(
            "city", city.trim(),
            "modelAnswer", modelAnswer
        );
    }


    // Weather Advice -- Builder Level Default
    @GetMapping("/weather-advice-default")
    public Map<String, Object> weatherAdviceDefault(
        @RequestParam(defaultValue = "istanbul") String city
    ) {
        String modelAnswer = chatClientWithDefaultTool.prompt()
            .user(String.format("""
                %s sehri icin kisa bir gunluk plan tavsiyesi ver.
                Gerekliyse sehir_hava_tavsiyesi tool'unu cagir.
                """, city.trim()))
            .call().content();

        return Map.of(
            "city", city.trim(),
            "modelAnswer", modelAnswer
        );
    }


    // Tool Servisi
    static class WeatherToolService {

        // İlk Tool Aracimiz -- cityWeatherAdvice
        @Tool(
            name = "sehir_hava_tavsiyesi",
            description = "Sehir icin hava riski ve tavsiye dondurur"
        )
        public Map<String, Object> cityWeatherAdvice(
            @ToolParam(description = "Sehir adi") String city
        ) {
            String normalized = city == null ? "" : city.trim().toLowerCase();

            String risk = "DUSUK"; // Varsayılan risk
            String advice = "Normal planla devam edebilirsin"; // Varsayılan tavsiye

            if (normalized.contains("erzurum") || normalized.contains("kars")) {
                risk = "YUKSEK";
                advice = "Soguk hava ekipmanı ve gecikme payı planla";
            } else if (normalized.contains("istanbul") || normalized.contains("ordu")) {
                risk = "ORTA";
                advice = "Yagis ihtimaline karsi alternatif plan hazirla";
            }

            return Map.of(
                "city", city.trim(),
                "risk", risk,
                "advice", advice
            );
        }

    }

}
