package com.example.football.repository;

import com.example.football.entity.FieldMaintenanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FieldMaintenanceLogRepository extends JpaRepository<FieldMaintenanceLog, Long> {

    @Query("""
            SELECT m FROM FieldMaintenanceLog m
            JOIN FETCH m.field f
            LEFT JOIN FETCH m.timeSlot ts
            WHERE f.id = :fieldId AND m.maintenanceDate = :date
            """)
    List<FieldMaintenanceLog> findByFieldIdAndDate(@Param("fieldId") Long fieldId,
                                                   @Param("date") LocalDate date);

    @Query("""
            SELECT m FROM FieldMaintenanceLog m
            JOIN FETCH m.field f
            LEFT JOIN FETCH m.timeSlot ts
            WHERE f.id = :fieldId
            ORDER BY m.maintenanceDate DESC, m.createdAt DESC
            """)
    List<FieldMaintenanceLog> findByFieldIdOrderByMaintenanceDateDesc(@Param("fieldId") Long fieldId);

    @Query("""
            SELECT m FROM FieldMaintenanceLog m
            JOIN FETCH m.field f
            LEFT JOIN FETCH m.timeSlot ts
            JOIN FETCH m.createdBy u
            ORDER BY m.maintenanceDate DESC, m.createdAt DESC
            """)
    List<FieldMaintenanceLog> findAllWithDetails();
}
