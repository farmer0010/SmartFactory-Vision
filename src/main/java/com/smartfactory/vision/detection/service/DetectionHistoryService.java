package com.smartfactory.vision.detection.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.vision.detection.entity.DetectionLog;
import com.smartfactory.vision.detection.repository.DetectionLogRepository;
import com.smartfactory.vision.control.OpcUaClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionHistoryService {

    private final DetectionLogRepository detectionLogRepository;
    private final ObjectMapper objectMapper;
    private final OpcUaClientService opcUaClientService;
    private final SimpMessagingTemplate messagingTemplate;

    private static final double ALERT_DEFECT_RATE_THRESHOLD = 0.20; 

    private static final double MIN_CONFIDENCE_THRESHOLD = 0.70;
    private static final int REQUIRED_CONSECUTIVE_DEFECTS = 3;
    private final Map<String, Integer> consecutiveDefectCounter = new ConcurrentHashMap<>();

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

        if (conf < MIN_CONFIDENCE_THRESHOLD) {

            DetectionLog logEntry = createLogEntry(cameraId, label, conf, false);
            bufferNormalLog(logEntry);
            consecutiveDefectCounter.put(cameraId, 0); 
            return;
        }

        boolean isDefectLabel = label.toLowerCase().contains("ng") || label.toLowerCase().contains("defect");

        if (isDefectLabel) {

            int count = consecutiveDefectCounter.getOrDefault(cameraId, 0) + 1;
            consecutiveDefectCounter.put(cameraId, count);

            if (count >= REQUIRED_CONSECUTIVE_DEFECTS) {

                DetectionLog logEntry = createLogEntry(cameraId, label, conf, true);
                saveDefectLog(logEntry);

                opcUaClientService.triggerRejectKicker();

                consecutiveDefectCounter.put(cameraId, 0);
            } else {

                DetectionLog logEntry = createLogEntry(cameraId, label + "_unconfirmed", conf, false);
                bufferNormalLog(logEntry);
                log.debug("[History] Potential defect on {} (Frame {}/{}), waiting for confirmation...", cameraId,
                        count, REQUIRED_CONSECUTIVE_DEFECTS);
            }
        } else {

            DetectionLog logEntry = createLogEntry(cameraId, label, conf, false);
            bufferNormalLog(logEntry);
            consecutiveDefectCounter.put(cameraId, 0); 
        }
    }

    private DetectionLog createLogEntry(String cameraId, String label, double conf, boolean isDefect) {
        return DetectionLog.builder()
                .timestamp(LocalDateTime.now())
                .cameraId(cameraId)
                .label(label)
                .confidence(conf)
                .isDefect(isDefect)
                .workDir("local")
                .build();
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

    @Scheduled(fixedRate = 60000)
    public void checkDefectRateAlert() {
        try {
            LocalDateTime since = LocalDateTime.now().minusMinutes(5);
            List<DetectionLog> recentLogs = detectionLogRepository.findAllByOrderByTimestampDesc(
                    PageRequest.of(0, 200)).getContent();

            List<DetectionLog> window = recentLogs.stream()
                    .filter(l -> l.getTimestamp().isAfter(since))
                    .toList();

            if (window.size() < 10)
                return; 

            long defectCount = window.stream().filter(DetectionLog::isDefect).count();
            double defectRate = (double) defectCount / window.size();

            if (defectRate >= ALERT_DEFECT_RATE_THRESHOLD) {
                String alertMsg = String.format(
                        "{\"type\":\"DEFECT_RATE_ALERT\",\"rate\":%.1f,\"defects\":%d,\"total\":%d,\"window_min\":5}",
                        defectRate * 100, defectCount, window.size());
                messagingTemplate.convertAndSend("/topic/alerts", alertMsg);
                log.warn("[Alert] HIGH DEFECT RATE: {:.1f}% ({} / {}) in last 5 min",
                        defectRate * 100, defectCount, window.size());
            }
        } catch (Exception e) {
            log.error("[Alert] Defect rate check failed: {}", e.getMessage());
        }
    }
}
