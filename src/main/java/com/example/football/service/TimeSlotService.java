package com.example.football.service;

import com.example.football.entity.TimeSlot;
import com.example.football.form.TimeSlotForm;
import com.example.football.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;

    @Transactional(readOnly = true)
    public List<TimeSlot> getActiveTimeSlots() {
        return timeSlotRepository.findByActiveTrueOrderByStartTimeAsc();
    }

    @Transactional(readOnly = true)
    public List<TimeSlot> getAllTimeSlots() {
        return timeSlotRepository.findAllByOrderByStartTimeAsc();
    }

    @Transactional(readOnly = true)
    public TimeSlot getTimeSlotById(Long id) {
        return timeSlotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khung giờ với ID: " + id));
    }

    @Transactional
    public TimeSlot saveOrUpdateTimeSlot(TimeSlotForm form) {
        if (!form.getEndTime().isAfter(form.getStartTime())) {
            throw new IllegalArgumentException("Giờ kết thúc ca đá phải sau giờ bắt đầu!");
        }

        TimeSlot slot;
        if (form.getId() != null) {
            slot = getTimeSlotById(form.getId());
        } else {
            slot = new TimeSlot();
        }

        slot.setSlotName(form.getSlotName().trim());
        slot.setStartTime(form.getStartTime());
        slot.setEndTime(form.getEndTime());
        slot.setPeakHourExtraFee(form.getPeakHourExtraFee() != null ? form.getPeakHourExtraFee() : BigDecimal.ZERO);
        slot.setActive(form.isActive());

        TimeSlot saved = timeSlotRepository.save(slot);
        log.info("[OWNER TIMESLOT] Đã lưu khung giờ id={}, name='{}' ({}-{}), phụ phí={}",
                saved.getId(), saved.getSlotName(), saved.getStartTime(), saved.getEndTime(), saved.getPeakHourExtraFee());
        return saved;
    }

    @Transactional
    public void toggleActiveStatus(Long id) {
        TimeSlot slot = getTimeSlotById(id);
        slot.setActive(!slot.isActive());
        timeSlotRepository.save(slot);
    }
}

