package com.smartfactory.vision.audit.service;

import com.smartfactory.vision.audit.entity.AuditLog;
import com.smartfactory.vision.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(String username, String eventType, String message) {
        log(username, eventType, message, null);
    }

    @Async
    public void log(String username, String eventType, String message, String ipAddress) {
        try {
            AuditLog entry = AuditLog.builder()
                    .timestamp(LocalDateTime.now())
                    .username(username != null ? username : "SYSTEM")
                    .eventType(eventType)
                    .message(message)
                    .ipAddress(ipAddress)
                    .build();
            auditLogRepository.save(entry);
            log.debug("[Audit] {} | {} | {}", eventType, username, message);
        } catch (Exception e) {
            log.error("[Audit] Failed to save audit log: {}", e.getMessage());
        }
    }

    public List<AuditLog> getRecentLogs(int limit) {
        return auditLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, Math.min(limit, 500)));
    }

    @EventListener
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        log(username, "LOGIN", "로그인 성공");
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        if (event.getAuthentication() != null) {
            String username = event.getAuthentication().getName();
            log(username, "LOGOUT", "로그아웃");
        }
    }
}
