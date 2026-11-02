package com.moeasy.moeasybe.domain.schedule.repository;

import com.moeasy.moeasybe.domain.schedule.entity.Schedule;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    boolean existsByIdAndGroupIdAndDeletedAtIsNull(Long scheduleId, Long groupId);
    Optional<Schedule> findByIdAndDeletedAtIsNull(Long id);

    @Query("select s.group.id from Schedule s where s.id = :id and s.deletedAt is null")
    Optional<Long> findGroupId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Schedule s where s.id = :id and s.deletedAt is null")
    Optional<Schedule> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select s from Schedule s where s.group.id = :groupId and s.deletedAt is null
            and s.startsAt < :to and s.endsAt > :from
            and (:createdAt is null or s.createdAt < :createdAt or (s.createdAt = :createdAt and s.id < :id))
            order by s.createdAt desc, s.id desc
            """)
    List<Schedule> findWindow(@Param("groupId") Long groupId, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to, @Param("createdAt") LocalDateTime createdAt,
            @Param("id") Long id, Pageable pageable);
}
