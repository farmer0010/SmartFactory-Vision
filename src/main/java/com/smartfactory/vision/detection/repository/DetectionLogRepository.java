package com.smartfactory.vision.detection.repository;

import com.smartfactory.vision.detection.entity.DetectionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface DetectionLogRepository extends JpaRepository<DetectionLog, Long> {

    Page<DetectionLog> findAllByOrderByTimestampDesc(Pageable pageable);

    List<DetectionLog> findByIsDefectTrueOrderByTimestampDesc();


    @Query(value = "SELECT HOUR(timestamp) as hour, COUNT(*) as count FROM detection_logs WHERE is_defect = true AND timestamp >= :since GROUP BY HOUR(timestamp) ORDER BY hour", nativeQuery = true)
    List<Map<String, Object>> countDefectsPerHour(LocalDateTime since);
}
