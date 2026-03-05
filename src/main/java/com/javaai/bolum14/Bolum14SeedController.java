package com.javaai.bolum14;

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
@RequestMapping(value = "/api/b14", produces = "application/json;charset=UTF-8")
public class Bolum14SeedController {

    private final com.javaai.bolum14.Bolum14SeedVeriHazirla seedService;

    public Bolum14SeedController(
        com.javaai.bolum14.Bolum14SeedVeriHazirla seedService) {
        this.seedService = seedService;
    }

    @PostMapping("/seed-all")
    public Map<String, Object> seedAll() {
        return this.seedService.seedAll();
    }

    @PostMapping("/seed/{lessonCode}")
    public Map<String, Object> seedLesson(
        @PathVariable String lessonCode) {
        return this.seedService.seedLesson(lessonCode);
    }
}
