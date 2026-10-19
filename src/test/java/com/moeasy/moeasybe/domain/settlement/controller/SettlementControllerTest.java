package com.moeasy.moeasybe.domain.settlement.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.dto.response.SettlementResDTO;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.domain.settlement.exception.SettlementException;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import com.moeasy.moeasybe.domain.settlement.service.command.SettlementCommandService;
import com.moeasy.moeasybe.domain.settlement.service.query.SettlementQueryService;
import com.moeasy.moeasybe.global.security.jwt.JwtAuthenticationFilter;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@WebMvcTest(SettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
class SettlementControllerTest {

    private static final Long GROUP_ID = 10L;
    private static final Long MEMBER_ID = 42L;
    private static final String PATH = "/api/v1/groups/10/settlements";
    private static final String VALID_BODY = """
            {"title":" 정산 ","totalAmount":500,"splitMode":"EQUAL","groupMemberIds":[11,12,13]}
            """;
    private static final String PREVIEW_PATH = PATH + "/preview";
    private static final String PREVIEW_BODY = """
            {"totalAmount":500,"splitMode":"EQUAL","groupMemberIds":[11,12,13]}
            """;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SettlementCommandService service;
    @MockitoBean private SettlementQueryService queryService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    // 애플리케이션의 JPA auditing 설정은 MVC 슬라이스에서 실제 영속성 메타모델을 사용하지 않는다.
    @MockitoBean private JpaMetamodelMappingContext jpaMappingContext;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("균등 정산 요청은 경로·인증 회원과 함께 전달하고 생성 식별자를 공통 201 응답으로 반환한다")
    void createSettlement_equal_returnsCreatedEnvelope() throws Exception {
        // given
        when(service.createSettlement(eq(GROUP_ID), any(SettlementReqDTO.Create.class), eq(MEMBER_ID)))
                .thenReturn(SettlementResDTO.Created.builder().settlementId(99L).build());

        // when
        var result = mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY));

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SETTLEMENT201_1"))
                .andExpect(jsonPath("$.message").value("정산 생성에 성공했습니다."))
                .andExpect(jsonPath("$.result.settlementId").value(99));
        ArgumentCaptor<SettlementReqDTO.Create> request = ArgumentCaptor.forClass(SettlementReqDTO.Create.class);
        verify(service).createSettlement(eq(GROUP_ID), request.capture(), eq(MEMBER_ID));
        assertAll(
                () -> assertEquals("정산", request.getValue().title()),
                () -> assertEquals(500L, request.getValue().totalAmount()),
                () -> assertEquals(SettlementSplitMode.EQUAL, request.getValue().splitMode()),
                () -> assertEquals(List.of(11L, 12L, 13L), request.getValue().groupMemberIds())
        );
    }

    @Test
    @DisplayName("개별 금액과 선택적 일정·납부 기한을 역직렬화해 서비스로 전달한다")
    void createSettlement_custom_passesMemberAmountsAndOptionalFields() throws Exception {
        // given
        String body = """
                {"title":"식사비","description":"설명","scheduleId":7,"dueAt":"2026-12-01T12:00:00",
                 "totalAmount":500,"splitMode":"CUSTOM","memberAmounts":[{"groupMemberId":11,"amount":500}]}
                """;
        when(service.createSettlement(eq(GROUP_ID), any(SettlementReqDTO.Create.class), eq(MEMBER_ID)))
                .thenReturn(SettlementResDTO.Created.builder().settlementId(100L).build());

        // when
        var result = mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        result.andExpect(status().isCreated()).andExpect(jsonPath("$.result.settlementId").value(100));
        ArgumentCaptor<SettlementReqDTO.Create> request = ArgumentCaptor.forClass(SettlementReqDTO.Create.class);
        verify(service).createSettlement(eq(GROUP_ID), request.capture(), eq(MEMBER_ID));
        assertAll(
                () -> assertEquals(SettlementSplitMode.CUSTOM, request.getValue().splitMode()),
                () -> assertEquals(List.of(new SettlementReqDTO.MemberAmount(11L, 500L)), request.getValue().memberAmounts()),
                () -> assertEquals(7L, request.getValue().scheduleId()),
                () -> assertEquals("2026-12-01T12:00", request.getValue().dueAt().toString()),
                () -> assertEquals("설명", request.getValue().description())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    @DisplayName("필수값·길이·양수·빈 배열·중첩 항목 검증에 실패하면 서비스 실행 전에 VALID400_1을 반환한다")
    void createSettlement_invalidFields_returnsValidationFailure(String body) throws Exception {
        // given: 요청 본문의 필드 제약을 검증한다.

        // when
        var result = mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("VALID400_1"))
                .andExpect(jsonPath("$.result").isMap());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("invalidJson")
    @DisplayName("숫자·분할 방식·날짜의 잘못된 JSON 형식은 COMMON400_1을 반환한다")
    void createSettlement_invalidJson_returnsBadRequest(String body) throws Exception {
        // given: 변환할 수 없는 요청을 전달한다.

        // when
        var result = mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result").isEmpty());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(SettlementErrorCode.class)
    @DisplayName("서비스의 정산 규칙 오류는 실제 도메인 코드·메시지의 공통 실패 응답으로 변환한다")
    void createSettlement_businessFailure_preservesDomainError(SettlementErrorCode code) throws Exception {
        // given
        when(service.createSettlement(eq(GROUP_ID), any(SettlementReqDTO.Create.class), eq(MEMBER_ID)))
                .thenThrow(new SettlementException(code));

        // when
        var result = mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(code.getCode()))
                .andExpect(jsonPath("$.message").value(code.getMessage()))
                .andExpect(jsonPath("$.result").isEmpty());
    }

    @Test
    @DisplayName("서비스의 모임 없음 오류는 GROUP404_1로 반환한다")
    void createSettlement_groupNotFound_returnsNotFound() throws Exception {
        // given
        when(service.createSettlement(eq(GROUP_ID), any(SettlementReqDTO.Create.class), eq(MEMBER_ID)))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        // when
        var result = mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY));

        // then
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GROUP404_1"));
    }

    @Test
    @DisplayName("미리보기는 요청과 인증 회원을 QueryService에 전달하고 공통 200 응답을 반환한다")
    void previewSplit_equal_returnsPreviewEnvelope() throws Exception {
        // given
        when(queryService.previewSplit(eq(GROUP_ID), any(SettlementReqDTO.Preview.class), eq(MEMBER_ID)))
                .thenReturn(SettlementResDTO.Preview.builder().splitMode(SettlementSplitMode.EQUAL)
                        .totalAmount(500L).targetCount(3).items(List.of(
                                SettlementResDTO.PreviewItem.builder().groupMemberId(11L).nickname("모이지")
                                        .amount(167L).remainderAmount(1L).build(),
                                SettlementResDTO.PreviewItem.builder().groupMemberId(12L).nickname("민지")
                                        .amount(167L).remainderAmount(1L).build(),
                                SettlementResDTO.PreviewItem.builder().groupMemberId(13L).nickname("도윤")
                                        .amount(166L).remainderAmount(0L).build())).build());

        // when
        var result = mockMvc.perform(post(PREVIEW_PATH).contentType(MediaType.APPLICATION_JSON).content(PREVIEW_BODY));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SETTLEMENT200_1"))
                .andExpect(jsonPath("$.message").value("정산 분할 미리보기에 성공했습니다."))
                .andExpect(jsonPath("$.result.splitMode").value("EQUAL"))
                .andExpect(jsonPath("$.result.totalAmount").value(500))
                .andExpect(jsonPath("$.result.targetCount").value(3))
                .andExpect(jsonPath("$.result.items.length()").value(3))
                .andExpect(jsonPath("$.result.items[0].groupMemberId").value(11))
                .andExpect(jsonPath("$.result.items[0].nickname").value("모이지"))
                .andExpect(jsonPath("$.result.items[0].amount").value(167))
                .andExpect(jsonPath("$.result.items[0].remainderAmount").value(1))
                .andExpect(jsonPath("$.result.items[2].remainderAmount").value(0));
        ArgumentCaptor<SettlementReqDTO.Preview> request = ArgumentCaptor.forClass(SettlementReqDTO.Preview.class);
        verify(queryService).previewSplit(eq(GROUP_ID), request.capture(), eq(MEMBER_ID));
        assertAll(
                () -> assertEquals(SettlementSplitMode.EQUAL, request.getValue().splitMode()),
                () -> assertEquals(500L, request.getValue().totalAmount()),
                () -> assertEquals(List.of(11L, 12L, 13L), request.getValue().groupMemberIds())
        );
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("회원별 미리보기 금액은 중첩 DTO로 읽어 QueryService에 전달한다")
    void previewSplit_custom_passesMemberAmounts() throws Exception {
        // given
        String body = """
                {"totalAmount":500,"splitMode":"CUSTOM","memberAmounts":[{"groupMemberId":11,"amount":500}]}
                """;
        when(queryService.previewSplit(eq(GROUP_ID), any(SettlementReqDTO.Preview.class), eq(MEMBER_ID)))
                .thenReturn(SettlementResDTO.Preview.builder().splitMode(SettlementSplitMode.CUSTOM)
                        .totalAmount(500L).targetCount(1).items(List.of(
                                SettlementResDTO.PreviewItem.builder().groupMemberId(11L).nickname("모이지")
                                        .amount(500L).remainderAmount(0L).build())).build());

        // when
        var result = mockMvc.perform(post(PREVIEW_PATH).contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.result.splitMode").value("CUSTOM"))
                .andExpect(jsonPath("$.result.items[0].amount").value(500));
        ArgumentCaptor<SettlementReqDTO.Preview> request = ArgumentCaptor.forClass(SettlementReqDTO.Preview.class);
        verify(queryService).previewSplit(eq(GROUP_ID), request.capture(), eq(MEMBER_ID));
        assertEquals(List.of(new SettlementReqDTO.MemberAmount(11L, 500L)), request.getValue().memberAmounts());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("invalidPreviewFields")
    @DisplayName("미리보기 필드 검증 실패 시 서비스 실행 전 VALID400_1을 반환한다")
    void previewSplit_invalidFields_returnsValidationFailure(String body) throws Exception {
        // given: 필수값·배열·중첩 항목 검증에 실패하는 요청

        // when
        var result = mockMvc.perform(post(PREVIEW_PATH).contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALID400_1"))
                .andExpect(jsonPath("$.result").isMap());
        verifyNoInteractions(queryService, service);
    }

    private static Stream<String> invalidPreviewFields() {
        return Stream.of(
                PREVIEW_BODY.replace("\"totalAmount\":500,", ""),
                PREVIEW_BODY.replace("\"totalAmount\":500", "\"totalAmount\":0"),
                PREVIEW_BODY.replace("\"splitMode\":\"EQUAL\",", ""),
                PREVIEW_BODY.replace("[11,12,13]", "[]"),
                PREVIEW_BODY.replace("[11,12,13]", "[null]"),
                PREVIEW_BODY.replace("[11,12,13]", "[-1]"),
                """
                {"totalAmount":500,"splitMode":"CUSTOM","memberAmounts":[{"groupMemberId":11,"amount":0}]}
                """,
                """
                {"totalAmount":500,"splitMode":"CUSTOM","memberAmounts":[null]}
                """
        );
    }

    @ParameterizedTest
    @MethodSource("invalidPreviewJson")
    @DisplayName("미리보기의 잘못된 JSON과 숫자·분할 방식 형식은 COMMON400_1을 반환한다")
    void previewSplit_invalidJson_returnsBadRequest(String body) throws Exception {
        // given: 파싱 또는 형 변환에 실패하는 요청

        // when
        var result = mockMvc.perform(post(PREVIEW_PATH).contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON400_1"));
        verifyNoInteractions(queryService, service);
    }

    private static Stream<String> invalidPreviewJson() {
        return Stream.concat(Stream.of("\"500\"", "1.5", "5e2", "9223372036854775808")
                        .map(amount -> PREVIEW_BODY.replace("\"totalAmount\":500", "\"totalAmount\":" + amount)),
                Stream.of("null", "{", PREVIEW_BODY.replace("\"EQUAL\"", "0"),
                        PREVIEW_BODY.replace("\"EQUAL\"", "\"INVALID\""),
                        PREVIEW_BODY.replace("[11,12,13]", "[\"11\"]")));
    }

    @ParameterizedTest
    @EnumSource(value = SettlementErrorCode.class,
            names = {"INVALID_TARGET_MEMBERS", "INVALID_SPLIT_MODE_FIELDS", "INVALID_AMOUNT"})
    @DisplayName("미리보기 서비스 오류는 도메인 코드·메시지와 함께 반환한다")
    void previewSplit_businessFailure_preservesDomainError(SettlementErrorCode code) throws Exception {
        // given
        when(queryService.previewSplit(eq(GROUP_ID), any(SettlementReqDTO.Preview.class), eq(MEMBER_ID)))
                .thenThrow(new SettlementException(code));

        // when
        var result = mockMvc.perform(post(PREVIEW_PATH).contentType(MediaType.APPLICATION_JSON).content(PREVIEW_BODY));

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code.getCode()))
                .andExpect(jsonPath("$.message").value(code.getMessage()))
                .andExpect(jsonPath("$.result").isEmpty());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("미리보기 대상 모임이 없으면 GROUP404_1을 반환한다")
    void previewSplit_groupNotFound_returnsNotFound() throws Exception {
        // given
        when(queryService.previewSplit(eq(GROUP_ID), any(SettlementReqDTO.Preview.class), eq(MEMBER_ID)))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        // when
        var result = mockMvc.perform(post(PREVIEW_PATH).contentType(MediaType.APPLICATION_JSON).content(PREVIEW_BODY));

        // then
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GROUP404_1"));
    }

    private static Stream<String> invalidFields() {
        return Stream.of(
                VALID_BODY.replace("\" 정산 \"", "\"   \""),
                VALID_BODY.replace("\" 정산 \"", "\"" + "가".repeat(101) + "\""),
                VALID_BODY.replace("\"totalAmount\":500,", ""),
                VALID_BODY.replace("\"totalAmount\":500", "\"totalAmount\":0"),
                VALID_BODY.replace("\"splitMode\":\"EQUAL\",", ""),
                VALID_BODY.replace("[11,12,13]", "[]"),
                VALID_BODY.replace("[11,12,13]", "[null]"),
                VALID_BODY.replace("[11,12,13]", "[-1]"),
                VALID_BODY.replace("\"title\":", "\"description\":\"" + "가".repeat(501) + "\",\"title\":"),
                """
                {"title":"정산","totalAmount":500,"splitMode":"CUSTOM","memberAmounts":[{"groupMemberId":11,"amount":0}]}
                """,
                """
                {"title":"정산","totalAmount":500,"splitMode":"CUSTOM","memberAmounts":[null]}
                """
        );
    }

    private static Stream<String> invalidJson() {
        return Stream.concat(Stream.of("\"500\"", "1.5", "5e2", "9223372036854775808")
                        .map(amount -> VALID_BODY.replace("\"totalAmount\":500", "\"totalAmount\":" + amount)),
                Stream.of(
                        "null", "{",
                        VALID_BODY.replace("\"EQUAL\"", "0"),
                        VALID_BODY.replace("\"EQUAL\"", "\"INVALID\""),
                        VALID_BODY.replace("\"title\":", "\"dueAt\":\"2026-02-30T12:00:00\",\"title\":"),
                        VALID_BODY.replace("[11,12,13]", "[\"11\"]")
                ));
    }

    @TestConfiguration
    static class PrincipalResolverConfiguration implements WebMvcConfigurer {

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }
}
