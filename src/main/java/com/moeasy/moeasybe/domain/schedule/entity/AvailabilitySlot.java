package com.moeasy.moeasybe.domain.schedule.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
@Entity @Table(name = "availability_slot") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilitySlot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long availabilityPollId;
    @Column(nullable = false) private LocalDateTime slotAt;
    @Builder private AvailabilitySlot(Long availabilityPollId, LocalDateTime slotAt) {
        this.availabilityPollId = availabilityPollId; this.slotAt = slotAt;
    }
}
