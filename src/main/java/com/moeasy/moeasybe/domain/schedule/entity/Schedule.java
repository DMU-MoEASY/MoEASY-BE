package com.moeasy.moeasybe.domain.schedule.entity;

import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.schedule.enums.PlaceProvider;
import com.moeasy.moeasybe.domain.schedule.enums.ScheduleStatus;
import com.moeasy.moeasybe.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;
    @Column(nullable = false, length = 100)
    private String title;
    @Column(nullable = false, length = 500)
    private String description;
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;
    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;
    @Column(name = "location_name", nullable = false, length = 100)
    private String locationName;
    @Column(name = "location_address", nullable = false, length = 255)
    private String locationAddress;
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;
    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;
    @Enumerated(EnumType.STRING)
    @Column(name = "place_provider", length = 32)
    private PlaceProvider placeProvider;
    @Column(name = "place_id", length = 100)
    private String placeId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScheduleStatus status;

    @Column(name = "time_revision", nullable = false)
    private long timeRevision;

    @Builder
    private Schedule(Group group, String title, String description, LocalDateTime startsAt, LocalDateTime endsAt,
            String locationName, String locationAddress, BigDecimal latitude, BigDecimal longitude,
            PlaceProvider placeProvider, String placeId) {
        this.group = group;
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.locationName = locationName;
        this.locationAddress = locationAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.placeProvider = placeProvider;
        this.placeId = placeId;
        this.status = ScheduleStatus.SCHEDULED;
    }

    public void update(String title, String description, LocalDateTime startsAt, LocalDateTime endsAt,
            String locationName, String locationAddress, BigDecimal latitude, BigDecimal longitude,
            PlaceProvider placeProvider, String placeId) {
        this.title = title;
        this.description = description;
        reschedule(startsAt, endsAt);
        this.locationName = locationName;
        this.locationAddress = locationAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.placeProvider = placeProvider;
        this.placeId = placeId;
    }

    public void reschedule(LocalDateTime start, LocalDateTime end) {
        if (!java.util.Objects.equals(startsAt, start) || !java.util.Objects.equals(endsAt, end)) timeRevision++;
        startsAt = start;
        endsAt = end;
    }

    public void cancel() { this.status = ScheduleStatus.CANCELLED; }
    public void softDelete(LocalDateTime now) { markDeletedAt(now); }
}
