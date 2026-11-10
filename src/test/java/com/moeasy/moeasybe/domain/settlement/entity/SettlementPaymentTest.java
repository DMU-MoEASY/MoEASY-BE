package com.moeasy.moeasybe.domain.settlement.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentStatus;
import com.moeasy.moeasybe.domain.settlement.exception.SettlementException;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementPaymentTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 29, 14, 0);
    private static final LocalDateTime CANCELLED_AT = OCCURRED_AT.plusHours(1);

    @Test
    @DisplayName("수동 납부 취소는 원본 금액·입금 시각·등록자를 보존하고 별도의 취소 정보를 기록한다")
    void cancel_manualPayment_preservesOriginalAndRecordsCancellation() {
        // given
        GroupMember recorder = GroupMember.builder().build();
        GroupMember canceller = GroupMember.builder().build();
        SettlementPayment payment = createPayment(recorder);

        // when
        payment.cancel(canceller, CANCELLED_AT, "  오등록 정정  ");

        // then
        assertAll(
                () -> assertEquals(SettlementPaymentStatus.CANCELLED, payment.getStatus()),
                () -> assertEquals(4000L, payment.getAmount()),
                () -> assertEquals(OCCURRED_AT, payment.getOccurredAt()),
                () -> assertSame(recorder, payment.getRecordedByGroupMember()),
                () -> assertSame(canceller, payment.getCancelledByGroupMember()),
                () -> assertEquals(CANCELLED_AT, payment.getCancelledAt()),
                () -> assertEquals("오등록 정정", payment.getCancelReason())
        );
    }

    @Test
    @DisplayName("이미 취소한 기록은 다시 취소하거나 최초 취소 정보를 덮어쓸 수 없다")
    void cancel_alreadyCancelled_preservesFirstCancellation() {
        // given
        GroupMember canceller = GroupMember.builder().build();
        SettlementPayment payment = createPayment(canceller);
        payment.cancel(canceller, CANCELLED_AT, "오등록 정정");

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> payment.cancel(GroupMember.builder().build(), CANCELLED_AT.plusHours(1), "재취소"));

        // then
        assertAll(
                () -> assertEquals(SettlementErrorCode.PAYMENT_CANCELLATION_NOT_ALLOWED, exception.getCode()),
                () -> assertEquals(SettlementPaymentStatus.CANCELLED, payment.getStatus()),
                () -> assertSame(canceller, payment.getCancelledByGroupMember()),
                () -> assertEquals(CANCELLED_AT, payment.getCancelledAt()),
                () -> assertEquals("오등록 정정", payment.getCancelReason())
        );
    }

    @Test
    @DisplayName("취소 사유가 공백이면 취소 상태와 불완전한 감사 정보를 남기지 않는다")
    void cancel_blankReason_rejectsWithoutMutation() {
        // given
        GroupMember canceller = GroupMember.builder().build();
        SettlementPayment payment = createPayment(canceller);

        // when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> payment.cancel(canceller, CANCELLED_AT, "  "));

        // then
        assertAll(
                () -> assertEquals("취소 사유는 공백이 아닌 1~255자여야 합니다.", exception.getMessage()),
                () -> assertEquals(SettlementPaymentStatus.COMPLETED, payment.getStatus()),
                () -> assertNull(payment.getCancelledByGroupMember()),
                () -> assertNull(payment.getCancelledAt()),
                () -> assertNull(payment.getCancelReason())
        );
    }

    private SettlementPayment createPayment(GroupMember recorder) {
        return SettlementPayment.builder().amount(4000L).occurredAt(OCCURRED_AT)
                .recordedByGroupMember(recorder).build();
    }
}
