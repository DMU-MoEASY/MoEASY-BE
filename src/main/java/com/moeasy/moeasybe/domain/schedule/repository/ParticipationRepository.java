package com.moeasy.moeasybe.domain.schedule.repository;

import com.moeasy.moeasybe.domain.schedule.dto.request.ParticipationReqDTO.Entry;
import com.moeasy.moeasybe.domain.schedule.dto.response.ParticipationResDTO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ParticipationRepository {
    private final JdbcTemplate db;
    private static final String ACTIVE_ROSTER = """
            FROM group_member gm JOIN member m ON m.id=gm.member_id
            LEFT JOIN membership_period p ON p.open_member=gm.id
            LEFT JOIN schedule_rsvp r ON r.period_id=p.id AND r.schedule_id=?
            WHERE gm.group_id=? AND gm.status='ACTIVE' AND gm.deleted_at IS NULL
            AND m.status='ACTIVE' AND m.deleted_at IS NULL
            """;

    public List<ParticipationResDTO.Member> responses(long schedule, long group) {
        return db.query("SELECT gm.id,COALESCE(r.status,'UNDECIDED') " + ACTIVE_ROSTER + " ORDER BY gm.id",
                (rs, i) -> new ParticipationResDTO.Member(rs.getLong(1), rs.getString(2)), schedule, group);
    }

    public void respond(long schedule, long membership, String status, LocalDateTime now) {
        long period = db.queryForObject("SELECT id FROM membership_period WHERE open_member=?", Long.class, membership);
        db.update("""
                INSERT INTO schedule_rsvp(schedule_id,group_member_id,period_id,status,responded_at)
                VALUES (?,?,?,?,?)
                ON DUPLICATE KEY UPDATE period_id=VALUES(period_id),status=VALUES(status),responded_at=VALUES(responded_at)
                """, schedule, membership, period, status, now);
    }

    public void withdraw(long schedule, long membership) {
        db.update("DELETE FROM schedule_rsvp WHERE schedule_id=? AND group_member_id=?", schedule, membership);
    }

    // The caller owns the group lock. All three statements commit together.
    public void seal(long group, LocalDateTime from, LocalDateTime until, Long schedule) {
        db.update("""
                INSERT INTO schedule_end_snapshot(schedule_id,ends_at,recoverable)
                SELECT s.id,s.ends_at,s.starts_at>=c.established_at FROM schedule s CROSS JOIN attendance_cutover c
                WHERE s.group_id=? AND s.ends_at>? AND s.ends_at<=? AND s.deleted_at IS NULL
                AND (? IS NULL OR s.id=?) AND NOT EXISTS
                (SELECT 1 FROM schedule_end_snapshot x WHERE x.schedule_id=s.id)
                """, group, from, until, schedule, schedule);
        db.update("""
                INSERT INTO schedule_end_member(schedule_id,group_member_id,attending)
                SELECT s.id,gm.id,COALESCE(r.status='ATTENDING' AND r.responded_at<s.starts_at,FALSE)
                FROM schedule_end_snapshot x JOIN schedule s ON s.id=x.schedule_id
                JOIN group_member gm ON gm.group_id=s.group_id
                JOIN membership_period p ON p.group_member_id=gm.id AND p.starts_at<=x.ends_at
                    AND (p.ends_at IS NULL OR p.ends_at>x.ends_at)
                LEFT JOIN schedule_rsvp r ON r.schedule_id=s.id AND r.period_id=p.id
                WHERE s.group_id=? AND x.sealed=FALSE
                """, group);
        db.update("""
                UPDATE schedule_end_snapshot x JOIN schedule s ON s.id=x.schedule_id
                SET x.member_count=(SELECT COUNT(*) FROM schedule_end_member e WHERE e.schedule_id=s.id), x.sealed=TRUE
                WHERE s.group_id=? AND x.sealed=FALSE
                """, group);
    }

    public boolean recoverable(long schedule) {
        return Boolean.TRUE.equals(db.queryForObject(
                "SELECT recoverable FROM schedule_end_snapshot WHERE schedule_id=?", Boolean.class, schedule));
    }

    public record Session(long id, long version, boolean completed) {}
    public Session session(long schedule) {
        return db.query("SELECT id,version,status='CLOSED' FROM attendance_session WHERE manual_schedule=?",
                (rs, i) -> new Session(rs.getLong(1), rs.getLong(2), rs.getBoolean(3)), schedule)
                .stream().findFirst().orElse(new Session(0, 0, false));
    }

    public Session createSession(long schedule) {
        db.update("INSERT INTO attendance_session(schedule_id,kind) VALUES (?,'MANUAL')", schedule);
        return session(schedule);
    }

    public List<ParticipationResDTO.Record> records(long schedule, long group, boolean ended, LocalDateTime start, long session) {
        String roster = ended ? "SELECT group_member_id id,attending FROM schedule_end_member WHERE schedule_id=?"
                : "SELECT gm.id,COALESCE(r.status='ATTENDING' AND r.responded_at<?,FALSE) attending " + ACTIVE_ROSTER;
        return db.query("SELECT roster.id,COALESCE(a.status,'UNRECORDED'),roster.attending,"
                + "COALESCE(roster.attending AND a.status IN ('PRESENT','LATE'),FALSE) FROM (" + roster
                + ") roster LEFT JOIN attendance a ON a.group_member_id=roster.id AND a.attendance_session_id=? ORDER BY roster.id",
                (rs, i) -> new ParticipationResDTO.Record(rs.getLong(1), rs.getString(2), rs.getBoolean(3), rs.getBoolean(4)),
                ended ? new Object[]{schedule, session} : new Object[]{start, schedule, group, session});
    }

    public void replace(long session, List<Entry> entries, long actor, LocalDateTime now) {
        db.update("DELETE FROM attendance WHERE attendance_session_id=?", session);
        db.batchUpdate("""
                INSERT INTO attendance(attendance_session_id,group_member_id,status,check_in_method,recorded_by,checked_at)
                VALUES (?,?,?,'MANUAL',?,?)
                """, entries.stream().map(e -> new Object[]{session, e.groupMemberId(), e.status().name(), actor, now}).toList());
        db.update("UPDATE attendance_session SET version=version+1 WHERE id=?", session);
    }

    public void complete(long session, long schedule, long actor, LocalDateTime now) {
        db.update("""
                INSERT INTO attendance(attendance_session_id,group_member_id,status,check_in_method,recorded_by,checked_at)
                SELECT ?,e.group_member_id,'ABSENT','MANUAL',?,? FROM schedule_end_member e WHERE e.schedule_id=?
                AND NOT EXISTS (SELECT 1 FROM attendance a WHERE a.attendance_session_id=? AND a.group_member_id=e.group_member_id)
                """, session, actor, now, schedule, session);
        db.update("UPDATE attendance_session SET version=version+1,status='CLOSED',completed_by=?,completed_at=? WHERE id=?",
                actor, now, session);
    }

    public void reopen(long session) {
        db.update("UPDATE attendance_session SET version=version+1,status='ACTIVE',completed_by=NULL,completed_at=NULL WHERE id=?", session);
    }

    public record Totals(long activities, long evaluated, long numerator, long denominator) {}

    public Totals totals(long group, LocalDateTime from, LocalDateTime until) {
        return db.queryForObject("""
                SELECT COUNT(*),COUNT(eligible.schedule_id),COALESCE(SUM(eligible.credited),0),
                       COALESCE(SUM(eligible.member_count),0)
                FROM schedule s LEFT JOIN (
                    SELECT x.schedule_id,x.member_count,COUNT(a.id) credited
                    FROM schedule_end_snapshot x JOIN schedule target ON target.id=x.schedule_id
                    JOIN attendance_session session ON session.manual_schedule=x.schedule_id AND session.status='CLOSED'
                    LEFT JOIN schedule_end_member e ON e.schedule_id=x.schedule_id AND e.attending
                    LEFT JOIN attendance a ON a.attendance_session_id=session.id AND a.group_member_id=e.group_member_id
                         AND a.status IN ('PRESENT','LATE')
                    WHERE target.group_id=? AND x.ends_at>? AND x.ends_at<=?
                      AND x.sealed AND x.recoverable AND x.member_count>=5
                    GROUP BY x.schedule_id,x.member_count
                ) eligible ON eligible.schedule_id=s.id
                WHERE s.group_id=? AND s.ends_at>? AND s.ends_at<=? AND s.status='SCHEDULED' AND s.deleted_at IS NULL
                """, (rs, i) -> new Totals(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
                group, from, until, group, from, until);
    }
}
