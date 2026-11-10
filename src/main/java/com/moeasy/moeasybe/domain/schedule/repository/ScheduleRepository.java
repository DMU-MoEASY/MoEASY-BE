package com.moeasy.moeasybe.domain.schedule.repository;

import com.moeasy.moeasybe.domain.schedule.entity.Schedule;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    interface SubscriptionMember {
        Long getScheduleId();
        Long getMemberId();
    }

    @Query("""
            select s.id as scheduleId, m.id as memberId
            from Schedule s join s.group g join GroupMember gm on gm.group = g join gm.member m
            where s.id in :scheduleIds and m.id in :memberIds
            and s.deletedAt is null and g.deletedAt is null
            and g.status <> com.moeasy.moeasybe.domain.group.enums.GroupStatus.CLOSED
            and gm.status = com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus.ACTIVE
            and gm.deletedAt is null and m.deletedAt is null
            and m.status = com.moeasy.moeasybe.domain.member.enums.MemberStatus.ACTIVE
            """)
    List<SubscriptionMember> findSubscriptionMembers(@Param("scheduleIds") Collection<Long> scheduleIds,
            @Param("memberIds") Collection<Long> memberIds);

    boolean existsByIdAndGroupIdAndDeletedAtIsNull(Long scheduleId, Long groupId);
    Optional<Schedule> findByIdAndDeletedAtIsNull(Long id);
    Optional<Schedule> findByIdAndGroupIdAndDeletedAtIsNull(Long id, Long groupId);

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
