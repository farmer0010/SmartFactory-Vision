package com.smartfactory.vision.detection.repository;

import com.smartfactory.vision.detection.entity.DefectHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DefectHistoryRepository extends JpaRepository<DefectHistory, Long> {
    List<DefectHistory> findTop100ByOrderByTimestampDesc();
}
