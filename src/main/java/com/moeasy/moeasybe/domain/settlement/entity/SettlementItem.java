package com.moeasy.moeasybe.domain.settlement.entity;

import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementItemStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementStatus;
import com.moeasy.moeasybe.domain.settlement.exception.SettlementException;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "settlement_item", uniqueConstraints = {
        @UniqueConstraint(name = "uq_settlement_item_member", columnNames = {"settlement_id", "group_member_id"})
})
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private Settlement settlement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMember groupMember;

    @Column(nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SettlementItemStatus status;

    // 납부 변경 시 행 잠금 안에서 명시적으로 증가시킬 업무 버전이다.
    @Column(nullable = false)
    private Long version = 0L;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private SettlementItem(Settlement settlement, GroupMember groupMember, Long amount) {
        this.settlement = settlement;
        this.groupMember = groupMember;
        this.amount = amount;
        this.status = SettlementItemStatus.PENDING;
    }

    /**
     * 행 잠금을 획득한 서비스가 유효 납부 기록의 최신 합계를 전달해야 한다.
     * 납부액 자체는 저장하지 않고, 반영 후 누적 납부액을 반환한다.
     */
    public long applyPayment(long expectedVersion, long currentPaidAmount, long paymentAmount) {
        validatePaymentChange(expectedVersion);
        if (paymentAmount <= 0 || currentPaidAmount < 0 || currentPaidAmount > amount) {
            throw new SettlementException(SettlementErrorCode.INVALID_AMOUNT);
        }

        long remainingAmount = amount - currentPaidAmount;
        if (paymentAmount > remainingAmount) {
            throw new SettlementException(SettlementErrorCode.PAYMENT_AMOUNT_EXCEEDS_REMAINING);
        }

        // 잔액 이하임을 먼저 확인했으므로 합계가 청구액·Long 범위를 넘지 않는다.
        long paidAmount = currentPaidAmount + paymentAmount;
        status = paidAmount == amount ? SettlementItemStatus.PAID : SettlementItemStatus.PENDING;
        version++;
        return paidAmount;
    }

    /**
     * 행 잠금 안에서 취소 전 유효 납부 합계와 취소할 기록의 원본 금액을 전달해야 한다.
     * 납부 기록 자체의 취소와 이 변경은 서비스의 같은 트랜잭션에서 처리한다.
     */
    public long cancelPayment(long expectedVersion, long currentPaidAmount, long paymentAmount) {
        validatePaymentChange(expectedVersion);
        if (paymentAmount <= 0 || currentPaidAmount < 0 || currentPaidAmount > amount
                || paymentAmount > currentPaidAmount) {
            throw new SettlementException(SettlementErrorCode.INVALID_AMOUNT);
        }

        long paidAmount = currentPaidAmount - paymentAmount;
        status = SettlementItemStatus.PENDING;
        version++;
        return paidAmount;
    }

    private void validatePaymentChange(long expectedVersion) {
        if (settlement.getDeletedAt() != null) {
            throw new SettlementException(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        }
        if (settlement.getStatus() != SettlementStatus.OPEN
                || (status != SettlementItemStatus.PENDING && status != SettlementItemStatus.PAID)) {
            throw new SettlementException(SettlementErrorCode.PAYMENT_NOT_ALLOWED);
        }
        if (version != expectedVersion || version == Long.MAX_VALUE) {
            throw new SettlementException(SettlementErrorCode.ITEM_VERSION_CONFLICT);
        }
    }
}
