package com.example.football.repository;

import com.example.football.entity.FootballField;
import com.example.football.enums.FieldStatus;
import com.example.football.enums.FieldType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FootballFieldRepository extends JpaRepository<FootballField, Long> {

    Optional<FootballField> findByFieldCode(String fieldCode);

    boolean existsByFieldCode(String fieldCode);

    List<FootballField> findByStatusOrderByIdAsc(FieldStatus status);

    List<FootballField> findAllByOrderByIdDesc();

    /**
     * Khóa bi quan dòng sân bóng (PESSIMISTIC_WRITE - SELECT ... FOR UPDATE)
     * để tuần tự hóa tuyệt đối khi nhiều người cùng bấm đặt 1 sân cùng lúc.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FootballField f WHERE f.id = :id")
    Optional<FootballField> findByIdWithLock(@Param("id") Long id);

    @Query("""
            SELECT f FROM FootballField f
            WHERE (:fieldType IS NULL OR f.fieldType = :fieldType)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(f.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(f.fieldCode) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND f.status <> com.example.football.enums.FieldStatus.INACTIVE
            ORDER BY f.status ASC, f.id ASC
            """)
    List<FootballField> searchAvailableFields(@Param("fieldType") FieldType fieldType,
                                              @Param("keyword") String keyword);
}
