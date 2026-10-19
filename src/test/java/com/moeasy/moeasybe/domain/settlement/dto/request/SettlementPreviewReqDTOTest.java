package com.moeasy.moeasybe.domain.settlement.dto.request;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class SettlementPreviewReqDTOTest {

    private static final String EQUAL_JSON = """
            {"splitMode":"EQUAL","totalAmount":500,"groupMemberIds":[13,11,12]}
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
    @DisplayName("균등 미리보기는 제목·일정·납부 기한 없이 요청할 수 있다")
    void preview_equalRequest_deserializesAndValidates() {
        // given
        String json = EQUAL_JSON;

        // when
        SettlementReqDTO.Preview request = objectMapper.readValue(json, SettlementReqDTO.Preview.class);
        var violations = validator.validate(request);

        // then
        assertAll(
                () -> assertTrue(violations.isEmpty()),
                () -> assertEquals(SettlementSplitMode.EQUAL, request.splitMode()),
                () -> assertEquals(500L, request.totalAmount()),
                () -> assertEquals(List.of(13L, 11L, 12L), request.groupMemberIds()),
                () -> assertNull(request.memberAmounts())
        );
    }

    @Test
    @DisplayName("개별 금액 미리보기는 생성과 같은 회원별 금액 DTO와 Long 범위를 사용한다")
    void preview_customRequest_preservesMaximumLongAmount() {
        // given
        String json = """
                {
                  "splitMode":"CUSTOM",
                  "totalAmount":9223372036854775807,
                  "memberAmounts":[{"groupMemberId":11,"amount":9223372036854775807}]
                }
                """;

        // when
        SettlementReqDTO.Preview request = objectMapper.readValue(json, SettlementReqDTO.Preview.class);
        var violations = validator.validate(request);

        // then
        assertAll(
                () -> assertTrue(violations.isEmpty()),
                () -> assertEquals(SettlementSplitMode.CUSTOM, request.splitMode()),
                () -> assertEquals(Long.MAX_VALUE, request.totalAmount()),
                () -> assertEquals(new SettlementReqDTO.MemberAmount(11L, Long.MAX_VALUE),
                        request.memberAmounts().getFirst()),
                () -> assertNull(request.groupMemberIds())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    @DisplayName("미리보기의 필수값·양수·빈 배열·중첩 항목은 DTO에서 검증한다")
    void preview_invalidField_reportsConstraintViolation(String field, String value, String expectedPath) {
        // given
        SettlementReqDTO.Preview request = objectMapper.readValue(withField(field, value),
                SettlementReqDTO.Preview.class);

        // when
        var violations = validator.validate(request);

        // then
        assertTrue(violations.stream().anyMatch(
                violation -> violation.getPropertyPath().toString().startsWith(expectedPath)));
    }

    private static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("splitMode", "null", "splitMode"),
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
                Arguments.of("memberAmounts", "[{\"groupMemberId\":11,\"amount\":0}]", "memberAmounts[0].amount")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidJsonFields")
    @DisplayName("미리보기도 숫자 문자열·소수·지수·범위 초과·잘못된 분할 방식을 거부한다")
    void preview_invalidJsonValue_rejectsWithoutCoercion(String field, String value) {
        // given
        String json = withField(field, value);

        // when
        JacksonException exception = assertThrows(JacksonException.class,
                () -> objectMapper.readValue(json, SettlementReqDTO.Preview.class));

        // then
        assertTrue(exception.getMessage().contains(field));
    }

    private static Stream<Arguments> invalidJsonFields() {
        return Stream.of(
                Arguments.of("totalAmount", "\"500\""),
                Arguments.of("totalAmount", "1.5"),
                Arguments.of("totalAmount", "1e3"),
                Arguments.of("totalAmount", "true"),
                Arguments.of("totalAmount", "9223372036854775808"),
                Arguments.of("groupMemberIds", "[\"11\"]"),
                Arguments.of("groupMemberIds", "[11.5]"),
                Arguments.of("memberAmounts", "[{\"groupMemberId\":\"11\",\"amount\":100}]"),
                Arguments.of("memberAmounts", "[{\"groupMemberId\":11,\"amount\":\"100\"}]"),
                Arguments.of("memberAmounts", "[{\"groupMemberId\":11,\"amount\":1e2}]"),
                Arguments.of("splitMode", "\"UNKNOWN\""),
                Arguments.of("splitMode", "0")
        );
    }

    private String withField(String field, String value) {
        ObjectNode body = (ObjectNode) objectMapper.readTree(EQUAL_JSON);
        body.set(field, objectMapper.readTree(value));
        return objectMapper.writeValueAsString(body);
    }
}
