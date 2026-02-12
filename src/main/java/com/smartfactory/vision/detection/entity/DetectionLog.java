package com.smartfactory.vision.detection.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "detection_logs")
@Getter
@NoArgsConstructor
@ToString
public class DetectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private String cameraId;
    private String label;
    private Double confidence;
    private boolean isDefect;

    @Column(length = 50)
    private String workDir;

    @Builder
    public DetectionLog(LocalDateTime timestamp, String cameraId, String label, Double confidence, boolean isDefect,
            String workDir) {
        this.timestamp = timestamp;
        this.cameraId = cameraId;
        this.label = label;
        this.confidence = confidence;
        this.isDefect = isDefect;
        this.workDir = workDir;
    }
}
