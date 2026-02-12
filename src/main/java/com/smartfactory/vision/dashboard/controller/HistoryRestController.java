package com.smartfactory.vision.dashboard.controller;

import com.smartfactory.vision.detection.entity.DetectionLog;
import com.smartfactory.vision.detection.service.DetectionHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryRestController {

    private final DetectionHistoryService detectionHistoryService;

    @GetMapping
    public Page<DetectionLog> getRecentLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return detectionHistoryService.getRecentLogs(page, size);
    }

    @GetMapping("/stats")
    public List<Map<String, Object>> getStats() {
        return detectionHistoryService.getDefectStats();
    }
}
