package com.moeasy.moeasybe.domain.settlement.entity;

import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementStatus;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "settlement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    // 일정·회비 규칙 도메인이 구현되기 전까지 ID로 연결하고 DB 외래키를 유지한다.
    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "fee_rule_id")
    private Long feeRuleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_group_member_id", nullable = false)
    private GroupMember createdByGroupMember;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "total_amount", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_mode", nullable = false, length = 32)
    private SettlementSplitMode splitMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SettlementStatus status;

    @Builder
    private Settlement(Group group, Long scheduleId, Long feeRuleId, GroupMember createdByGroupMember,
            String title, String description, LocalDateTime dueAt, Long totalAmount,
            SettlementSplitMode splitMode) {
        this.group = group;
        this.scheduleId = scheduleId;
        this.feeRuleId = feeRuleId;
        this.createdByGroupMember = createdByGroupMember;
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
        this.totalAmount = totalAmount;
        this.splitMode = splitMode;
        this.status = SettlementStatus.OPEN;
    }
}
