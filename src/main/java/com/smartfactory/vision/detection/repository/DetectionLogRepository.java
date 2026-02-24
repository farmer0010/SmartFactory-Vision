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

    // Query for total detections vs defect counts by hour (Yield Monitoring)
    @Query(value = "SELECT HOUR(timestamp) as hour, COUNT(*) as total_count, " +
                   "SUM(CASE WHEN is_defect = true THEN 1 ELSE 0 END) as defect_count " +
                   "FROM detection_logs " +
                   "WHERE timestamp >= :since " +
                   "GROUP BY HOUR(timestamp) " +
                   "ORDER BY hour", nativeQuery = true)
    List<Map<String, Object>> getYieldStatsPerHour(LocalDateTime since);

    // Query for heatmap (defects by camera ID)
    @Query(value = "SELECT camera_id, COUNT(*) as defect_count " +
                   "FROM detection_logs " +
                   "WHERE is_defect = true AND timestamp >= :since " +
                   "GROUP BY camera_id " +
                   "ORDER BY defect_count DESC", nativeQuery = true)
    List<Map<String, Object>> getDefectHeatmapStats(LocalDateTime since);

    // Legacy method for compatibility if needed
    @Query(value = "SELECT HOUR(timestamp) as hour, COUNT(*) as count FROM detection_logs WHERE is_defect = true AND timestamp >= :since GROUP BY HOUR(timestamp) ORDER BY hour", nativeQuery = true)
    List<Map<String, Object>> countDefectsPerHour(LocalDateTime since);
}
