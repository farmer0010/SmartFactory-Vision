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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionHistoryService {

    private final DetectionLogRepository detectionLogRepository;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional
    public void saveLogAsync(String cameraId, String jsonResult) {
        try {
            JsonNode root = objectMapper.readTree(jsonResult);

            if (root.has("error") || root.has("skipped")) {
                return;
            }


            if (root.has("detections") && root.get("detections").isArray()) {
                JsonNode detections = root.get("detections");
                log.info("[History] Processing {} detections for {}", detections.size(), cameraId);
                

                for (JsonNode det : detections) {
                    String label = det.path("label").asText("unknown");
                    double conf = det.path("score").asDouble(0.0); // Python uses 'score'
                    boolean isDefect = label.toLowerCase().contains("ng") || label.toLowerCase().contains("defect");

                    DetectionLog logEntry = DetectionLog.builder()
                            .timestamp(LocalDateTime.now())
                            .cameraId(cameraId)
                            .label(label)
                            .confidence(conf)
                            .isDefect(isDefect)
                            .workDir("local")
                            .build();

                    detectionLogRepository.save(logEntry);
                    log.info("[History] Saved detection: {} ({}) for {}", label, conf, cameraId);
                }
            } else {
                log.info("[History] No detections array found for {}", cameraId);
            }
        } catch (Exception e) {
            log.error("[History] Failed to save log", e);
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
