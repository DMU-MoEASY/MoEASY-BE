package com.moeasy.moeasybe.domain.group.repository;

import com.moeasy.moeasybe.domain.group.service.GroupTierPolicy;
import com.moeasy.moeasybe.domain.schedule.repository.ParticipationRepository.Totals;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GroupTierRepository {
    private final JdbcTemplate db;
    public record Job(LocalDate basis, long group, String token) {}

    public LocalDate plannedThrough() {
        return db.queryForObject("SELECT planned_through FROM attendance_cutover WHERE id=1", LocalDate.class);
    }

    public void finishPlanning(LocalDate day) {
        db.update("UPDATE attendance_cutover SET planned_through=? WHERE id=1 AND planned_through<?", day, day);
    }

    public List<Long> missing(LocalDate day, long after) {
        return db.queryForList("""
                SELECT g.id FROM member_group g WHERE g.id>? AND g.deleted_at IS NULL
                AND g.status IN ('ACTIVE','ARCHIVED') AND g.created_at<=?
                AND NOT EXISTS (SELECT 1 FROM group_tier_job j WHERE j.basis=? AND j.group_id=g.id)
                ORDER BY g.id LIMIT 200
                """, Long.class, after, day.atStartOfDay(), day);
    }

    public void enqueue(LocalDate day, List<Long> ids, String state) {
        db.batchUpdate("""
                INSERT INTO group_tier_job(basis,group_id,state) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE group_id=group_tier_job.group_id
                """, ids.stream().map(id -> new Object[]{day, id, state}).toList());
    }

    public void skipOlder(LocalDate day) {
        db.update("UPDATE group_tier_job SET state='SKIPPED',owner_token=NULL WHERE basis<? AND state IN ('RUNNING','FAILED','PENDING')", day);
    }

    public List<Long> ready(LocalDate day, LocalDateTime at, long after) {
        return db.queryForList("""
                SELECT group_id FROM group_tier_job WHERE basis=? AND group_id>? AND
                (state='PENDING' OR (state IN ('FAILED','RUNNING') AND lease_until<=?))
                ORDER BY group_id LIMIT 100
                """, Long.class, day, after, at);
    }

    public boolean claim(Job j, LocalDateTime at) {
        return db.update("""
                UPDATE group_tier_job SET state='RUNNING',owner_token=?,lease_until=?,attempts=attempts+1
                WHERE basis=? AND group_id=? AND (state='PENDING' OR (state IN ('FAILED','RUNNING') AND lease_until<=?))
                """, j.token(), at.plusMinutes(1), j.basis(), j.group(), at) == 1;
    }

    public record Input(boolean frozen, long activities, long evaluated, long numerator, long denominator, long members) {}
    public Input owned(Job j) {
        return db.query("""
                SELECT frozen_at IS NOT NULL,activities,evaluated,numerator,denominator,active_members
                FROM group_tier_job WHERE basis=? AND group_id=? AND state='RUNNING' AND owner_token=? FOR UPDATE
                """, (rs, i) -> new Input(rs.getBoolean(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6)),
                j.basis(), j.group(), j.token()).stream().findFirst().orElse(null);
    }

    public long active(long group) {
        return db.queryForObject("""
                SELECT COUNT(*) FROM group_member gm JOIN member m ON m.id=gm.member_id
                WHERE gm.group_id=? AND gm.status='ACTIVE' AND gm.deleted_at IS NULL AND m.status='ACTIVE' AND m.deleted_at IS NULL
                """, Long.class, group);
    }

    public void freeze(Job j, LocalDateTime at, Totals t, long active) {
        db.update("""
                UPDATE group_tier_job SET frozen_at=?,activities=?,evaluated=?,numerator=?,denominator=?,active_members=?
                WHERE basis=? AND group_id=? AND owner_token=?
                """, at, t.activities(), t.evaluated(), t.numerator(), t.denominator(), active, j.basis(), j.group(), j.token());
    }

    public void skip(Job j) {
        db.update("UPDATE group_tier_job SET state='SKIPPED',owner_token=NULL WHERE basis=? AND group_id=? AND owner_token=?",
                j.basis(), j.group(), j.token());
    }

    public void apply(Job j, GroupTierPolicy result) {
        int applied = db.update("""
                UPDATE member_group SET tier=?,tier_as_of=? WHERE id=? AND deleted_at IS NULL
                AND status IN ('ACTIVE','ARCHIVED') AND (tier_as_of IS NULL OR tier_as_of<?)
                """, result.tier(), j.basis(), j.group(), j.basis());
        db.update("""
                UPDATE group_tier_job SET state=?,score=?,result=?,failure=NULL,owner_token=NULL,lease_until=NULL
                WHERE basis=? AND group_id=? AND owner_token=?
                """, applied == 1 ? "DONE" : "SKIPPED", result.score(), result.tier(), j.basis(), j.group(), j.token());
    }

    public void fail(Job j, String reason, LocalDateTime retry) {
        db.update("""
                UPDATE group_tier_job SET state='FAILED',failure=?,lease_until=?,owner_token=NULL
                WHERE basis=? AND group_id=? AND owner_token=?
                """, reason, retry, j.basis(), j.group(), j.token());
    }
}
