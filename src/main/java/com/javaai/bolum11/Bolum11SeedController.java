package com.javaai.bolum11;

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
@RequestMapping(value = "/api/b11", produces = "application/json;charset=UTF-8")
public class Bolum11SeedController {

    private final Bolum11SeedVeriHazirla seedService;

    public Bolum11SeedController(Bolum11SeedVeriHazirla seedService) {
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
