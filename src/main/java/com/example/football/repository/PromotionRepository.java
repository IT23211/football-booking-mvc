package com.example.football.repository;

import com.example.football.entity.Promotion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    Optional<Promotion> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Promotion p WHERE p.id = :id")
    Optional<Promotion> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Promotion p WHERE UPPER(p.code) = UPPER(:code)")
    Optional<Promotion> findByCodeWithLock(@Param("code") String code);

    List<Promotion> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    @Query("""
        SELECT p FROM Promotion p
        WHERE p.owner.id = :ownerId
          AND (:keyword IS NULL OR :keyword = '' OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY p.createdAt DESC
        """)
    List<Promotion> searchOwnerPromotions(@Param("ownerId") Long ownerId,
                                          @Param("keyword") String keyword);

    @Query("SELECT p FROM Promotion p WHERE p.owner.id = :ownerId AND p.stopped = false ORDER BY p.startAt ASC")
    List<Promotion> findPublicPromotionsByOwner(@Param("ownerId") Long ownerId);
}
