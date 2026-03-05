// Swagger UI  : http://localhost:8080/swagger-ui.html
// Test (POST) : http://localhost:8080/api/b10/seed-all
// Test (POST) : http://localhost:8080/api/b10/seed/b102

// Iki endpoint saglar:
//   1. POST /api/b10/seed-all          → Tum 8 dersi (b102-b109) tek istekte seed eder
//   2. POST /api/b10/seed/{lessonCode} → Tek bir dersi seed eder (ornek: /api/b10/seed/b102)

package com.javaai.bolum10;

import java.util.Map;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("pgvector")
@EnableAutoConfiguration
@RestController
@RequestMapping(value = "/api/b10", produces = "application/json;charset=UTF-8") // Ortak prefix; Turkce karakterler icin UTF-8 zorunlu
public class Bolum10SeedController {

    private final Bolum10SeedVeriHazirla seedService;

    public Bolum10SeedController(Bolum10SeedVeriHazirla seedService) {
        this.seedService = seedService;
    }


    @PostMapping("/seed-all")
    public Map<String, Object> seedAll() {
        return this.seedService.seedAll(); // Servis ic donerek tum dersleri sirayla seed eder
    }

    @PostMapping("/seed/{lessonCode}")
    public Map<String, Object> seedLesson(@PathVariable String lessonCode) {
        return this.seedService.seedLesson(lessonCode); // Servis: DELETE by lesson + VectorStore.add
    }
}
