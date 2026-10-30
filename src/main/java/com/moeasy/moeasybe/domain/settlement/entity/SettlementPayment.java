package com.moeasy.moeasybe.domain.settlement.entity;

import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentSource;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentType;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "settlement_payment")
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_item_id", nullable = false)
    private SettlementItem settlementItem;

    // 은행 거래 도메인 구현 전까지 ID로 매핑하고 기존 DB 외래키를 유지한다.
    @Column(name = "account_transaction_id")
    private Long accountTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 32)
    private SettlementPaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SettlementPaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SettlementPaymentSource source;

    @Column(nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long amount;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    // 청구 대상이 아니라 기록을 입력한 운영진이다. 기존 기록·시스템 처리는 null을 허용한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_group_member_id")
    private GroupMember recordedByGroupMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_group_member_id")
    private GroupMember cancelledByGroupMember;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private SettlementPayment(SettlementItem settlementItem, Long amount, LocalDateTime occurredAt,
            GroupMember recordedByGroupMember) {
        this.settlementItem = settlementItem;
        this.amount = amount;
        this.occurredAt = occurredAt;
        this.recordedByGroupMember = recordedByGroupMember;
        this.paymentType = SettlementPaymentType.PAYMENT;
        this.status = SettlementPaymentStatus.COMPLETED;
        this.source = SettlementPaymentSource.MANUAL;
    }
}
