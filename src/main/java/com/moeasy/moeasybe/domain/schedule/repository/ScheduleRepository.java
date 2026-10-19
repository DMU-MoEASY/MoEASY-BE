package com.moeasy.moeasybe.domain.schedule.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ScheduleRepository {

    private final JdbcTemplate jdbcTemplate;

    // TODO: 일정 도메인에서 Schedule 엔티티 구현 시 JpaRepository 기반 조회로 전환하고
    // 같은 모임 소속·미삭제 일정 조건을 유지한다.
    public boolean existsByIdAndGroupIdAndDeletedAtIsNull(Long scheduleId, Long groupId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM schedule
                    WHERE id = ? AND group_id = ? AND deleted_at IS NULL
                )
                """, Boolean.class, scheduleId, groupId));
    }
}
