package com.example.football.service;

import com.example.football.entity.AuditSecurityLog;
import com.example.football.enums.SecurityEventType;
import com.example.football.repository.AuditSecurityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditSecurityLogRepository auditRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSecurityEvent(Long userId,
                                 String username,
                                 SecurityEventType eventType,
                                 String targetEndpoint,
                                 String ipAddress,
                                 String details) {
        try {
            AuditSecurityLog securityLog = AuditSecurityLog.builder()
                    .userId(userId)
                    .username(username)
                    .eventType(eventType)
                    .targetEndpoint(targetEndpoint)
                    .ipAddress(ipAddress != null ? ipAddress : "UNKNOWN")
                    .details(details)
                    .build();
            auditRepository.save(securityLog);
            log.warn("[SECURITY AUDIT LOG] Event={} | User={} | IP={} | Endpoint={} | Details={}",
                    eventType, username, ipAddress, targetEndpoint, details);
        } catch (Exception ex) {
            log.error("Không thể ghi AuditSecurityLog: {}", ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String username,
                         SecurityEventType eventType,
                         String ipAddress,
                         String targetEndpoint,
                         String details) {
        logSecurityEvent(null, username, eventType, targetEndpoint, ipAddress, details);
    }

    @Transactional(readOnly = true)
    public List<AuditSecurityLog> getRecentSecurityLogs() {
        return auditRepository.findTop50ByOrderByCreatedAtDesc();
    }
}
