package com.javaai.bolum05;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!multimodel")
@EnableAutoConfiguration
@RestController
@RequestMapping("/api/b53")

public class NestedEnumPolimorfikDemo {

    private static final Logger log = LoggerFactory.getLogger(NestedEnumPolimorfikDemo.class);
    private final ChatClient chatClient;

    public NestedEnumPolimorfikDemo(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem(
                "Sen kurumsal rapor asistanisin. Alan adlarini eksiksiz ve tutarli doldur.")
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(NestedEnumPolimorfikDemo.class, args);
    }

    @GetMapping("/company-report")
    public Map<String, Object> companyReport(
        @RequestParam(defaultValue = "E-Commerce") String sector) {
        CompanyReport report = chatClient.prompt()
            .user("""
                %s sektoru icin bir sirket raporu olustur.
                companyName, sector ve departments alanlarını doldur
                Department.type yalnızca su enum degerlerinden biri olsun:
                ENGINEERING, MARKETING, HR, FINANCE.
                Her department'te en az 2 project olsun.
                """.formatted(sector.trim()))
            .call()
            .entity(CompanyReport.class);

        return Map.of(
            "mode", "nested-enum",
            "report", report
        );
    }

    @GetMapping("/work-items")
    public Map<String, Object> workItems(@RequestParam(required = false) String product) {
        String effectiveProduct = product == null ? "mobile app" : product;

        List<WorkItem> items = chatClient
            .prompt()
            .user("""
                %s urunu icin 4 is kalemi uret.
                Iki tanesi FEATURE, iki tanesi BUG olsun.
                
                itemType alani yalnizca BUYUK HARFLE FEATURE veya BUG olsun.
                Tuk kayitlarda summary ve owner dolu olsun.
                FEATURE kayitlarda storyPoint dolu kalsin.
                FEATURE kayitlarda severity bos birakilsin.
                BUG kayitlarinda severity dolu olsun.
                BUG kayitlarinda storyPoint bos birakilsin.
                """.formatted(effectiveProduct.trim()))
            .call()
            .entity(new ParameterizedTypeReference<List<WorkItem>>() {
            });

        long featureCount = items.stream().filter(item -> item.itemType() == WorkItemType.FEATURE)
            .count(); // Feature sayisi
        long bugCount = items.stream().filter(item -> item.itemType() == WorkItemType.BUG)
            .count(); // Bug sayisi

        return Map.of(
            "mode", "simple-polymorphism",
            "product", effectiveProduct.trim(),
            "featureCount", featureCount,
            "bugCount", bugCount,
            "items", items
        );
    }

    public enum DepartmentType {
        ENGINEERING,
        MARKETING,
        HR,
        FINANCE
    }

    public enum WorkItemType {
        FEATURE,
        BUG
    }

    /**
     * CompanyReport: Nested yapinin en ust katmani
     *
     * @param companyName Sirket adi
     * @param sector      Sirket sektoru
     * @param departments Departman listesi (her Department icinde Project listesi var)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompanyReport(String companyName, String sector, List<Department> departments) {

        public CompanyReport {
            if (companyName == null || companyName.isBlank()) {
                throw new IllegalArgumentException("companyName bos olamaz");
            }
            if (sector == null || sector.isBlank()) {
                throw new IllegalArgumentException("sector bos olamaz");
            }
            if (departments == null || departments.isEmpty()) {
                throw new IllegalArgumentException("departments bos olamaz");
            }
        }
    }

    /**
     * Departman modeli. Orta katman
     *
     * @param name          Departman adi
     * @param type          Departman tipi enum (ENGINEERING, MARKETING, HR, FINANCE)
     * @param employeeCount Calisan sayisi
     * @param projects      Proje listesi
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Department(String name, DepartmentType type, int employeeCount,
                             List<Project> projects) {

        public Department {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("department name bos olamaz");
            }
            if (type == null) {
                throw new IllegalArgumentException("department type bos olamaz");
            }
            if (employeeCount <= 0) {
                throw new IllegalArgumentException("employeeCount pozitif olmali");
            }
            if (projects == null || projects.isEmpty()) {
                throw new IllegalArgumentException("projects bos olamaz");
            }
        }
    }

    /**
     * Nested yapinin en alt katmani.
     *
     * @param name              Proje adi
     * @param status            Durum (PLANNED, IN_PROGRESS, COMPLETED, vb.)
     * @param completionPercent Tamamlanma yuzdesi (0-100)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Project(String name, String status, int completionPercent) {

        public Project {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("project name bos olamaz");
            }
            if (status == null || status.isBlank()) {
                throw new IllegalArgumentException("project status bos olamaz");
            }
            if (completionPercent < 0 || completionPercent > 100) {
                throw new IllegalArgumentException("completionPercent 0-100 olmali");
            }
        }
    }

    /**
     * FEATURE/BUG kalemlerini tek listede tasiyan sade polimorfik model.
     *
     * @param itemType   Is kalemi tipi: FEATURE veya BUG
     * @param summary    Kisa aciklama
     * @param owner      Sorumlu kisi
     * @param storyPoint FEATURE ise pozitif olmali, BUG ise null
     * @param severity   BUG ise dolu olmali (LOW/MEDIUM/HIGH/CRITICAL), FEATURE ise null
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkItem(
        WorkItemType itemType,
        String summary,
        String owner,
        Integer storyPoint,
        String severity) {

        public WorkItem {
            if (itemType == null) {
                throw new IllegalArgumentException("itemType bos olamaz");
            }
            if (summary == null || summary.isBlank()) {
                throw new IllegalArgumentException("summary bos olamaz");
            }
            if (owner == null || owner.isBlank()) {
                throw new IllegalArgumentException("owner bos olamaz");
            }

            if (itemType == WorkItemType.FEATURE) {
                if (storyPoint == null || storyPoint <= 0) {
                    throw new IllegalArgumentException("FEATURE icin storyPoint pozitif olmali");
                }
                if (severity != null && !severity.isBlank()) {
                    throw new IllegalArgumentException("FEATURE kaydinda severity olmamali");
                }
            }

            if (itemType == WorkItemType.BUG) {
                if (severity == null || severity.isBlank()) {
                    throw new IllegalArgumentException("BUG icin severity bos olamaz");
                }
                if (storyPoint != null) {
                    throw new IllegalArgumentException("BUG kaydinda storyPoint olmamali");
                }
            }
        }
    }
}
