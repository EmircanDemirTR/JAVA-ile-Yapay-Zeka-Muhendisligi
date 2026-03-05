package com.javaai.bolum07;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
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
@RequestMapping("/api/b74")

public class McpToolEntegrasyonDemo {

    private final ChatClient mcpOnlyChatClient; // Sadece MCP callbackleri ile calisan client
    private final ChatClient hybridChatClient; // MCP + local tool birlesik client
    private final ToolCallbackProvider mcpToolCallbackProvider; // MCP tool callback provider
    private final LocalUtilityTools localUtilityTools; // Yerel utility tool sınıfı

    public McpToolEntegrasyonDemo(ChatClient.Builder builder, ObjectProvider<ToolCallbackProvider> callbackProvider) {
        // Same model, different tool topology
        this.localUtilityTools = new LocalUtilityTools();

        this.mcpToolCallbackProvider = callbackProvider.getIfAvailable( // Spring contextten MCP callback provider al
            () -> ToolCallbackProvider.from(new ToolCallback[0])
        );

        this.mcpOnlyChatClient = builder
            .clone()
            .defaultSystem("Sen MCP odakli bir asistansin. Yalnizca MCP tool callback'lerini kullan")
            .defaultToolCallbacks(this.mcpToolCallbackProvider)
            .build();

        this.hybridChatClient = builder
            .clone()
            .defaultSystem("Sen hibrit bir asistansin. MCP callback ve yerel @Tool fonksiyonlarını birlikte kullan")
            .defaultToolCallbacks(this.mcpToolCallbackProvider)
            .defaultTools(this.localUtilityTools)
            .build();
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai,mcp");
        SpringApplication.run(McpToolEntegrasyonDemo.class, args);
    }

    @GetMapping("/ask")
    public Map<String, Object> ask(@RequestParam(defaultValue = "Proje klasorundeki dosyalari listele") String message) {
        String answer = mcpOnlyChatClient
            .prompt()
            .user(message)
            .call()
            .content();

        return Map.of(
            "mode", "MCP_ONLY",
            "message", message,
            "answer", answer
        );
    }

    @GetMapping("/hybrid")
    public Map<String, Object> hybrid(@RequestParam(defaultValue = "Bugunun tarihi nedir ve proje klasorundeki dosyalari listele") String message) {
        String answer = hybridChatClient
            .prompt()
            .user(message)
            .call()
            .content();

        return Map.of(
            "mode", "HYBRID MCP+LOCAL",
            "message", message,
            "answer", answer
        );
    }

    @GetMapping("/active-tools")
    public Map<String, Object> getActiveTools() {
        ToolCallback[] callbacks = mcpToolCallbackProvider.getToolCallbacks();
        List<Map<String, String>> mcpTools = Arrays.stream(callbacks)
            .map(cb -> Map.of(
                "name", cb.getToolDefinition().name(), // mcp callback ismi
                "description", cb.getToolDefinition().description(),
                "inputSchema", cb.getToolDefinition().inputSchema()
            ))
            .toList();

        List<Map<String, String>> localTools = extractLocalToolMetadata(LocalUtilityTools.class);

        return Map.of(
            "mcpToolCount", mcpTools.size(),
            "localToolCount", localTools.size(),
            "totalCount", mcpTools.size() + localTools.size(),
            "mcpTools", mcpTools,
            "localTools", localTools
        );
    }

    private List<Map<String, String>> extractLocalToolMetadata(Class<?> toolClass) {
        Method[] methods = toolClass.getDeclaredMethods();  // Sinifin tanimlanan tum metotlarini al — reflection API kullanimi
        return Arrays.stream(methods)
            .filter(method -> method.isAnnotationPresent(Tool.class))
            .map(method -> {  // Her @Tool metodu icin Map olustur
                Tool annotation = method.getAnnotation(Tool.class);  // @Tool annotation objesini al
                return Map.of(
                    "name", annotation.name(),
                    "description", annotation.description(),
                    "methodName", method.getName()
                );
            })
            .collect(Collectors.toList());
    }

    static class LocalUtilityTools {

        @Tool(name = "bugunun_tarihi", description = "Bugunun tarih ve saat bilgisini ver")
        public String todayDateTime() {
            return "Simdiki tarih-saat: " + LocalDateTime.now();
        }

        @Tool(name = "gun_farki_hesapla", description = "İki tarih arasındaki gun farkini hesaplar")
        public String dayDiff(
            @ToolParam(description = "Baslangic tarihi, format: yyyy-MM-dd") String startDate,
            @ToolParam(description = "Bitis tarihi, format: yyyy-MM-dd") String endDate
        ) {
            LocalDate start = LocalDate.parse(startDate);  // String tarih yyyy-MM-dd formatinda LocalDate'e cevir
            LocalDate end = LocalDate.parse(endDate);  // Bitis tarihini parse et
            long days = end.toEpochDay() - start.toEpochDay();
            return String.format("%s ile %s arasinda %d gun var.", startDate, endDate, days);
        }
    }
}
