package com.smartfactory.vision.detection.controller;

import com.smartfactory.vision.detection.entity.DetectionLog;
import com.smartfactory.vision.detection.repository.DetectionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryApiController {

    private final DetectionLogRepository detectionLogRepository;

    @GetMapping("/recent")
    public List<DetectionLog> getRecentLogs() {
        return detectionLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, 50)).getContent();
    }
}
