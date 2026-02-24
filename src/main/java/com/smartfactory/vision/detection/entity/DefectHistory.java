package com.smartfactory.vision.detection.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "defect_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DefectHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String camId;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private String defectType;

    @Column(nullable = false)
    private Double confidence;
}
