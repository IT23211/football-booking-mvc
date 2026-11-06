package com.example.football.repository;

import com.example.football.entity.AuditSecurityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditSecurityLogRepository extends JpaRepository<AuditSecurityLog, Long> {

    List<AuditSecurityLog> findTop50ByOrderByCreatedAtDesc();
}

