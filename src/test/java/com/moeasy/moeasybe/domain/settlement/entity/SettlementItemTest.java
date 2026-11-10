package com.moeasy.moeasybe.domain.settlement.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moeasy.moeasybe.domain.settlement.enums.SettlementItemStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementStatus;
import com.moeasy.moeasybe.domain.settlement.exception.SettlementException;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class SettlementItemTest {

    @ParameterizedTest
    @CsvSource({
            "10000, 0, 4000, 4000, PENDING",
            "10000, 4000, 6000, 10000, PAID",
            "1, 0, 1, 1, PAID",
            "9223372036854775807, 0, 9223372036854775807, 9223372036854775807, PAID",
            "9223372036854775807, 9223372036854775806, 1, 9223372036854775807, PAID"
    })
    @DisplayName("납부를 누적하고 부분 납부·완납 상태와 버전을 갱신하되 정산을 자동 마감하지 않는다")
    void applyPayment_validAmount_updatesItemAndReturnsPaidAmount(
            long amount, long currentPaid, long payment, long expectedPaid, SettlementItemStatus expectedStatus) {
        // given
        SettlementItem item = createItem(amount);

        // when
        long paidAmount = item.applyPayment(0L, currentPaid, payment);

        // then
        assertAll(
                () -> assertEquals(expectedPaid, paidAmount),
                () -> assertEquals(expectedStatus, item.getStatus()),
                () -> assertEquals(1L, item.getVersion()),
                () -> assertEquals(amount, item.getAmount()),
                () -> assertEquals(SettlementStatus.OPEN, item.getSettlement().getStatus())
        );
    }

    @Test
    @DisplayName("연속 부분 납부에서도 매번 버전이 증가하고 마지막 납부에서 완납으로 바뀐다")
    void applyPayment_successivePayments_incrementsEveryVersion() {
        // given
        SettlementItem item = createItem(10000L);
        long firstPaid = item.applyPayment(0L, 0L, 3000L);
        long secondPaid = item.applyPayment(1L, firstPaid, 1000L);

        // when
        long finalPaid = item.applyPayment(2L, secondPaid, 6000L);

        // then
        assertAll(
                () -> assertEquals(10000L, finalPaid),
                () -> assertEquals(SettlementItemStatus.PAID, item.getStatus()),
                () -> assertEquals(3L, item.getVersion())
        );
    }

    @Test
    @DisplayName("이미 반영한 요청의 이전 버전은 다시 반영하지 않고 기존 상태·버전을 유지한다")
    void applyPayment_staleRetry_rejectsWithoutMutation() {
        // given
        SettlementItem item = createItem(10000L);
        long paidAmount = item.applyPayment(0L, 0L, 4000L);

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> item.applyPayment(0L, paidAmount, 4000L));

        // then
        assertAll(
                () -> assertEquals(SettlementErrorCode.ITEM_VERSION_CONFLICT, exception.getCode()),
                () -> assertEquals(SettlementItemStatus.PENDING, item.getStatus()),
                () -> assertEquals(1L, item.getVersion())
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 1L, Long.MAX_VALUE})
    @DisplayName("현재 청구 버전과 다르면 상태·버전을 변경하지 않는다")
    void applyPayment_mismatchedVersion_rejectsWithoutMutation(long expectedVersion) {
        // given
        SettlementItem item = createItem(10000L);

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> item.applyPayment(expectedVersion, 0L, 4000L));

        // then
        assertUnchanged(item, exception, SettlementErrorCode.ITEM_VERSION_CONFLICT);
    }

    @ParameterizedTest
    @CsvSource({
            "10000, 0, 10001", "10000, 4000, 6001",
            "9223372036854775807, 1, 9223372036854775807"
    })
    @DisplayName("잔액 초과는 합산 전에 거부해 금액 오버플로와 상태 변경을 막는다")
    void applyPayment_excessAmount_rejectsBeforeAddition(long amount, long currentPaid, long payment) {
        // given
        SettlementItem item = createItem(amount);

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> item.applyPayment(0L, currentPaid, payment));

        // then
        assertUnchanged(item, exception, SettlementErrorCode.PAYMENT_AMOUNT_EXCEEDS_REMAINING);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "0, -1", "-1, 1000", "10001, 1000"})
    @DisplayName("양수가 아닌 납부액과 범위를 벗어난 기존 납부 합계를 거부한다")
    void applyPayment_invalidAmount_rejectsWithoutMutation(long currentPaid, long payment) {
        // given
        SettlementItem item = createItem(10000L);

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> item.applyPayment(0L, currentPaid, payment));

        // then
        assertUnchanged(item, exception, SettlementErrorCode.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("완납한 청구에 추가 납부할 수 없고 PAID 상태와 버전을 유지한다")
    void applyPayment_fullyPaidItem_rejectsExtraPayment() {
        // given
        SettlementItem item = createItem(10000L);
        long paidAmount = item.applyPayment(0L, 0L, 10000L);

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> item.applyPayment(1L, paidAmount, 1L));

        // then
        assertAll(
                () -> assertEquals(SettlementErrorCode.PAYMENT_AMOUNT_EXCEEDS_REMAINING, exception.getCode()),
                () -> assertEquals(SettlementItemStatus.PAID, item.getStatus()),
                () -> assertEquals(1L, item.getVersion())
        );
    }

    @Test
    @DisplayName("삭제된 정산의 청구는 납부를 반영하지 않는다")
    void applyPayment_deletedSettlement_rejectsWithoutMutation() {
        // given
        SettlementItem item = createItem(10000L);
        ReflectionTestUtils.setField(item.getSettlement(), "deletedAt", LocalDateTime.of(2026, 10, 29, 12, 0));

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> item.applyPayment(0L, 0L, 1000L));

        // then
        assertUnchanged(item, exception, SettlementErrorCode.SETTLEMENT_NOT_FOUND);
    }

    @ParameterizedTest
    @CsvSource({"10000, 4000, 6000", "6000, 4000, 2000", "4000, 4000, 0"})
    @DisplayName("완납·부분 납부에서 기록을 취소하면 남은 납부액과 미납 상태·버전을 갱신한다")
    void cancelPayment_validAmount_updatesPaidAmountAndVersion(long currentPaid, long cancelled, long expectedPaid) {
        // given
        SettlementItem item = createItem(10000L);
        item.applyPayment(0L, 0L, currentPaid);

        // when
        long paidAmount = item.cancelPayment(1L, currentPaid, cancelled);

        // then
        assertAll(
                () -> assertEquals(expectedPaid, paidAmount),
                () -> assertEquals(SettlementItemStatus.PENDING, item.getStatus()),
                () -> assertEquals(2L, item.getVersion()),
                () -> assertEquals(10000L, item.getAmount()),
                () -> assertEquals(SettlementStatus.OPEN, item.getSettlement().getStatus())
        );
    }

    @ParameterizedTest
    @CsvSource({"4000, 0", "4000, 4001", "10001, 4000"})
    @DisplayName("0원 취소·납부 합계 초과 취소·청구액을 넘는 기존 합계는 상태 변경 없이 거부한다")
    void cancelPayment_invalidAmount_rejectsWithoutMutation(long currentPaid, long cancelled) {
        // given
        SettlementItem item = createItem(10000L);

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> item.cancelPayment(0L, currentPaid, cancelled));

        // then
        assertUnchanged(item, exception, SettlementErrorCode.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("취소도 현재 청구 버전이 아니면 기존 완납 상태와 버전을 유지한다")
    void cancelPayment_staleVersion_rejectsWithoutMutation() {
        // given
        SettlementItem item = createItem(10000L);
        item.applyPayment(0L, 0L, 10000L);

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> item.cancelPayment(0L, 10000L, 4000L));

        // then
        assertAll(
                () -> assertEquals(SettlementErrorCode.ITEM_VERSION_CONFLICT, exception.getCode()),
                () -> assertEquals(SettlementItemStatus.PAID, item.getStatus()),
                () -> assertEquals(1L, item.getVersion())
        );
    }

    private SettlementItem createItem(long amount) {
        Settlement settlement = Settlement.builder().title("회비").totalAmount(amount)
                .splitMode(SettlementSplitMode.EQUAL).dueAt(LocalDateTime.of(2000, 1, 1, 0, 0)).build();
        return SettlementItem.builder().settlement(settlement).amount(amount).build();
    }

    private void assertUnchanged(SettlementItem item, SettlementException exception, SettlementErrorCode code) {
        assertAll(
                () -> assertEquals(code, exception.getCode()),
                () -> assertEquals(SettlementItemStatus.PENDING, item.getStatus()),
                () -> assertEquals(0L, item.getVersion())
        );
    }
}
