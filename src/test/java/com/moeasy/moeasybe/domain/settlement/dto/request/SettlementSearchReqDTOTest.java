package com.moeasy.moeasybe.domain.settlement.dto.request;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moeasy.moeasybe.domain.settlement.enums.SettlementStatus;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.ResolvableType;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.ServletRequestDataBinder;

class SettlementSearchReqDTOTest {

    private static LocalValidatorFactoryBean validator;

    @BeforeAll
    static void setUpValidator() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
    }

    @AfterAll
    static void closeValidator() {
        validator.close();
    }

    @Test
    @DisplayName("목록 조건을 생략하면 첫 페이지 20개와 전체 상태·일정 조건을 사용한다")
    void search_noParameters_appliesDefaults() {
        // given
        Map<String, String> parameters = Map.of();

        // when
        ServletRequestDataBinder binder = bind(parameters);
        var request = (SettlementReqDTO.Search) binder.getTarget();

        // then
        assertAll(
                () -> assertFalse(binder.getBindingResult().hasErrors()),
                () -> assertEquals(0, request.page()),
                () -> assertEquals(20, request.size()),
                () -> assertNull(request.status()),
                () -> assertNull(request.scheduleId())
        );
    }

    @ParameterizedTest
    @MethodSource("partialPagination")
    @DisplayName("페이지 번호·크기의 기본값은 생략된 필드에만 적용한다")
    void search_partialPagination_preservesProvidedValue(Map<String, String> parameters,
            int expectedPage, int expectedSize) {
        // given
        Map<String, String> query = parameters;

        // when
        ServletRequestDataBinder binder = bind(query);
        var request = (SettlementReqDTO.Search) binder.getTarget();

        // then
        assertAll(
                () -> assertFalse(binder.getBindingResult().hasErrors()),
                () -> assertEquals(expectedPage, request.page()),
                () -> assertEquals(expectedSize, request.size())
        );
    }

    private static Stream<Arguments> partialPagination() {
        return Stream.of(
                Arguments.of(Map.of("page", "2"), 2, 20),
                Arguments.of(Map.of("size", "1"), 0, 1)
        );
    }

    @ParameterizedTest
    @MethodSource("validConditions")
    @DisplayName("페이지 경계값·정산 상태·양수 일정 ID를 쿼리 문자열에서 변환한다")
    void search_validConditions_bindsAndValidates(int page, int size, SettlementStatus status,
            long scheduleId) {
        // given
        Map<String, String> parameters = Map.of(
                "page", Integer.toString(page), "size", Integer.toString(size),
                "status", status.name(), "scheduleId", Long.toString(scheduleId));

        // when
        ServletRequestDataBinder binder = bind(parameters);
        var request = (SettlementReqDTO.Search) binder.getTarget();

        // then
        assertAll(
                () -> assertFalse(binder.getBindingResult().hasErrors()),
                () -> assertEquals(page, request.page()),
                () -> assertEquals(size, request.size()),
                () -> assertEquals(status, request.status()),
                () -> assertEquals(scheduleId, request.scheduleId())
        );
    }

    private static Stream<Arguments> validConditions() {
        return Stream.of(
                Arguments.of(0, 1, SettlementStatus.OPEN, 1L),
                Arguments.of(Integer.MAX_VALUE, 100, SettlementStatus.CLOSED, Long.MAX_VALUE),
                Arguments.of(2, 20, SettlementStatus.CANCELLED, 7L)
        );
    }

    @ParameterizedTest
    @MethodSource("outOfRangeConditions")
    @DisplayName("잘못된 페이지 범위와 양수가 아닌 일정 ID를 기본값으로 보정하지 않는다")
    void search_outOfRange_reportsValidationError(String field, String value) {
        // given
        Map<String, String> parameters = Map.of(field, value);

        // when
        ServletRequestDataBinder binder = bind(parameters);

        // then
        assertTrue(binder.getBindingResult().hasFieldErrors(field));
    }

    private static Stream<Arguments> outOfRangeConditions() {
        return Stream.of(
                Arguments.of("page", "-1"),
                Arguments.of("size", "0"),
                Arguments.of("size", "-1"),
                Arguments.of("size", "101"),
                Arguments.of("scheduleId", "0"),
                Arguments.of("scheduleId", "-1")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidParameterTypes")
    @DisplayName("문자열·소수·정수 범위 초과·알 수 없는 상태는 바인딩 오류로 거부한다")
    void search_invalidParameterType_reportsBindingFailure(String field, String value) {
        // given
        Map<String, String> parameters = Map.of(field, value);

        // when
        ServletRequestDataBinder binder = bind(parameters);

        // then
        assertAll(
                () -> assertTrue(binder.getBindingResult().hasFieldErrors(field)),
                () -> assertTrue(binder.getBindingResult().getFieldError(field).isBindingFailure())
        );
    }

    private static Stream<Arguments> invalidParameterTypes() {
        return Stream.of(
                Arguments.of("page", "abc"),
                Arguments.of("page", "2147483648"),
                Arguments.of("size", "1.5"),
                Arguments.of("scheduleId", "9223372036854775808"),
                Arguments.of("scheduleId", "1e2"),
                Arguments.of("status", "UNKNOWN"),
                Arguments.of("status", "open"),
                Arguments.of("status", "0")
        );
    }

    private ServletRequestDataBinder bind(Map<String, String> parameters) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        parameters.forEach(request::addParameter);
        ServletRequestDataBinder binder = new ServletRequestDataBinder(null, "search");
        binder.setTargetType(ResolvableType.forClass(SettlementReqDTO.Search.class));
        binder.setConversionService(new DefaultFormattingConversionService());
        binder.setValidator(validator);
        binder.construct(request);
        if (!binder.getBindingResult().hasErrors()) {
            binder.validate();
        }
        return binder;
    }
}
