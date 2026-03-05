package com.javaai.bolum11;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("pgvector")
@EnableAutoConfiguration
@Import(Bolum11SeedVeriHazirla.class)
@RestController
@RequestMapping(value = "/api/b115", produces = "application/json;charset=UTF-8")
public class RecursiveAdvisorReActDemo {

    private static final String LESSON_CODE = "b115";
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.3;
    private static final int MAX_STEPS_DEFAULT = 5;

    private static final int MAX_STEPS_HARD_LIMIT = 8; // Guvenlik siniri — kullanicinin verebilecegi maksimum deger

    private final ChatClient.Builder chatClientBuilder;
    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final Bolum11SeedVeriHazirla seedService;


    public RecursiveAdvisorReActDemo(ChatClient.Builder chatClientBuilder,
        ChatModel chatModel,
        VectorStore vectorStore,
        Bolum11SeedVeriHazirla seedService) {
        this.chatClientBuilder = chatClientBuilder;
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.seedService = seedService;
    }

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "pgvector");
        SpringApplication.run(RecursiveAdvisorReActDemo.class, args);
    }

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return seedService.seedLesson(LESSON_CODE);
    }

    @GetMapping("/react-loop")
    public Map<String, Object> reactLoop(
        @RequestParam(defaultValue = "Spring AI'da RAG pipeline'ı nasıl optimize edilir?") String task,
        @RequestParam(defaultValue = "5") int maxSteps
    ) {
        int clampedSteps = Math.max(1, Math.min(maxSteps, MAX_STEPS_DEFAULT));

        return executeReActLoop(
            task, clampedSteps, String.format("Kullanıcı sinirli ReAct. maxSteps=%d (hard limit: %d)", clampedSteps, MAX_STEPS_HARD_LIMIT)
        );
    }

    private Map<String, Object> executeReActLoop(String task, int maxSteps, String loopNote) {
        List<Map<String, Object>> steps = new ArrayList<>();
        String currentTask = task;
        String finalAnswer = "";
        boolean isCompleted = false;
        String terminationReason = "max-steps"; // Varsayılan - adim siniri asildi.

        ResearchTools tools = new ResearchTools(vectorStore, chatModel, LESSON_CODE);

        MethodToolCallbackProvider toolProvider = MethodToolCallbackProvider.builder()
            .toolObjects(tools)
            .build();

        ToolCallback[] toolCallbacks = toolProvider.getToolCallbacks();

        String systemPrompt = "Sen bir arastirma asistanisin. ReAct dongusunu takip et:\n"
            + "1. GOZLEM: Bilgi tabanini ara (searchKnowledge) kullan."
            + "2. DUSUNCE: Buldukların hakkında dusun\n"
            + "3. AKSIYON: Ya daha fazla bilgi ara ya da sonuca ulas\n"
            + "Sonuca ulastiginda ccevabini FINAL: ile baslat.";

        ChatClient reactClient = chatClientBuilder.clone()
            .defaultSystem(systemPrompt)
            .build();

        for (int step = 1; step <= maxSteps; step++) {
            String response = reactClient.prompt()
                .toolCallbacks(toolCallbacks)
                .user(currentTask)
                .call()
                .content();

            if (response == null || response.isBlank()) {
                terminationReason = "empty-response";
                break;
            }

            LinkedHashMap<String, Object> stepRecord = new LinkedHashMap<>();
            stepRecord.put("stepNumber", step);
            stepRecord.put("action", currentTask);
            stepRecord.put("result", response);
            steps.add(stepRecord);

            if (response.contains("FINAL") || response.contains("FİNAL")) {
                finalAnswer = response.substring(response.indexOf("FINAL") + "FINAL:".length()).trim();
                isCompleted = true;
                terminationReason = "final";
                break;
            }

            currentTask = response;
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("task", task);
        result.put("steps", steps);
        result.put("finalAnswer", finalAnswer);
        result.put("completed", isCompleted);
        result.put("terminationReason", terminationReason);
        return result;
    }

    public static class ResearchTools {

        private final VectorStore vectorStore;
        private final ChatModel chatModel;
        private final String lessonCode;

        public ResearchTools(VectorStore vectorStore, ChatModel chatModel, String lessonCode) {
            this.vectorStore = vectorStore;
            this.chatModel = chatModel;
            this.lessonCode = lessonCode;
        }

        @Tool(description = "Bilgi tabaninda arama yapar ve ilgili dokumanları döndürür")
        public String searchKnowledge(
            @ToolParam(description = "Aranacak sorgu") String query
        ) {
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThreshold(0.3)
                .filterExpression(fb.eq("lesson", lessonCode).build())
                .build();

            List<Document> docs = vectorStore.similaritySearch(request);

            if (docs.isEmpty()) {
                return "Sonuc bulunamadi.";
            }

            StringBuilder sb = new StringBuilder(); // Belgeleri numarali listele - LLM referansi icin
            for (int i = 0; i < docs.size(); i++) {
                sb.append(String.format("[%d] %s%n",
                    i + 1, docs.get(i).getText()));
            }
            return sb.toString().trim();
        }

        @Tool(description = "Verilen metni ozetler")
        public String getSummary(
            @ToolParam(description = "Ozetlenecek metin") String text
        ) {
            String prompt = String.format("Su metni 2-3 cumleyle ozetle:\n\n%s", text);

            return ChatClient.builder(chatModel)
                .build()
                .prompt()
                .user(prompt)
                .call()
                .content();
        }
    }
}