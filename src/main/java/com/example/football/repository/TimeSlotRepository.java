package com.example.football.repository;

import com.example.football.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    List<TimeSlot> findByActiveTrueOrderByStartTimeAsc();

    List<TimeSlot> findAllByOrderByStartTimeAsc();
}
