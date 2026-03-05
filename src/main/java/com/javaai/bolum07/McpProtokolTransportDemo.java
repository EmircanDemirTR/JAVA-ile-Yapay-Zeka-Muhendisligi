package com.javaai.bolum07;

import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b71")

public class McpProtokolTransportDemo {

    // İki transport yöntemi: 1- Stdio (yerel, hızlı), 2- SSE (uzak, HTTP tabanlı)
    // STDIO = Dogrudan proses arası iletişim, SSE = HTTP üzerinden event stream

    private final ChatClient chatClient;

    public McpProtokolTransportDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("""
                Sen MCP uzmanı bir asistansin.
                MCP ile Function Calling arasındaki farkları, transport yontemlerini ve MCP yetenklerini (Tools, Resources, Prompts) acik ve orneklerle
                anlatırsın .Teknik terimleri basitleştirirsin, günlük hayat anolojileri kullanırsın.
                Cevapların kısa ve öz olmalı. Maksimum 4-5 cümle. Cevaplar Türkçe olsun.
                """)
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(McpProtokolTransportDemo.class, args);
    }

    @GetMapping("/compare")
    public Map<String, Object> compareMcpAndFunctionCalling(
        @RequestParam(defaultValue = "MCP ile Function Calling arasındaki farklar nelerdir") String message
    ) {
        String answer = chatClient
            .prompt()
            .user(message)
            .call()
            .content();

        return Map.of(
            "message", message,
            "answer", answer
        );
    }

    // Transport Seçimi
    @GetMapping("/transport-guide")
    public Map<String, Object> getTransportGuide(
        @RequestParam(defaultValue = "local") String target
    ) {
        return switch (target.toLowerCase()) {
            case "local" -> Map.of(
                "target", "local",
                "recommended", "STDIO",
                "reason", """
                    STDIO (Standart Input / Output) - dogrudan proses arasi iletisim
                    Yerel MCP serverlar icin ideal: dusuk gecikme, HTTP overhead'i yok.
                    Ornek: npx ile yerel filesystem server çalıştırma
                    Boru hattı analojisi: İki bina arasında doğrudan boru - su aninda gecer
                    """
            );
            case "remote" -> Map.of(
                "target", "remote",
                "recommended", "SSE (Server-Sent Events)",
                "reason", """
                    SSE - HTTP uzerinden event stream.
                    Uzak MCP Servlerlar icin ugundur: Firewall-friendly, TLS destekli.
                    Ornek: Bulutta cailsan MCP service'e baglanmak.
                    Posta servisi analojisi: Uzak sehre paket gondermek - standart taşıma protokolü gerekir.
                    """
            );
            default -> throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                String.format("Gecersiz target: %s", target)
            );
        };
    }

    // MCP yetenekleri
    @GetMapping("capabilities")
    public Map<String, Object> getMcpCapabilities() {

        return Map.of(
            "lesson", "B7.1 — MCP Yetenekleri",  // Ders kimlik bilgisi
            "capabilities", Map.of(  // Uc temel MCP capability alani

                "Tools", """
                    Arac cagirma — Function Calling benzeri ama standart.
                    Ornek: list_files, read_file, search_web.
                    Model tool'u secer, MCP client cagriyi yapar.""",  // Tool capability aciklamasi

                "Resources", """
                    Veri okuma — dosya, veritabani, API.
                    Ornek: file:///project/README.md kaynagi okuma.
                    MCP server bu kaynaklari saglar, client erisir.""",  // Resource capability aciklamasi

                "Prompts", """
                    Sablon yonetimi — onceden tanimli prompt'lar.
                    Ornek: code-review, summarize gibi sablonlar.
                    Server'da hazir, client cagirinca doldurulur."""  // Prompt capability aciklamasi
            ),
            "note", "Bu ders MCP teorisi — B7.2'de client kodunu yazacagiz"  // Sonraki derse kopru
        );

    }
}
