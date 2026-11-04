package com.moeasy.moeasybe.domain.schedule.repository;
import com.moeasy.moeasybe.domain.schedule.entity.AvailabilitySlot;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {
    List<AvailabilitySlot> findAllByAvailabilityPollIdOrderBySlotAt(Long pollId);
}
