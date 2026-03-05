package com.javaai.bolum07;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
@RequestMapping("/api/b72")

public class McpClientBaglantiDemo {
    // listTools() ile keşif, callTool() ile eylem

    // VM options: -Dspring.profiles.active=mcp


    private final List<McpSyncClient> mcpClients;

    public McpClientBaglantiDemo(List<McpSyncClient> mcpClients) {
        this.mcpClients = mcpClients == null ? List.of() : List.copyOf(mcpClients);
    }

    private static void ensureInitialized(McpSyncClient client) {
        if (!client.isInitialized()) {
            client.initialize();
        }
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai,mcp");
        SpringApplication.run(McpClientBaglantiDemo.class, args);
    }

    @GetMapping("/tools")
    public Map<String, Object> listTools(@RequestParam(defaultValue = "0") int clientIndex) {
        Optional<McpSyncClient> selected = selectClient(clientIndex); // Index'e göre client sec.
        if (selected.isEmpty()) {
            return Map.of(
                "connected", false,
                "reason", "Aktif MCP client bulunamadi. mcp profili ve mcp yml ayarlarını kontrol edin",
                "clientCount", mcpClients.size()
            );
        }

        McpSyncClient client = selected.get(); // Seclien MCP client
        ensureInitialized(client); // Handshake kontrolü

        McpSchema.ListToolsResult listToolsResult = client.listTools();
        List<Map<String, String>> tools = listToolsResult.tools().stream() // Tool listesini donustur
            .map(tool -> Map.of(
                "name", tool.name(),
                "title", tool.title(),
                "description", tool.description(),
                "inputSchema", String.valueOf(tool.inputSchema())
            ))
            .toList();

        return Map.of(
            "connected", true,
            "clientIndex", clientIndex,
            "clientCount", mcpClients.size(),
            "toolCount", tools.size(),
            "tools", tools
        );
    }

    // Katalogdan bir tool'u parametre ile cagirmak
    @GetMapping("/call-tool")
    public Map<String, Object> callTool(
        @RequestParam String toolName,
        @RequestParam(defaultValue = "path") String argumentKey,
        @RequestParam(defaultValue = "/") String arg,
        @RequestParam(defaultValue = "0") int clientIndex
    ) {
        Optional<McpSyncClient> selected = selectClient(clientIndex);
        if (selected.isEmpty()) {
            return Map.of(
                "connected", false,
                "reason", "Aktif MCP client bulunamadi. mcp profili ve mcp yml ayarlarını kontrol edin",
                "clientIndex", clientIndex
            );
        }

        McpSyncClient client = selected.get(); // Seclien MCP client
        ensureInitialized(client); // Handshake kontrolü

        try {

            McpSchema.CallToolRequest request = new CallToolRequest( // MCP tool cagrisi icin istek nesnesi olustur
                toolName,
                Map.of(argumentKey, arg) // parametreler, key-value çift olarak tool'a gönderilir
            );

            McpSchema.CallToolResult result = client.callTool(request); // MCP callTool cagrisi
            List<String> contents = result.content().stream()
                .map(String::valueOf) // Her content objesini String'e çevir - method reference kullanımı
                .toList();

            return Map.of(
                "connected", true,
                "clientIndex", clientIndex,
                "toolName", toolName,
                "contentCount", contents.size(),
                "content", contents
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private Optional<McpSyncClient> selectClient(int clientIndex) {
        if (mcpClients.isEmpty()) {
            return Optional.empty();
        }
        if (clientIndex < 0 || clientIndex >= mcpClients.size()) {
            return Optional.empty();
        }
        return Optional.of(mcpClients.get(clientIndex));
    }
}
