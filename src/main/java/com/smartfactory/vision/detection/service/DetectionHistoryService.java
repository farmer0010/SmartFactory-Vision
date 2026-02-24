package com.smartfactory.vision.detection.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.vision.detection.entity.DetectionLog;
import com.smartfactory.vision.detection.repository.DetectionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionHistoryService {

    private final DetectionLogRepository detectionLogRepository;
    private final ObjectMapper objectMapper;

    // In-memory buffer for normal detections to prevent DB overload (High-Frequency
    // Batching)
    private final ConcurrentLinkedQueue<DetectionLog> normalLogBuffer = new ConcurrentLinkedQueue<>();
    private static final int BATCH_SIZE_LIMIT = 500;

    @Async
    public void saveLogAsync(String cameraId, String jsonResult) {
        try {
            JsonNode root = objectMapper.readTree(jsonResult);

            if (root.has("error") || root.has("skipped")) {
                return;
            }

            if (root.has("detections") && root.get("detections").isArray()) {
                JsonNode detections = root.get("detections");

                for (JsonNode det : detections) {
                    processDetection(cameraId, det);
                }
            }
        } catch (Exception e) {
            log.error("[History] Failed to parse and save log", e);
        }
    }

    private void processDetection(String cameraId, JsonNode det) {
        String label = det.path("label").asText("unknown");
        double conf = det.path("score").asDouble(0.0);
        boolean isDefect = label.toLowerCase().contains("ng") || label.toLowerCase().contains("defect");

        DetectionLog logEntry = DetectionLog.builder()
                .timestamp(LocalDateTime.now())
                .cameraId(cameraId)
                .label(label)
                .confidence(conf)
                .isDefect(isDefect)
                .workDir("local")
                .build();

        if (isDefect) {
            // IMMEDIATE SAVE FOR DEFECTS
            saveDefectLog(logEntry);
        } else {
            // BATCH FOR NORMAL LOGS
            bufferNormalLog(logEntry);
        }
    }

    @Transactional
    protected void saveDefectLog(DetectionLog logEntry) {
        detectionLogRepository.save(logEntry);
        log.warn("🚨 [History] DEFECT detected and saved immediately: {} ({}) for {}", logEntry.getLabel(),
                logEntry.getConfidence(), logEntry.getCameraId());
    }

    private void bufferNormalLog(DetectionLog logEntry) {
        normalLogBuffer.offer(logEntry);
        if (normalLogBuffer.size() >= BATCH_SIZE_LIMIT) {
            flushNormalLogs();
        }
    }

    // Flush every 10 seconds or when buffer hits limit
    @Scheduled(fixedRate = 10000)
    @Transactional
    public void flushNormalLogs() {
        if (normalLogBuffer.isEmpty()) {
            return;
        }

        List<DetectionLog> batch = new ArrayList<>();
        while (!normalLogBuffer.isEmpty() && batch.size() < BATCH_SIZE_LIMIT) {
            DetectionLog logEntry = normalLogBuffer.poll();
            if (logEntry != null) {
                batch.add(logEntry);
            }
        }

        if (!batch.isEmpty()) {
            detectionLogRepository.saveAll(batch);
            log.info("[History] Flushed {} normal logs to DB in batch.", batch.size());
        }
    }

    public Page<DetectionLog> getRecentLogs(int page, int size) {
        return detectionLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(page, size));
    }

    public List<Map<String, Object>> getDefectStats() {
        try {
            return detectionLogRepository.countDefectsPerHour(LocalDateTime.now().minusDays(1));
        } catch (Exception e) {
            log.error("[History] Failed to fetch defect stats", e);
            return List.of();
        }
    }
}
