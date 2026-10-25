package com.moeasy.moeasybe.domain.settlement.dto.request;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
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

class SettlementReqDTOTest {

    private static final String EQUAL_JSON = """
            {
              "title": "10월 모임 회비",
              "splitMode": "EQUAL",
              "totalAmount": 10000,
              "groupMemberIds": [11, 12, 13]
            }
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
    @DisplayName("균등 분할 요청은 선택 필드 없이 읽고 검증할 수 있다")
    void create_equalRequest_deserializesAndValidates() {
        // given
        String json = EQUAL_JSON;

        // when
        SettlementReqDTO.Create request = objectMapper.readValue(json, SettlementReqDTO.Create.class);
        var violations = validator.validate(request);

        // then
        assertAll(
                () -> assertTrue(violations.isEmpty()),
                () -> assertEquals(SettlementSplitMode.EQUAL, request.splitMode()),
                () -> assertEquals(10000L, request.totalAmount()),
                () -> assertEquals(3, request.groupMemberIds().size())
        );
    }

    @Test
    @DisplayName("회원별 지정 요청은 Long 최대 금액을 반올림 없이 보존한다")
    void create_customRequest_preservesMaximumLongAmount() {
        // given
        String json = """
                {
                  "title": "개별 정산",
                  "scheduleId": 7,
                  "dueAt": "2026-11-01T23:59:59",
                  "splitMode": "CUSTOM",
                  "totalAmount": 9223372036854775807,
                  "memberAmounts": [{"groupMemberId": 11, "amount": 9223372036854775807}]
                }
                """;

        // when
        SettlementReqDTO.Create request = objectMapper.readValue(json, SettlementReqDTO.Create.class);
        var violations = validator.validate(request);

        // then
        assertAll(
                () -> assertTrue(violations.isEmpty()),
                () -> assertEquals(SettlementSplitMode.CUSTOM, request.splitMode()),
                () -> assertEquals(7L, request.scheduleId()),
                () -> assertEquals(LocalDateTime.of(2026, 11, 1, 23, 59, 59), request.dueAt()),
                () -> assertEquals(Long.MAX_VALUE, request.totalAmount()),
                () -> assertEquals(Long.MAX_VALUE, request.memberAmounts().getFirst().amount())
        );
    }

    @Test
    @DisplayName("제목은 앞뒤 공백을 제거한 뒤 길이를 검증한다")
    void create_titleWithOuterSpaces_trimsBeforeValidation() {
        // given
        String title = "가".repeat(100);
        String json = withField("title", objectMapper.writeValueAsString("  " + title + "  "));

        // when
        SettlementReqDTO.Create request = objectMapper.readValue(json, SettlementReqDTO.Create.class);
        var violations = validator.validate(request);

        // then
        assertAll(
                () -> assertEquals(title, request.title()),
                () -> assertTrue(violations.isEmpty())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    @DisplayName("필수값·길이·양수·빈 배열·중첩 항목 오류는 DTO 검증으로 거부한다")
    void create_invalidField_reportsConstraintViolation(String field, String value, String expectedPath) {
        // given
        String json = withField(field, value);
        SettlementReqDTO.Create request = objectMapper.readValue(json, SettlementReqDTO.Create.class);

        // when
        var violations = validator.validate(request);

        // then
        assertTrue(violations.stream().anyMatch(
                violation -> violation.getPropertyPath().toString().startsWith(expectedPath)));
    }

    private static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("title", "null", "title"),
                Arguments.of("title", "\"\"", "title"),
                Arguments.of("title", "\"   \"", "title"),
                Arguments.of("title", "\"" + "가".repeat(101) + "\"", "title"),
                Arguments.of("description", "\"" + "가".repeat(501) + "\"", "description"),
                Arguments.of("splitMode", "null", "splitMode"),
                Arguments.of("scheduleId", "0", "scheduleId"),
                Arguments.of("totalAmount", "null", "totalAmount"),
                Arguments.of("totalAmount", "0", "totalAmount"),
                Arguments.of("totalAmount", "-1", "totalAmount"),
                Arguments.of("groupMemberIds", "[]", "groupMemberIds"),
                Arguments.of("groupMemberIds", "[null]", "groupMemberIds"),
                Arguments.of("groupMemberIds", "[0]", "groupMemberIds"),
                Arguments.of("groupMemberIds", "[-1]", "groupMemberIds"),
                Arguments.of("memberAmounts", "[]", "memberAmounts"),
                Arguments.of("memberAmounts", "[null]", "memberAmounts"),
                Arguments.of("memberAmounts", "[{\"amount\":1}]", "memberAmounts[0].groupMemberId"),
                Arguments.of("memberAmounts", "[{\"groupMemberId\":0,\"amount\":1}]", "memberAmounts[0].groupMemberId"),
                Arguments.of("memberAmounts", "[{\"groupMemberId\":11}]", "memberAmounts[0].amount"),
                Arguments.of("memberAmounts", "[{\"groupMemberId\":11,\"amount\":0}]", "memberAmounts[0].amount"),
                Arguments.of("memberAmounts", "[{\"groupMemberId\":11,\"amount\":-1}]", "memberAmounts[0].amount")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidJsonFields")
    @DisplayName("잘못된 JSON 타입과 표현은 자동 변환하지 않고 거부한다")
    void create_invalidJsonValue_rejectsWithoutCoercion(String field, String value) {
        // given
        String json = withField(field, value);

        // when
        JacksonException exception = assertThrows(JacksonException.class,
                () -> objectMapper.readValue(json, SettlementReqDTO.Create.class));

        // then
        assertTrue(exception.getMessage().contains(field));
    }

    private static Stream<Arguments> invalidJsonFields() {
        return Stream.of(
                Arguments.of("totalAmount", "1.5"),
                Arguments.of("totalAmount", "100.0"),
                Arguments.of("totalAmount", "1e3"),
                Arguments.of("totalAmount", "\"10000\""),
                Arguments.of("totalAmount", "true"),
                Arguments.of("totalAmount", "9223372036854775808"),
                Arguments.of("totalAmount", "-9223372036854775809"),
                Arguments.of("scheduleId", "1.5"),
                Arguments.of("groupMemberIds", "[11.5]"),
                Arguments.of("memberAmounts", "[{\"groupMemberId\":11,\"amount\":1.5}]"),
                Arguments.of("memberAmounts", "[{\"groupMemberId\":11.5,\"amount\":1000}]"),
                Arguments.of("splitMode", "\"UNKNOWN\""),
                Arguments.of("splitMode", "0"),
                Arguments.of("dueAt", "[]"),
                Arguments.of("dueAt", "[2026,11,1,12,0,0]"),
                Arguments.of("dueAt", "\"\""),
                Arguments.of("dueAt", "123")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000-01-01T00:00:00", "9999-12-31T23:59:59"})
    @DisplayName("납부 기한의 DB 저장 가능 연도 경계값을 손실 없이 읽는다")
    void create_dueAtWithinDatabaseRange_preservesDate(String dueAt) {
        // given
        String json = withField("dueAt", objectMapper.writeValueAsString(dueAt));

        // when
        SettlementReqDTO.Create request = objectMapper.readValue(json, SettlementReqDTO.Create.class);

        // then
        assertEquals(LocalDateTime.parse(dueAt), request.dueAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-02-30T12:00:00", "2026-13-01T12:00:00", "2026-11-01T12:00",
            "2026-11-01T12:00:00.123", "2026-11-01T12:00:00Z",
            "0999-12-31T23:59:59", "0000-01-01T00:00:00", "-0001-01-01T00:00:00",
            "+10000-01-01T00:00:00", "+999999999-12-31T23:59:59"
    })
    @DisplayName("납부 기한은 DB 저장 범위 안의 유효한 초 단위 날짜만 허용한다")
    void create_invalidDueAtFormat_rejectsDate(String dueAt) {
        // given
        String json = withField("dueAt", objectMapper.writeValueAsString(dueAt));

        // when
        JacksonException exception = assertThrows(JacksonException.class,
                () -> objectMapper.readValue(json, SettlementReqDTO.Create.class));

        // then
        assertTrue(exception.getMessage().contains("dueAt"));
    }

    private String withField(String field, String jsonValue) {
        ObjectNode body = (ObjectNode) objectMapper.readTree(EQUAL_JSON);
        body.set(field, objectMapper.readTree(jsonValue));
        return objectMapper.writeValueAsString(body);
    }
}
