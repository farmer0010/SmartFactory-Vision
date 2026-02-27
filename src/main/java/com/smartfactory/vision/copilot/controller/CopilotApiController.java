package com.smartfactory.vision.copilot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@RestController
@RequestMapping("/api/copilot")
@RequiredArgsConstructor
public class CopilotApiController {

    private static final String PYTHON_COPILOT_URL = "http://localhost:8765/chat";
    private static final int MAX_HISTORY = 30;

    private final List<Map<String, String>> chatHistory = new CopyOnWriteArrayList<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "").trim();

        if (question.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "질문이 비어 있습니다."));
        }

        log.info("[Copilot] Received question: {}", question);
        long startMs = System.currentTimeMillis();

        try {

            String jsonBody = String.format("{\"question\":\"%s\",\"session_id\":\"web\"}",
                    question.replace("\"", "\\\"").replace("\n", "\\n"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PYTHON_COPILOT_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            long elapsedMs = System.currentTimeMillis() - startMs;
            log.info("[Copilot] Python response in {}ms (status {})", elapsedMs, response.statusCode());

            if (response.statusCode() != 200) {
                return ResponseEntity.status(502)
                        .body(Map.of(
                                "error", "AI 서버 오류",
                                "detail", "Python Copilot 서버가 응답하지 않습니다 (HTTP " + response.statusCode() + ")"));
            }

            String rawBody = response.body();
            String answer = extractJsonField(rawBody, "answer");
            String toolUsed = extractJsonField(rawBody, "tool_used");

            saveToHistory(question, answer);

            return ResponseEntity.ok(Map.of(
                    "answer", answer,
                    "tool_used", toolUsed != null ? toolUsed : "unknown",
                    "elapsed_ms", elapsedMs));

        } catch (java.net.ConnectException e) {
            log.warn("[Copilot] Python server not reachable: {}", e.getMessage());
            return ResponseEntity.status(503)
                    .body(Map.of(
                            "answer", "⚠️ AI Copilot 서버가 실행되지 않고 있습니다.\n\n" +
                                    "서버 시작 방법:\n" +
                                    "```\ncd C:\\Users\\김주영\\.jpyrust\\python_dist\n" +
                                    "pip install -r copilot_requirements.txt\n" +
                                    "python copilot_server.py\n```",
                            "error", "COPILOT_SERVER_NOT_RUNNING"));
        } catch (Exception e) {
            log.error("[Copilot] Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of(
                            "answer", "죄송합니다. AI 처리 중 오류가 발생했습니다: " + e.getMessage(),
                            "error", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, String>>> getHistory() {
        return ResponseEntity.ok(new ArrayList<>(chatHistory));
    }

    @DeleteMapping("/history")
    public ResponseEntity<Map<String, String>> clearHistory() {
        chatHistory.clear();
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8765/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.ok(Map.of(
                    "python_server", "UP",
                    "status_code", response.statusCode(),
                    "detail", response.body()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "python_server", "DOWN",
                    "reason", e.getMessage()));
        }
    }

    private void saveToHistory(String question, String answer) {
        chatHistory.add(Map.of("role", "user", "content", question));
        chatHistory.add(Map.of("role", "assistant", "content", answer));

        while (chatHistory.size() > MAX_HISTORY * 2) {
            chatHistory.remove(0);
            chatHistory.remove(0);
        }
    }

    private String extractJsonField(String json, String field) {

        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0)
            return null;
        start += key.length();
        int end = json.indexOf("\"", start);
        if (end < 0)
            return null;
        return json.substring(start, end)
                .replace("\\n", "\n")
                .replace("\\\"", "\"");
    }
}
