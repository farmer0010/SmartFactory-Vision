package com.smartfactory.vision.audit.repository;

import com.smartfactory.vision.audit.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    List<AuditLog> findByEventTypeOrderByTimestampDesc(String eventType, Pageable pageable);
}
