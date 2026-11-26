package com.example.football.repository;

import com.example.football.entity.PromotionVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionVersionRepository extends JpaRepository<PromotionVersion, Long> {
    List<PromotionVersion> findByPromotionIdOrderByVersionNumberDesc(Long promotionId);
}
