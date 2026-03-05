package com.javaai.bolum07;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
@RequestMapping("/api/b73")

public class McpServerDemo {

    private final ProductToolService productToolService;
    private final ChatClient chatClient;

    public McpServerDemo(ChatClient.Builder builder) {
        this.productToolService = new ProductToolService();
        this.chatClient = builder
            .clone() // bagimsiz kopya builder
            .defaultSystem("Sen urun asistanisin. Urun sorgularında bagli tool'ları kullan")
            .defaultTools(productToolService)
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(McpServerDemo.class, args);
    }

    @GetMapping("/simulate-client")
    public Map<String, Object> simulateClient(@RequestParam(defaultValue = "laptop ara") String query) {
        String answer = chatClient
            .prompt()
            .user(query)
            .call()
            .content();

        return Map.of(
            "query", query,
            "answer", answer
        );
    }

    @GetMapping("/server-tools")
    public Map<String, Object> getServerTools() {
        Method[] methods = ProductToolService.class.getDeclaredMethods(); // tüm metotları al
        List<Map<String, String>> tools = Arrays.stream(methods)
            .filter(method -> method.isAnnotationPresent(Tool.class)) // sadece @Tool metotları
            .map(method -> {
                Tool tool = method.getAnnotation(Tool.class); // Annotation verisini oku
                return Map.of(
                    "name", tool.name(),
                    "description", tool.description(),
                    "parameters", Arrays.toString(method.getParameters())
                );
            })
            .collect(Collectors.toList()); // Listeye cevir

        return Map.of(
            "toolCount", tools.size(),
            "availableTools", tools
        );
    }

    static class ProductToolService {

        private static final List<Map<String, Object>> PRODUCTS = List.of(
            Map.of("code", "LAPTOP-001", "name", "Gaming Laptop", "price", 45999.99, "category", "Elektronik"), // Urun 1
            Map.of("code", "PHONE-001", "name", "Akilli Telefon", "price", 24999.99, "category", "Elektronik"), // Urun 2
            Map.of("code", "BOOK-001", "name", "Java Programlama", "price", 189.99, "category", "Kitap"), // Urun 3
            Map.of("code", "CHAIR-001", "name", "Ergonomik Sandalye", "price", 8999.99, "category", "Mobilya") // Urun 4
        );

        @Tool(name = "urun_sorgula", description = "Urun adina veya kategoriye gore arama yapar")
        public List<Map<String, Object>> searchProducts(@ToolParam(description = "Aranacak urun adi veya kategori") String query) {
            if (query == null || query.isBlank()) {
                return PRODUCTS;
            }

            String lowered = query.toLowerCase();
            return PRODUCTS.stream()
                .filter(product -> {
                    String name = ((String) product.get("name")).toLowerCase();
                    String category = ((String) product.get("category")).toLowerCase();
                    return name.contains(lowered) || category.contains(lowered);
                })
                .collect(Collectors.toList()); // Sonuclari listeye cevirme islemi
        }

        @Tool(name = "fiyat_getir", description = "Urun koduna gore fiyat bilgisini dondur")
        public Map<String, Object> getPrice(@ToolParam(description = "Urun kodu, ornek: LAPTOP-001") String code) {
            return PRODUCTS.stream()
                .filter(product -> product.get("code").equals(code))
                .findFirst()
                .orElseGet(() -> Map.of("error", "Urun bulunamadi" + code));
        }
    }
}
