package com.moeasy.moeasybe.domain.group.entity;

import com.moeasy.moeasybe.domain.group.enums.GroupJoinRequestStatus;
import com.moeasy.moeasybe.domain.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "group_join_request")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupJoinRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 500)
    private String introduction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GroupJoinRequestStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_group_member_id")
    private GroupMember processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private GroupJoinRequest(Group group, Member member, String introduction, GroupJoinRequestStatus status) {
        this.group = group;
        this.member = member;
        this.introduction = introduction;
        this.status = status;
    }

    @PrePersist
    @PreUpdate
    private void normalizeAuditPrecision() {
        createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
        updatedAt = updatedAt.truncatedTo(ChronoUnit.MICROS);
    }

    public void approve(GroupMember processor, LocalDateTime at) {
        status = GroupJoinRequestStatus.APPROVED;
        processedBy = processor;
        processedAt = at;
    }

    public void reject(GroupMember processor, String reason, LocalDateTime at) {
        status = GroupJoinRequestStatus.REJECTED;
        rejectionReason = reason;
        processedBy = processor;
        processedAt = at;
    }

    public void cancel(LocalDateTime at) {
        status = GroupJoinRequestStatus.CANCELLED;
        processedBy = null;
        processedAt = at;
    }
}
