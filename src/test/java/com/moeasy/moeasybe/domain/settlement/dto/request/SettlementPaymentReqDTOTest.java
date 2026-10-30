package com.moeasy.moeasybe.domain.settlement.dto.request;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class SettlementPaymentReqDTOTest {

    private static final String PAYMENT_JSON = """
            {"amount":4000,"expectedVersion":0,"occurredAt":"2026-10-29T14:00:00"}
            """;
    private static ValidatorFactory validatorFactory;
    private static Validator validator;
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("납부 등록은 원 단위 금액·최초 청구 버전 0·입금 시각을 읽는다")
    void registerPayment_validRequest_deserializesAndValidates() {
        // given
        String json = PAYMENT_JSON;

        // when
        var request = objectMapper.readValue(json, SettlementReqDTO.RegisterPayment.class);
        var violations = validator.validate(request);

        // then
        assertAll(
                () -> assertTrue(violations.isEmpty()),
                () -> assertEquals(4000L, request.amount()),
                () -> assertEquals(0L, request.expectedVersion()),
                () -> assertEquals(LocalDateTime.of(2026, 10, 29, 14, 0), request.occurredAt())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"amount\":1,\"expectedVersion\":0}",
            "{\"amount\":1,\"expectedVersion\":0,\"occurredAt\":null}"})
    @DisplayName("입금 시각을 생략하거나 null로 보내면 DTO는 서버 시각을 임의로 채우지 않는다")
    void registerPayment_omittedOrNullOccurredAt_leavesTimeForService(String json) {
        // given
        var request = objectMapper.readValue(json, SettlementReqDTO.RegisterPayment.class);

        // when
        var violations = validator.validate(request);

        // then
        assertAll(() -> assertTrue(violations.isEmpty()), () -> assertNull(request.occurredAt()));
    }

    @Test
    @DisplayName("금액과 청구 버전의 Long 최댓값을 정밀도 손실 없이 읽는다")
    void registerPayment_maximumLong_preservesIntegerValues() {
        // given
        String json = """
                {"amount":9223372036854775807,"expectedVersion":9223372036854775807}
                """;

        // when
        var request = objectMapper.readValue(json, SettlementReqDTO.RegisterPayment.class);
        var violations = validator.validate(request);

        // then
        assertAll(
                () -> assertTrue(violations.isEmpty()),
                () -> assertEquals(Long.MAX_VALUE, request.amount()),
                () -> assertEquals(Long.MAX_VALUE, request.expectedVersion())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidConstraints")
    @DisplayName("금액·버전의 누락·null·허용 범위 위반을 필드 검증으로 거부한다")
    void registerPayment_invalidField_reportsConstraintViolation(String json, String path) {
        // given
        var request = objectMapper.readValue(json, SettlementReqDTO.RegisterPayment.class);

        // when
        var violations = validator.validate(request);

        // then
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(path)));
    }

    private static Stream<Arguments> invalidConstraints() {
        return Stream.of(
                Arguments.of("{\"expectedVersion\":0}", "amount"),
                Arguments.of("{\"amount\":null,\"expectedVersion\":0}", "amount"),
                Arguments.of("{\"amount\":0,\"expectedVersion\":0}", "amount"),
                Arguments.of("{\"amount\":-1,\"expectedVersion\":0}", "amount"),
                Arguments.of("{\"amount\":1}", "expectedVersion"),
                Arguments.of("{\"amount\":1,\"expectedVersion\":null}", "expectedVersion"),
                Arguments.of("{\"amount\":1,\"expectedVersion\":-1}", "expectedVersion")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidJsonFields")
    @DisplayName("금액·버전의 숫자 문자열·소수·지수·타입 오류·오버플로를 자동 변환하지 않는다")
    void registerPayment_invalidJson_rejectsCoercion(String field, String value) {
        // given
        String json = withField(field, value);

        // when
        JacksonException exception = assertThrows(JacksonException.class,
                () -> objectMapper.readValue(json, SettlementReqDTO.RegisterPayment.class));

        // then
        assertTrue(exception.getMessage().contains(field));
    }

    private static Stream<Arguments> invalidJsonFields() {
        return Stream.of("amount", "expectedVersion").flatMap(field ->
                Stream.of("\"4000\"", "1.5", "1.0", "1e3", "true", "[]", "9223372036854775808")
                        .map(value -> Arguments.of(field, value)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000-01-01T00:00:00", "9999-12-31T23:59:59"})
    @DisplayName("입금 시각은 DB 연도 경계값을 읽고 현재 시각과의 비교는 서비스에 맡긴다")
    void registerPayment_occurredAtDatabaseBoundary_preservesDate(String occurredAt) {
        // given
        String json = withField("occurredAt", objectMapper.writeValueAsString(occurredAt));

        // when
        var request = objectMapper.readValue(json, SettlementReqDTO.RegisterPayment.class);

        // then
        assertEquals(LocalDateTime.parse(occurredAt), request.occurredAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"0999-12-31T23:59:59\"", "\"+10000-01-01T00:00:00\"",
            "\"2026-02-30T12:00:00\"", "\"2026-10-29T14:00\"", "\"2026-10-29T14:00:00.123\"",
            "\"2026-10-29T14:00:00Z\"", "\"\"", "123", "[2026,10,29,14,0,0]"})
    @DisplayName("입금 시각은 DB 범위 안의 유효한 초 단위 문자열만 허용한다")
    void registerPayment_invalidOccurredAt_rejectsDate(String value) {
        // given
        String json = withField("occurredAt", value);

        // when
        JacksonException exception = assertThrows(JacksonException.class,
                () -> objectMapper.readValue(json, SettlementReqDTO.RegisterPayment.class));

        // then
        assertTrue(exception.getMessage().contains("occurredAt"));
    }

    private String withField(String field, String jsonValue) {
        ObjectNode body = (ObjectNode) objectMapper.readTree(PAYMENT_JSON);
        body.set(field, objectMapper.readTree(jsonValue));
        return objectMapper.writeValueAsString(body);
    }
}
