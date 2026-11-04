package com.moeasy.moeasybe.domain.schedule.repository;
import com.moeasy.moeasybe.domain.schedule.entity.AvailabilityResponse;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AvailabilityResponseRepository extends JpaRepository<AvailabilityResponse, Long> {
    List<AvailabilityResponse> findAllByAvailabilityPollId(Long pollId);
    Optional<AvailabilityResponse> findByAvailabilityPollIdAndGroupMemberId(Long pollId, Long groupMemberId);
}
