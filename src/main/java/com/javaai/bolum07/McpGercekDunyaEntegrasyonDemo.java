package com.javaai.bolum07;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b75")

public class McpGercekDunyaEntegrasyonDemo {

    private final List<McpSyncClient> mcpClients; // MCP client listesi
    private final ToolCallbackProvider callbackProvider; // Callback havuzu
    private final ChatClient multiServerChatClient; // Orkestrator client
    private final McpSecurityFilter securityFilter; // Path guvenlik

    @Autowired
    public McpGercekDunyaEntegrasyonDemo(ChatClient.Builder builder,
        ObjectProvider<ToolCallbackProvider> callbackProvider,
        List<McpSyncClient> mcpClients) {

        this.mcpClients = mcpClients == null ?
            List.of()
            : List.copyOf(mcpClients);

        this.callbackProvider = callbackProvider.getIfAvailable(
            () -> ToolCallbackProvider.from(new ToolCallback[0])
        );

        this.multiServerChatClient = builder
            .clone()
            .defaultSystem("Sen MCP orkestrasyon asistani bir uzmansın. Gerektiginde birden fazla MCP tool callback'i kullan")
            .build();

        this.securityFilter = new McpSecurityFilter(); // Path güvenlik filtresi oluştur
    }

    McpGercekDunyaEntegrasyonDemo(
        ChatClient multiServerChatClient,
        ToolCallbackProvider callbackProvider,
        List<McpSyncClient> mcpClients
    ) {
        this.mcpClients = mcpClients == null
            ? List.of()
            : List.copyOf(mcpClients);
        this.callbackProvider = callbackProvider == null
            ? ToolCallbackProvider.from(new ToolCallback[0])
            : callbackProvider;
        this.multiServerChatClient = multiServerChatClient;
        this.securityFilter = new McpSecurityFilter();
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai,mcp");
        SpringApplication.run(McpGercekDunyaEntegrasyonDemo.class, args);
    }

    private static void ensureInitalized(McpSyncClient client) {
        if (!client.isInitialized()) {
            client.initialize();
        }
    }

    private Map<String, Object> summarizeServerTools(McpSyncClient client) {

        try {
            ensureInitalized(client);
            McpSchema.ListToolsResult toolsResult = client.listTools(); // MCP Server'dan tool listesi çek
            McpSchema.Implementation serverInfo = client.getServerInfo(); // Server metadata bilgisini al

            List<Map<String, String>> tools = toolsResult.tools().stream()
                .map(tool -> Map.of(
                    "name", tool.name(),
                    "description", tool.description() == null ? "" : tool.description()
                ))
                .toList();

            return Map.of(
                "serverName", serverInfo.name(),
                "serverVersion", serverInfo.version(),
                "toolCount", tools.size(),
                "tools", tools
            );

        } catch (Exception e) {
            return Map.of(
                "serverName", "unknown",
                "serverVersion", "unknown",
                "toolCount", 0,
                "error", e.getMessage()
            );
        }

    }

    @GetMapping("/multi-server-tools")
    public Map<String, Object> getMultiServerTools() {
        List<Map<String, Object>> serverSummaries = mcpClients.stream()
            .map(this::summarizeServerTools)
            .toList();

        int totalTools = serverSummaries.stream()
            .mapToInt(server -> (Integer) server.getOrDefault("totalCount", 0))
            .sum();

        return Map.of(
            "totalServers", mcpClients.size(),
            "totalTools", totalTools,
            "servers", serverSummaries
        );
    }

    @GetMapping("/task")
    public Map<String, Object> executeCompositeTask(
        @RequestParam(defaultValue = "Proje dosyalarini listele ve en onemli iki dosyayı ozetle") String prompt
    ) {
        String answer = multiServerChatClient
            .prompt()
            .user(prompt)
            .call()
            .content();

        List<String> activeCallbacks = List.of(callbackProvider.getToolCallbacks()).stream()
            .map(callback -> callback.getToolDefinition().name())
            .toList();

        return Map.of(
            "prompt", prompt,
            "activeCallbackCount", activeCallbacks.size(),
            "activeCallbacks", activeCallbacks,
            "answer", answer
        );
    }

    @GetMapping("/security-check")
    public ResponseEntity<Map<String, Object>> checkPathSecurity(
        @RequestParam(defaultValue = "/project/README.md") String path
    ) {
        boolean allowed = securityFilter.isPathAllowed(path);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("path", path);
        body.put("allowed", allowed);

        if (!allowed) {
            body.put("message", securityFilter.violationMessage(path));
            body.put("explanation", "Path whitelist dışında veya blocked listede");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        body.put("message", "Erisim izni verildi");
        body.put("explanation", "Path whitelist icinde ve blocked listede degil");
        return ResponseEntity.ok(body);
    }

    static class McpSecurityFilter {

        private static final Set<String> ALLOWED_PATHS = Set.of(
            "/project", "/docs", "/public"
        );
        private static final Set<String> BLOCKED_PATHS = Set.of(
            "/etc", "/root", "/windows/system32", "/.env", "/secrets"
        );

        boolean isPathAllowed(String path) {
            String normalized = path.toLowerCase().replace("\\", "/");
            if (BLOCKED_PATHS.stream().anyMatch(normalized::startsWith)) {
                return false;
            }
            return ALLOWED_PATHS.stream().anyMatch(normalized::startsWith);
        }

        String violationMessage(String path) {
            return String.format(
                "Guvenlik ihlali: '%s' pathi engellendi. İzin verilen pathler: %s", path, ALLOWED_PATHS
            );
        }
    }
}
