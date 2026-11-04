package com.moeasy.moeasybe.domain.schedule.repository;
import com.moeasy.moeasybe.domain.schedule.entity.AvailabilityPoll;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface AvailabilityPollRepository extends JpaRepository<AvailabilityPoll, Long> {
    Optional<AvailabilityPoll> findByScheduleId(Long scheduleId);
    @Query("select p.scheduleId from AvailabilityPoll p where p.status = com.moeasy.moeasybe.domain.schedule.enums.AvailabilityStatus.OPEN and p.voteDeadlineAt <= :now order by p.id")
    List<Long> findDueScheduleIds(@Param("now") LocalDateTime now, org.springframework.data.domain.Pageable page);
}
