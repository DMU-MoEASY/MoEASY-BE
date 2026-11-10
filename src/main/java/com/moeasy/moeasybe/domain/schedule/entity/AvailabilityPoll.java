package com.moeasy.moeasybe.domain.schedule.entity;

import com.moeasy.moeasybe.domain.schedule.enums.AvailabilityStatus;
import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Entity
@Table(name = "availability_poll")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilityPoll {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true) private Long scheduleId;
    @Column(nullable = false) private LocalDateTime pollStartAt;
    @Column(nullable = false) private LocalDateTime pollEndAt;
    @Column(nullable = false) private LocalDateTime voteDeadlineAt;
    @Column(nullable = false) private byte slotMinutes;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AvailabilityStatus status;
    @Column(nullable = false) private LocalDate startDate;
    @Column(nullable = false) private int dayCount;
    @Column(nullable = false) private int startHour;
    @Column(nullable = false) private int endHour;
    @Column(nullable = false) private long revision;
    private LocalDateTime confirmedStartAt;
    private LocalDateTime confirmedEndAt;
    private LocalDateTime originalStartAt;
    private LocalDateTime originalEndAt;
    private Long confirmedTimeRevision;
    private String confirmationToken;

    @Builder
    private AvailabilityPoll(Long scheduleId, LocalDate startDate, int dayCount, int startHour,
            int endHour, LocalDateTime voteDeadlineAt) {
        this.scheduleId = scheduleId;
        this.slotMinutes = 30;
        configure(startDate, dayCount, startHour, endHour, voteDeadlineAt);
    }
    public void configure(LocalDate date, int days, int from, int to, LocalDateTime deadline) {
        startDate = date; dayCount = days; startHour = from; endHour = to; voteDeadlineAt = deadline;
        pollStartAt = date.atStartOfDay().plusHours(from);
        pollEndAt = date.plusDays(days - 1).atStartOfDay().plusHours(to);
        status = AvailabilityStatus.OPEN;
        confirmedStartAt = null; confirmedEndAt = null; originalStartAt = null; originalEndAt = null;
        confirmedTimeRevision = null; confirmationToken = null;
        touch();
    }
    public AvailabilityStatus effectiveStatus(LocalDateTime now) {
        return status == AvailabilityStatus.OPEN && !voteDeadlineAt.isAfter(now) ? AvailabilityStatus.CLOSED : status;
    }
    public void closeIfDue(LocalDateTime now) {
        if (status == AvailabilityStatus.OPEN && !voteDeadlineAt.isAfter(now)) {
            status = AvailabilityStatus.CLOSED; touch();
        }
    }
    public void confirm(LocalDateTime start, LocalDateTime end, LocalDateTime originalStart,
            LocalDateTime originalEnd, long timeRevision, String token) {
        confirmedStartAt = start; confirmedEndAt = end; originalStartAt = originalStart;
        originalEndAt = originalEnd; confirmedTimeRevision = timeRevision; confirmationToken = token;
        status = AvailabilityStatus.CONFIRMED; touch();
    }
    public void touch() { revision++; }
}
