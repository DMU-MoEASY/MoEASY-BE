package com.moeasy.moeasybe.domain.schedule.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "availability_vote") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilityVote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long availabilitySlotId;
    @Column(nullable = false) private Long groupMemberId;
    @Column(nullable = false) private boolean isAvailable;
    @Builder private AvailabilityVote(Long availabilitySlotId, Long groupMemberId, boolean isAvailable) {
        this.availabilitySlotId = availabilitySlotId; this.groupMemberId = groupMemberId;
        this.isAvailable = isAvailable;
    }
}
