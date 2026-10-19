package com.moeasy.moeasybe.domain.settlement.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.exception.SettlementException;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator.Allocation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SettlementSplitCalculatorTest {

    private final SettlementSplitCalculator calculator = new SettlementSplitCalculator();

    @ParameterizedTest
    @MethodSource("equalSplitCases")
    @DisplayName("균등 분할은 원 단위 합계를 보존하고 작은 ID부터 나머지 1원씩 배분한다")
    void calculateEqual_validAmount_returnsExactAllocation(
            long totalAmount, List<Long> ids, List<Allocation> expected) {
        // given
        List<Long> targets = ids;

        // when
        List<Allocation> result = calculator.calculateEqual(totalAmount, targets);

        // then
        assertEquals(expected, result);
    }

    private static Stream<Arguments> equalSplitCases() {
        return Stream.of(
                Arguments.of(10000L, List.of(11L, 12L, 13L), List.of(
                        new Allocation(11L, 3334L, 1L),
                        new Allocation(12L, 3333L, 0L),
                        new Allocation(13L, 3333L, 0L))),
                Arguments.of(500L, List.of(11L, 12L, 13L), List.of(
                        new Allocation(11L, 167L, 1L),
                        new Allocation(12L, 167L, 1L),
                        new Allocation(13L, 166L, 0L))),
                Arguments.of(12000L, List.of(11L, 12L, 13L), List.of(
                        new Allocation(11L, 4000L, 0L),
                        new Allocation(12L, 4000L, 0L),
                        new Allocation(13L, 4000L, 0L))),
                Arguments.of(3L, List.of(11L, 12L, 13L), List.of(
                        new Allocation(11L, 1L, 0L),
                        new Allocation(12L, 1L, 0L),
                        new Allocation(13L, 1L, 0L))),
                Arguments.of(1L, List.of(11L), List.of(new Allocation(11L, 1L, 0L))),
                Arguments.of(Long.MAX_VALUE, List.of(11L), List.of(
                        new Allocation(11L, Long.MAX_VALUE, 0L))),
                Arguments.of(Long.MAX_VALUE, List.of(11L, 12L, 13L), List.of(
                        new Allocation(11L, 3074457345618258603L, 1L),
                        new Allocation(12L, 3074457345618258602L, 0L),
                        new Allocation(13L, 3074457345618258602L, 0L)))
        );
    }

    @ParameterizedTest
    @MethodSource("targetOrders")
    @DisplayName("균등 분할은 요청 순서가 달라도 동일한 회원에게 나머지를 배분한다")
    void calculateEqual_differentInputOrder_returnsSameAllocation(List<Long> ids) {
        // given
        List<Allocation> expected = List.of(
                new Allocation(11L, 167L, 1L),
                new Allocation(12L, 167L, 1L),
                new Allocation(13L, 166L, 0L));

        // when
        List<Allocation> result = calculator.calculateEqual(500L, ids);

        // then
        assertEquals(expected, result);
    }

    private static Stream<List<Long>> targetOrders() {
        return Stream.of(
                List.of(11L, 12L, 13L), List.of(11L, 13L, 12L),
                List.of(12L, 11L, 13L), List.of(12L, 13L, 11L),
                List.of(13L, 11L, 12L), List.of(13L, 12L, 11L));
    }

    @Test
    @DisplayName("균등 분할을 계산해도 요청 대상 목록의 순서를 변경하지 않는다")
    void calculateEqual_mutableInput_preservesInputOrder() {
        // given
        List<Long> ids = new ArrayList<>(List.of(13L, 11L, 12L));

        // when
        calculator.calculateEqual(500L, ids);

        // then
        assertEquals(List.of(13L, 11L, 12L), ids);
    }

    @ParameterizedTest
    @MethodSource("invalidEqualTargets")
    @DisplayName("균등 분할의 빈 대상·잘못된 ID·중복 대상은 거부한다")
    void calculateEqual_invalidTargets_throwsTargetError(List<Long> ids) {
        // given
        Long totalAmount = 10000L;

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> calculator.calculateEqual(totalAmount, ids));

        // then
        assertEquals(SettlementErrorCode.INVALID_TARGET_MEMBERS, exception.getCode());
    }

    private static Stream<List<Long>> invalidEqualTargets() {
        return Stream.of(
                null, List.of(), Arrays.asList((Long) null),
                List.of(0L), List.of(-1L), List.of(11L, 11L));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("균등 분할 총액은 필수 양수다")
    void calculateEqual_invalidTotalAmount_throwsAmountError(Long totalAmount) {
        // given
        List<Long> ids = List.of(11L);

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> calculator.calculateEqual(totalAmount, ids));

        // then
        assertEquals(SettlementErrorCode.INVALID_AMOUNT, exception.getCode());
    }

    @Test
    @DisplayName("균등 분할로 0원 청구가 발생하면 거부한다")
    void calculateEqual_totalLessThanTargetCount_throwsAmountError() {
        // given
        List<Long> ids = List.of(11L, 12L, 13L);

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> calculator.calculateEqual(2L, ids));

        // then
        assertEquals(SettlementErrorCode.INVALID_AMOUNT, exception.getCode());
    }

    @Test
    @DisplayName("회원별 지정 금액은 보정하지 않고 보존하며 나머지 추가분은 0원이다")
    void calculateCustom_matchingTotal_preservesEachAmount() {
        // given
        List<SettlementReqDTO.MemberAmount> amounts = new ArrayList<>(List.of(
                new SettlementReqDTO.MemberAmount(12L, 8000L),
                new SettlementReqDTO.MemberAmount(11L, 12000L)));

        // when
        List<Allocation> result = calculator.calculateCustom(20000L, amounts);

        // then
        assertAll(
                () -> assertEquals(List.of(
                        new Allocation(11L, 12000L, 0L),
                        new Allocation(12L, 8000L, 0L)), result),
                () -> assertEquals(List.of(
                        new SettlementReqDTO.MemberAmount(12L, 8000L),
                        new SettlementReqDTO.MemberAmount(11L, 12000L)), amounts)
        );
    }

    @Test
    @DisplayName("회원별 금액 합계가 Long 최댓값이어도 정확히 일치하면 허용한다")
    void calculateCustom_maximumLongTotal_succeedsWithoutOverflow() {
        // given
        List<SettlementReqDTO.MemberAmount> amounts = List.of(
                new SettlementReqDTO.MemberAmount(11L, Long.MAX_VALUE - 1),
                new SettlementReqDTO.MemberAmount(12L, 1L));

        // when
        List<Allocation> result = calculator.calculateCustom(Long.MAX_VALUE, amounts);

        // then
        assertEquals(List.of(
                new Allocation(11L, Long.MAX_VALUE - 1, 0L),
                new Allocation(12L, 1L, 0L)), result);
    }

    @ParameterizedTest
    @MethodSource("invalidCustomTargets")
    @DisplayName("회원별 지정의 빈 대상·잘못된 ID·중복 대상은 거부한다")
    void calculateCustom_invalidTargets_throwsTargetError(List<SettlementReqDTO.MemberAmount> amounts) {
        // given
        Long totalAmount = 1000L;

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> calculator.calculateCustom(totalAmount, amounts));

        // then
        assertEquals(SettlementErrorCode.INVALID_TARGET_MEMBERS, exception.getCode());
    }

    private static Stream<List<SettlementReqDTO.MemberAmount>> invalidCustomTargets() {
        return Stream.of(
                null, List.of(), Arrays.asList((SettlementReqDTO.MemberAmount) null),
                List.of(new SettlementReqDTO.MemberAmount(null, 1000L)),
                List.of(new SettlementReqDTO.MemberAmount(0L, 1000L)),
                List.of(new SettlementReqDTO.MemberAmount(-1L, 1000L)),
                List.of(new SettlementReqDTO.MemberAmount(11L, 600L),
                        new SettlementReqDTO.MemberAmount(11L, 400L)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("회원별 지정 총액도 필수 양수다")
    void calculateCustom_invalidTotalAmount_throwsAmountError(Long totalAmount) {
        // given
        List<SettlementReqDTO.MemberAmount> amounts = List.of(new SettlementReqDTO.MemberAmount(11L, 1000L));

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> calculator.calculateCustom(totalAmount, amounts));

        // then
        assertEquals(SettlementErrorCode.INVALID_AMOUNT, exception.getCode());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("회원별 청구액은 필수 양수다")
    void calculateCustom_invalidMemberAmount_throwsAmountError(Long amount) {
        // given
        List<SettlementReqDTO.MemberAmount> amounts = List.of(new SettlementReqDTO.MemberAmount(11L, amount));

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> calculator.calculateCustom(1000L, amounts));

        // then
        assertEquals(SettlementErrorCode.INVALID_AMOUNT, exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(longs = {19999L, 20001L})
    @DisplayName("회원별 금액 합계가 총액보다 작거나 크면 거부한다")
    void calculateCustom_mismatchedTotal_throwsAmountError(long totalAmount) {
        // given
        List<SettlementReqDTO.MemberAmount> amounts = List.of(
                new SettlementReqDTO.MemberAmount(11L, 12000L),
                new SettlementReqDTO.MemberAmount(12L, 8000L));

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> calculator.calculateCustom(totalAmount, amounts));

        // then
        assertEquals(SettlementErrorCode.INVALID_AMOUNT, exception.getCode());
    }

    @Test
    @DisplayName("회원별 금액 합계가 Long 범위를 넘으면 순환시키지 않고 거부한다")
    void calculateCustom_sumOverflows_throwsAmountError() {
        // given
        List<SettlementReqDTO.MemberAmount> amounts = List.of(
                new SettlementReqDTO.MemberAmount(11L, Long.MAX_VALUE),
                new SettlementReqDTO.MemberAmount(12L, 1L));

        // when
        SettlementException exception = assertThrows(SettlementException.class,
                () -> calculator.calculateCustom(Long.MAX_VALUE, amounts));

        // then
        assertEquals(SettlementErrorCode.INVALID_AMOUNT, exception.getCode());
    }
}
