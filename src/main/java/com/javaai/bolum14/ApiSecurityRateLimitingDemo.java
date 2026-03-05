package com.javaai.bolum14;

import io.github.bucket4j.BandwidthBuilder;
import io.github.bucket4j.Bucket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "API Key")
@RequestMapping(value = "/api/b142", produces = "application/json;charset=UTF-8")

public class ApiSecurityRateLimitingDemo {

    private final ChatClient chatClient;

    // Kullanici bazli rate limit bucket'lari — ConcurrentHashMap thread-safe erisim saglar
    private final Map<String, Bucket> rateLimitBuckets = new ConcurrentHashMap<>();


    public ApiSecurityRateLimitingDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("Sen yardimci bir asistansin.")
            .build();
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "openai");
        SpringApplication.run(ApiSecurityRateLimitingDemo.class, args);
    }

    @Operation(security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/secure-chat")
    public Map<String, Object> secureChat(
        @RequestParam(defaultValue = "Merhaba") String message,
        @RequestParam(defaultValue = "user-1") String userId,
        HttpServletResponse httpResponse
    ) {
        Bucket bucket = getOrCreateBucket(userId); // Kullanıcı bucket'ı
        if (!bucket.tryConsume(1)) {
            httpResponse.setStatus(429);
            LinkedHashMap<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Dakika basina istek siniri asildi");
            error.put("kalanTokenBilgisi", 0);
            return error;
        }

        String answer = chatClient.prompt()
            .user(message)
            .call()
            .content();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("answer", answer);
        response.put("kalanTokenBilgisi", bucket.getAvailableTokens());
        return response;
    }


    @GetMapping("/rate-state")
    public Map<String, Object> rateState(
        @RequestParam(defaultValue = "user-1") String userId
    ) {
        Bucket bucket = getOrCreateBucket(userId);
        long kalanToken = bucket.getAvailableTokens();
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("userId", userId);
        response.put("kalanTokenBilgisi", kalanToken);
        return response;
    }

    private Bucket getOrCreateBucket(String userId) {
        return rateLimitBuckets.computeIfAbsent(userId, k ->
            Bucket.builder()
                .addLimit(BandwidthBuilder.builder()
                    .capacity(5)
                    .refillGreedy(5, Duration.ofMinutes(1))
                    .build())
                .build());
    }

    static class BearerTokenFilter extends OncePerRequestFilter {

        private static final Set<String> VALID_TOKENS = Set.of("demo-key-2026", "test-key-abc");

        @Override
        protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
            String authHeader = request.getHeader("Authorization");

            if (!authHeader.startsWith("Bearer ")) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("format gecersiz");
                return;
            }

            String token = authHeader.substring(7).trim();

            if (!VALID_TOKENS.contains(token)) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("format gecersiz");
                return;
            }

            filterChain.doFilter(request, response);
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String path = request.getRequestURI();

            return !path.startsWith("/api/b142/")
                || path.equals("/api/b142/rate-state");
        }
    }

    @Configuration
    static class FilterConfig {

        @Bean
        FilterRegistrationBean<BearerTokenFilter> bearerTokenFilterFilterRegistrationBean() {
            FilterRegistrationBean<BearerTokenFilter> reg = new FilterRegistrationBean<>();
            reg.setFilter(new BearerTokenFilter());
            reg.addUrlPatterns("/api/b142/*");
            reg.setOrder(1);
            return reg;
        }
    }

}