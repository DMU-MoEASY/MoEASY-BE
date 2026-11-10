package com.moeasy.moeasybe.domain.schedule.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
@Entity @Table(name = "availability_response") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilityResponse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long availabilityPollId;
    @Column(nullable = false) private Long groupMemberId;
    @Column(nullable = false) private LocalDateTime membershipJoinedAt;
    @Builder private AvailabilityResponse(Long availabilityPollId, Long groupMemberId, LocalDateTime membershipJoinedAt) {
        this.availabilityPollId = availabilityPollId; this.groupMemberId = groupMemberId;
        this.membershipJoinedAt = membershipJoinedAt;
    }
    public void submit(LocalDateTime joinedAt) { membershipJoinedAt = joinedAt; }
}
