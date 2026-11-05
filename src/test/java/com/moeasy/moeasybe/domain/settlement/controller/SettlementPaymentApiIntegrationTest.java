package com.moeasy.moeasybe.domain.settlement.controller;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moeasy.moeasybe.domain.auth.config.AuthCookieNames;
import com.moeasy.moeasybe.domain.group.entity.Category;
import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.settlement.entity.Settlement;
import com.moeasy.moeasybe.domain.settlement.entity.SettlementItem;
import com.moeasy.moeasybe.domain.settlement.entity.SettlementPayment;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettlementPaymentApiIntegrationTest {

    private static final String CANCEL_BODY = """
            {"expectedVersion":2,"reason":"  오등록 정정  "}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ObjectMapper objectMapper;

    private Group group;
    private GroupMember owner;
    private GroupMember target;
    private Settlement settlement;
    private SettlementItem item;

    @BeforeEach
    void setUp() {
        Category category = entityManager.createQuery("select c from Category c where c.code = 'RUNNING'", Category.class)
                .getSingleResult();
        group = Group.builder().category(category).name("납부 HTTP " + UUID.randomUUID())
                .description("설명").regionCode("11680").placeName("장소").address("주소")
                .maxMembers(20).joinPolicy(GroupJoinPolicy.APPROVAL).build();
        entityManager.persist(group);
        owner = persistMembership(GroupMemberRole.OWNER);
        target = persistMembership(GroupMemberRole.MEMBER);
        settlement = Settlement.builder().group(group).createdByGroupMember(owner).title("회비")
                .totalAmount(10_000L).splitMode(SettlementSplitMode.EQUAL).build();
        entityManager.persist(settlement);
        item = SettlementItem.builder().settlement(settlement).groupMember(target).amount(10_000L).build();
        entityManager.persist(item);
        entityManager.flush();
        entityManager.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "MANAGER"})
    @DisplayName("실제 JWT·CSRF로 운영진이 납부를 등록하면 201과 누적 청구 현황을 반환한다")
    void registerPayment_operator_returnsCreated(String role) throws Exception {
        // given
        jdbcTemplate.update("UPDATE group_member SET role = ? WHERE id = ?", role, owner.getId());
        var request = authenticatedRequest(owner).content("""
                {"amount":4000,"expectedVersion":0,"occurredAt":"2000-01-01T09:00:00"}
                """);

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isCreated()).andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SETTLEMENT201_2"))
                .andExpect(jsonPath("$.message").value("납부 기록 등록에 성공했습니다."))
                .andExpect(jsonPath("$.result.paymentId").isNumber())
                .andExpect(jsonPath("$.result.settlementItemId").value(item.getId()))
                .andExpect(jsonPath("$.result.status").value("PENDING"))
                .andExpect(jsonPath("$.result.paidAmount").value(4000))
                .andExpect(jsonPath("$.result.remainingAmount").value(6000))
                .andExpect(jsonPath("$.result.version").value(1));
        long paymentId = objectMapper.readTree(result.andReturn().getResponse().getContentAsString())
                .path("result").path("paymentId").asLong();
        entityManager.flush();
        entityManager.clear();
        SettlementPayment payment = entityManager.find(SettlementPayment.class, paymentId);
        assertAll(
                () -> assertEquals(owner.getId(), payment.getRecordedByGroupMember().getId()),
                () -> assertEquals(target.getId(), payment.getSettlementItem().getGroupMember().getId()),
                () -> assertEquals(4_000L, payment.getAmount()),
                () -> assertEquals(LocalDateTime.of(2000, 1, 1, 9, 0), payment.getOccurredAt()),
                () -> assertEquals("MANUAL", payment.getSource().name()),
                () -> assertEquals("COMPLETED", payment.getStatus().name())
        );
        assertStored("PENDING", 1, 1);
    }

    @Test
    @DisplayName("이전 등록 응답의 버전으로 잔액을 납부하면 누적 완납 상태를 반환한다")
    void registerPayment_nextVersion_returnsPaid() throws Exception {
        // given
        mockMvc.perform(authenticatedRequest(owner).content(validBody())).andExpect(status().isCreated());
        var request = authenticatedRequest(owner).content("""
                {"amount":6000,"expectedVersion":1}
                """);

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isCreated()).andExpect(jsonPath("$.result.status").value("PAID"))
                .andExpect(jsonPath("$.result.paidAmount").value(10000))
                .andExpect(jsonPath("$.result.remainingAmount").value(0))
                .andExpect(jsonPath("$.result.version").value(2));
        assertStored("PAID", 2, 2);
    }

    @Test
    @DisplayName("납부·완납·취소 후 일반 회원의 목록·상세 조회는 최신 현황과 다음 요청에 쓸 버전을 반환한다")
    void getSettlements_paymentChanges_returnsCurrentSummaryAndVersion() throws Exception {
        // given
        var firstRequest = authenticatedRequest(owner).content(validBody());

        // when
        var firstResponse = mockMvc.perform(firstRequest).andExpect(status().isCreated()).andReturn().getResponse();

        // then
        long paymentId = objectMapper.readTree(firstResponse.getContentAsString())
                .path("result").path("paymentId").asLong();
        long version = assertPaymentSummaryThroughHttp(4000, 6000, "PENDING", 1);

        // when: 상세 조회에서 받은 버전으로 나머지 금액을 등록한다.
        var fullPayment = mockMvc.perform(authenticatedRequest(owner).content(
                "{\"amount\":6000,\"expectedVersion\":%d}".formatted(version)));

        // then
        fullPayment.andExpect(status().isCreated());
        version = assertPaymentSummaryThroughHttp(10000, 0, "PAID", 2);

        // when: 최초 4,000원 기록만 취소한다.
        var cancellation = mockMvc.perform(authenticatedRequest(owner, cancelPath(paymentId)).content(
                "{\"expectedVersion\":%d,\"reason\":\"오등록 정정\"}".formatted(version)));

        // then
        cancellation.andExpect(status().isOk());
        assertPaymentSummaryThroughHttp(6000, 4000, "PENDING", 3);
        assertStored("PENDING", 3, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "INVALID", "REFRESH"})
    @DisplayName("납부 등록은 유효한 Access Token이 없으면 401을 반환한다")
    void registerPayment_invalidJwt_unauthorized(String condition) throws Exception {
        // given
        CsrfCredentials csrf = requestCsrf();
        var request = post(path()).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(validBody());
        if (!condition.equals("MISSING")) {
            String token = condition.equals("INVALID") ? "invalid-token"
                    : jwtTokenProvider.issueRefreshToken(owner.getMember().getId()).value();
            request.cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, token));
        }

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401_1"));
        assertStored("PENDING", 0, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING_BOTH", "MISSING_COOKIE", "MISSING_HEADER", "MISMATCH"})
    @DisplayName("JWT가 유효해도 CSRF 쿠키·헤더가 없거나 다르면 납부를 저장하지 않는다")
    void registerPayment_invalidCsrf_forbidden(String condition) throws Exception {
        // given
        CsrfCredentials csrf = requestCsrf();
        var request = post(path()).cookie(accessCookie(owner)).contentType(MediaType.APPLICATION_JSON).content(validBody());
        if (condition.equals("MISSING_HEADER") || condition.equals("MISMATCH")) {
            request.cookie(csrf.cookie());
        }
        if (condition.equals("MISSING_COOKIE") || condition.equals("MISMATCH")) {
            request.header("X-XSRF-TOKEN", condition.equals("MISMATCH") ? "different-token" : csrf.token());
        }

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH403_1"));
        assertStored("PENDING", 0, 0);
    }

    @Test
    @DisplayName("일반 회원은 자신의 청구라도 수동 납부를 등록할 수 없다")
    void registerPayment_regularMember_forbidden() throws Exception {
        // given
        var request = authenticatedRequest(target).content(validBody());

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH403_1"));
        assertStored("PENDING", 0, 0);
    }

    @ParameterizedTest
    @CsvSource({"GROUP,0,VALID400_1", "SETTLEMENT,-1,VALID400_1", "ITEM,0,VALID400_1",
            "GROUP,abc,COMMON400_1", "SETTLEMENT,abc,COMMON400_1", "ITEM,abc,COMMON400_1"})
    @DisplayName("세 경로 ID의 양수·숫자 변환 검증은 저장 전에 공통 오류를 반환한다")
    void registerPayment_invalidPath_badRequest(String field, String value, String code) throws Exception {
        // given
        String requestPath = path(field.equals("GROUP") ? value : group.getId().toString(),
                field.equals("SETTLEMENT") ? value : settlement.getId().toString(),
                field.equals("ITEM") ? value : item.getId().toString());
        var request = authenticatedRequest(owner, requestPath).content(validBody());

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
        assertStored("PENDING", 0, 0);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{}|VALID400_1",
            "{\"amount\":0,\"expectedVersion\":0}|VALID400_1",
            "{\"amount\":1000,\"expectedVersion\":-1}|VALID400_1",
            "{\"amount\":\"1000\",\"expectedVersion\":0}|COMMON400_1",
            "{\"amount\":1.5,\"expectedVersion\":0}|COMMON400_1",
            "{\"amount\":1000,\"expectedVersion\":\"0\"}|COMMON400_1",
            "{\"amount\":9223372036854775808,\"expectedVersion\":0}|COMMON400_1",
            "{\"amount\":1000,\"expectedVersion\":0,\"occurredAt\":\"0999-01-01T00:00:00\"}|COMMON400_1",
            "{\"amount\":1000,\"expectedVersion\":0,\"occurredAt\":\"2026-10-30T09:00:00+09:00\"}|COMMON400_1"
    })
    @DisplayName("금액·버전 필드 검증과 엄격한 JSON·날짜 변환이 HTTP 요청에 적용된다")
    void registerPayment_invalidBody_badRequest(String body, String code) throws Exception {
        // given
        var request = authenticatedRequest(owner).content(body);

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
        assertStored("PENDING", 0, 0);
    }

    @ParameterizedTest
    @CsvSource({"GROUP,GROUP404_1", "SETTLEMENT,SETTLEMENT404_1", "ITEM,SETTLEMENT404_2"})
    @DisplayName("경로의 모임·정산·청구가 없으면 대응하는 404 코드를 반환한다")
    void registerPayment_missingResource_notFound(String missing, String code) throws Exception {
        // given
        String requestPath = path(missing.equals("GROUP") ? Long.toString(Long.MAX_VALUE) : group.getId().toString(),
                missing.equals("SETTLEMENT") ? Long.toString(Long.MAX_VALUE) : settlement.getId().toString(),
                missing.equals("ITEM") ? Long.toString(Long.MAX_VALUE) : item.getId().toString());
        var request = authenticatedRequest(owner, requestPath).content(validBody());

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(code));
        assertStored("PENDING", 0, 0);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"amount\":10001,\"expectedVersion\":0}|SETTLEMENT400_6",
            "{\"amount\":1000,\"expectedVersion\":0,\"occurredAt\":\"9999-12-31T23:59:59\"}|SETTLEMENT400_7",
            "{\"amount\":1000,\"expectedVersion\":1}|SETTLEMENT409_1"
    })
    @DisplayName("잔액·입금 시각·버전 업무 오류를 400·409와 도메인 코드로 반환한다")
    void registerPayment_invalidBusinessInput_returnsDomainError(String body, String code) throws Exception {
        // given
        var request = authenticatedRequest(owner).content(body);

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().is(code.startsWith("SETTLEMENT409") ? 409 : 400))
                .andExpect(jsonPath("$.code").value(code));
        assertStored("PENDING", 0, 0);
    }

    @Test
    @DisplayName("마감된 정산의 청구는 409 상태 제한 오류를 반환한다")
    void registerPayment_closedSettlement_conflict() throws Exception {
        // given
        jdbcTemplate.update("UPDATE settlement SET status = 'CLOSED' WHERE id = ?", settlement.getId());
        var request = authenticatedRequest(owner).content(validBody());

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SETTLEMENT409_2"));
        assertStored("PENDING", 0, 0);
    }

    @Test
    @DisplayName("Swagger 납부 등록은 세 경로 ID·CSRF 헤더·실제 요청 및 201 응답 스키마를 제공한다")
    void apiDocs_registerPayment_matchesHttpContract() throws Exception {
        // given
        String operationPath = "/api/v1/groups/{groupId}/settlements/{settlementId}/items/{settlementItemId}/payments";

        // when
        var result = mockMvc.perform(get("/v3/api-docs"));

        // then
        result.andExpect(status().isOk());
        JsonNode document = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        JsonNode operation = document.path("paths").path(operationPath).path("post");
        JsonNode requestContent = operation.path("requestBody").path("content").path("application/json");
        JsonNode requestSchema = resolveSchema(document, requestContent.path("schema"));
        JsonNode content = operation.path("responses").path("201").path("content").path("application/json");
        JsonNode envelope = resolveSchema(document, content.path("schema"));
        JsonNode responseSchema = resolveSchema(document, envelope.path("properties").path("result"));
        Map<String, JsonNode> parameters = new HashMap<>();
        operation.path("parameters").forEach(parameter -> parameters.put(parameter.path("name").asText(), parameter));
        assertEquals(Set.of("groupId", "settlementId", "settlementItemId", "X-XSRF-TOKEN"), parameters.keySet());
        for (String name : parameters.keySet()) {
            assertEquals(name.equals("X-XSRF-TOKEN") ? "header" : "path", parameters.get(name).path("in").asText());
            assertTrue(parameters.get(name).path("required").asBoolean());
        }
        assertAll(
                () -> assertTrue(operation.path("requestBody").path("required").asBoolean()),
                () -> assertTrue(requestSchema.path("properties").has("amount")),
                () -> assertTrue(requestSchema.path("properties").has("expectedVersion")),
                () -> assertTrue(requestSchema.path("properties").has("occurredAt")),
                () -> assertEquals(4000, requestContent.path("examples").path("현재 시각으로 첫 부분 납부")
                        .path("value").path("amount").asInt()),
                () -> assertEquals(0, requestContent.path("examples").path("현재 시각으로 첫 부분 납부")
                        .path("value").path("expectedVersion").asInt()),
                () -> assertTrue(envelope.path("properties").has("isSuccess")),
                () -> assertTrue(responseSchema.path("properties").has("paymentId")),
                () -> assertTrue(responseSchema.path("properties").has("settlementItemId")),
                () -> assertTrue(responseSchema.path("properties").has("status")),
                () -> assertTrue(responseSchema.path("properties").has("paidAmount")),
                () -> assertTrue(responseSchema.path("properties").has("remainingAmount")),
                () -> assertTrue(responseSchema.path("properties").has("version")),
                () -> assertEquals("SETTLEMENT201_2", content.path("examples").path("첫 부분 납부")
                        .path("value").path("code").asText()),
                () -> assertFalse(operation.path("responses").has("200"))
        );
        for (String code : Set.of("400", "401", "403", "404", "409")) {
            assertTrue(operation.path("responses").has(code));
        }
    }

    @Test
    @DisplayName("실제 JWT·CSRF로 납부를 취소하면 200과 취소 후 청구 현황·처리자를 기록한다")
    void cancelPayment_operator_returnsUpdatedItem() throws Exception {
        // given
        Long paymentId = preparePayment();
        var request = authenticatedRequest(owner, cancelPath(paymentId)).content(CANCEL_BODY);

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SETTLEMENT200_4"))
                .andExpect(jsonPath("$.message").value("납부 기록 취소에 성공했습니다."))
                .andExpect(jsonPath("$.result.paymentId").value(paymentId))
                .andExpect(jsonPath("$.result.settlementItemId").value(item.getId()))
                .andExpect(jsonPath("$.result.status").value("PENDING"))
                .andExpect(jsonPath("$.result.paidAmount").value(6000))
                .andExpect(jsonPath("$.result.remainingAmount").value(4000))
                .andExpect(jsonPath("$.result.version").value(3));
        assertStored("PENDING", 3, 2);
        SettlementPayment payment = entityManager.find(SettlementPayment.class, paymentId);
        assertAll(
                () -> assertEquals("CANCELLED", payment.getStatus().name()),
                () -> assertEquals("오등록 정정", payment.getCancelReason()),
                () -> assertEquals(owner.getId(), payment.getCancelledByGroupMember().getId()),
                () -> assertNotNull(payment.getCancelledAt())
        );
    }

    @ParameterizedTest
    @CsvSource({"JWT,401,AUTH401_1", "CSRF,403,AUTH403_1"})
    @DisplayName("취소 엔드포인트도 JWT와 CSRF가 모두 있어야 호출할 수 있다")
    void cancelPayment_missingSecurity_rejected(String missing, int httpStatus, String code) throws Exception {
        // given
        Long paymentId = preparePayment();
        var request = post(cancelPath(paymentId)).contentType(MediaType.APPLICATION_JSON).content(CANCEL_BODY);
        if (missing.equals("JWT")) {
            CsrfCredentials csrf = requestCsrf();
            request.cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token());
        } else {
            request.cookie(accessCookie(owner));
        }

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().is(httpStatus)).andExpect(jsonPath("$.code").value(code));
        assertStored("PAID", 2, 2);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"expectedVersion\":2,\"reason\":\"  \"}|VALID400_1",
            "{\"expectedVersion\":\"2\",\"reason\":\"오등록\"}|COMMON400_1"
    })
    @DisplayName("취소 요청에 DTO 검증과 엄격한 정수 변환을 적용한다")
    void cancelPayment_invalidBody_badRequest(String body, String code) throws Exception {
        // given
        Long paymentId = preparePayment();
        var request = authenticatedRequest(owner, cancelPath(paymentId)).content(body);

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
        assertStored("PAID", 2, 2);
    }

    @Test
    @DisplayName("취소 경로의 납부 기록 ID도 양수로 검증한다")
    void cancelPayment_nonPositivePaymentId_badRequest() throws Exception {
        // given
        var request = authenticatedRequest(owner, cancelPath(0L)).content(CANCEL_BODY);

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALID400_1"));
    }

    @Test
    @DisplayName("취소할 납부가 없으면 404와 납부 기록 없음 코드를 반환한다")
    void cancelPayment_missingPayment_notFound() throws Exception {
        // given
        var request = authenticatedRequest(owner, cancelPath(Long.MAX_VALUE)).content(CANCEL_BODY);

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SETTLEMENT404_3"));
    }

    @Test
    @DisplayName("취소 서비스의 버전 충돌을 HTTP 409 응답으로 변환한다")
    void cancelPayment_staleVersion_conflict() throws Exception {
        // given
        Long paymentId = preparePayment();
        var request = authenticatedRequest(owner, cancelPath(paymentId)).content("""
                {"expectedVersion":1,"reason":"오등록"}
                """);

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SETTLEMENT409_1"));
    }

    @Test
    @DisplayName("Swagger 취소는 네 경로 ID·CSRF 헤더와 실제 요청·200 응답 스키마를 제공한다")
    void apiDocs_cancelPayment_matchesHttpContract() throws Exception {
        // given
        String operationPath = "/api/v1/groups/{groupId}/settlements/{settlementId}"
                + "/items/{settlementItemId}/payments/{paymentId}/cancel";

        // when
        var result = mockMvc.perform(get("/v3/api-docs"));

        // then
        result.andExpect(status().isOk());
        JsonNode document = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        JsonNode operation = document.path("paths").path(operationPath).path("post");
        JsonNode requestContent = operation.path("requestBody").path("content").path("application/json");
        JsonNode requestSchema = resolveSchema(document, requestContent.path("schema"));
        JsonNode content = operation.path("responses").path("200").path("content").path("application/json");
        JsonNode envelope = resolveSchema(document, content.path("schema"));
        JsonNode responseSchema = resolveSchema(document, envelope.path("properties").path("result"));
        Map<String, JsonNode> parameters = new HashMap<>();
        operation.path("parameters").forEach(parameter -> parameters.put(parameter.path("name").asText(), parameter));
        assertEquals(Set.of("groupId", "settlementId", "settlementItemId", "paymentId", "X-XSRF-TOKEN"),
                parameters.keySet());
        for (String name : parameters.keySet()) {
            assertEquals(name.equals("X-XSRF-TOKEN") ? "header" : "path", parameters.get(name).path("in").asText());
            assertTrue(parameters.get(name).path("required").asBoolean());
        }
        assertAll(
                () -> assertTrue(operation.path("requestBody").path("required").asBoolean()),
                () -> assertTrue(requestSchema.path("properties").has("expectedVersion")),
                () -> assertTrue(requestSchema.path("properties").has("reason")),
                () -> assertEquals(2, requestContent.path("examples").path("기록 취소")
                        .path("value").path("expectedVersion").asInt()),
                () -> assertTrue(envelope.path("properties").has("isSuccess")),
                () -> assertEquals("SETTLEMENT200_4", content.path("examples").path("납부 취소")
                        .path("value").path("code").asText()),
                () -> assertFalse(operation.path("responses").has("201"))
        );
        for (String name : Set.of("paymentId", "settlementItemId", "status", "paidAmount", "remainingAmount", "version")) {
            assertTrue(responseSchema.path("properties").has(name));
        }
        for (String code : Set.of("400", "401", "403", "404", "409")) {
            assertTrue(operation.path("responses").has(code));
        }
    }

    @Test
    @DisplayName("일반 회원은 CSRF 없이 JWT 쿠키로 취소 이력과 페이지 정보를 조회한다")
    void getPaymentHistory_memberWithoutCsrf_returnsCancelledHistoryPage() throws Exception {
        // given
        Long paymentId = preparePayment();
        SettlementPayment payment = entityManager.find(SettlementPayment.class, paymentId);
        payment.cancel(owner, LocalDateTime.of(2000, 1, 1, 10, 0), "오등록 정정");
        payment.getSettlementItem().cancelPayment(2L, 10000L, 4000L);
        entityManager.createQuery("update SettlementPayment p set p.createdAt = :at where p.settlementItem.id = :itemId")
                .setParameter("at", LocalDateTime.of(2000, 1, 1, 9, 5)).setParameter("itemId", item.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        var request = get(path()).cookie(accessCookie(target)).param("page", "1").param("size", "1");

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SETTLEMENT200_5"))
                .andExpect(jsonPath("$.message").value("납부 이력 조회에 성공했습니다."))
                .andExpect(jsonPath("$.result.page").value(1))
                .andExpect(jsonPath("$.result.size").value(1))
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].paymentId").value(paymentId))
                .andExpect(jsonPath("$.result.items[0].settlementItemId").value(item.getId()))
                .andExpect(jsonPath("$.result.items[0].paymentType").value("PAYMENT"))
                .andExpect(jsonPath("$.result.items[0].source").value("MANUAL"))
                .andExpect(jsonPath("$.result.items[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.result.items[0].amount").value(4000))
                .andExpect(jsonPath("$.result.items[0].occurredAt").value("2000-01-01T09:00:00"))
                .andExpect(jsonPath("$.result.items[0].createdAt").value("2000-01-01T09:05:00"))
                .andExpect(jsonPath("$.result.items[0].recordedBy.groupMemberId").value(owner.getId()))
                .andExpect(jsonPath("$.result.items[0].cancelledBy.groupMemberId").value(owner.getId()))
                .andExpect(jsonPath("$.result.items[0].cancelledAt").value("2000-01-01T10:00:00"))
                .andExpect(jsonPath("$.result.items[0].cancelReason").value("오등록 정정"));
        assertStored("PENDING", 3, 2);
    }

    @Test
    @DisplayName("납부 기록이 없으면 생략한 페이지 기본값과 빈 이력을 반환한다")
    void getPaymentHistory_noPayments_returnsEmptyDefaultPage() throws Exception {
        // given
        var request = get(path()).cookie(accessCookie(target));

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SETTLEMENT200_5"))
                .andExpect(jsonPath("$.result.items").isEmpty())
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(0))
                .andExpect(jsonPath("$.result.totalPages").value(0))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    @DisplayName("납부 이력도 JWT 쿠키가 없으면 401을 반환한다")
    void getPaymentHistory_missingJwt_unauthorized() throws Exception {
        // given
        var request = get(path());

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401_1"));
    }

    @Test
    @DisplayName("탈퇴 회원의 이력 조회는 403으로 거부한다")
    void getPaymentHistory_inactiveRequester_forbidden() throws Exception {
        // given
        jdbcTemplate.update("UPDATE group_member SET status = 'LEFT' WHERE id = ?", target.getId());
        var request = get(path()).cookie(accessCookie(target));

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH403_1"));
    }

    @ParameterizedTest
    @CsvSource({"item,0,VALID400_1", "size,101,VALID400_1", "page,abc,COMMON400_1"})
    @DisplayName("이력 조회 경로·쿼리에 양수·페이지 범위·타입 검증을 적용한다")
    void getPaymentHistory_invalidParameter_badRequest(String field, String value, String code) throws Exception {
        // given
        String requestPath = field.equals("item")
                ? path(group.getId().toString(), settlement.getId().toString(), value) : path();
        var request = get(requestPath).cookie(accessCookie(target));
        if (!field.equals("item")) {
            request.param(field, value);
        }

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
    }

    @Test
    @DisplayName("조회할 청구가 없으면 404와 청구 없음 코드를 반환한다")
    void getPaymentHistory_missingItem_notFound() throws Exception {
        // given
        var request = get(path(group.getId().toString(), settlement.getId().toString(), Long.toString(Long.MAX_VALUE)))
                .cookie(accessCookie(target));

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SETTLEMENT404_2"));
    }

    @Test
    @DisplayName("Swagger 이력 조회는 경로·페이지 조건과 실제 페이지·감사 스키마를 제공하고 본문·CSRF는 없다")
    void apiDocs_getPaymentHistory_matchesHttpContract() throws Exception {
        // given
        String operationPath = "/api/v1/groups/{groupId}/settlements/{settlementId}/items/{settlementItemId}/payments";

        // when
        var result = mockMvc.perform(get("/v3/api-docs"));

        // then
        result.andExpect(status().isOk());
        JsonNode document = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        JsonNode operation = document.path("paths").path(operationPath).path("get");
        JsonNode content = operation.path("responses").path("200").path("content").path("application/json");
        JsonNode envelope = resolveSchema(document, content.path("schema"));
        JsonNode page = resolveSchema(document, envelope.path("properties").path("result"));
        JsonNode history = resolveSchema(document, page.path("properties").path("items").path("items"));
        Map<String, JsonNode> parameters = new HashMap<>();
        operation.path("parameters").forEach(parameter -> parameters.put(parameter.path("name").asText(), parameter));
        assertEquals(Set.of("groupId", "settlementId", "settlementItemId", "page", "size"), parameters.keySet());
        for (String name : Set.of("groupId", "settlementId", "settlementItemId")) {
            assertEquals("path", parameters.get(name).path("in").asText());
            assertTrue(parameters.get(name).path("required").asBoolean());
        }
        assertAll(
                () -> assertFalse(operation.has("requestBody")),
                () -> assertEquals("query", parameters.get("page").path("in").asText()),
                () -> assertEquals("query", parameters.get("size").path("in").asText()),
                () -> assertEquals(0, parameters.get("page").path("schema").path("default").asInt()),
                () -> assertEquals(20, parameters.get("size").path("schema").path("default").asInt()),
                () -> assertEquals(100, parameters.get("size").path("schema").path("maximum").asInt()),
                () -> assertTrue(envelope.path("properties").has("isSuccess")),
                () -> assertEquals("SETTLEMENT200_5", content.path("examples").path("취소 기록 포함")
                        .path("value").path("code").asText()),
                () -> assertFalse(operation.path("responses").has("201"))
        );
        for (String name : Set.of("items", "page", "size", "totalElements", "totalPages", "hasNext")) {
            assertTrue(page.path("properties").has(name));
        }
        for (String name : Set.of("paymentId", "settlementItemId", "paymentType", "source", "status", "amount",
                "occurredAt", "createdAt", "recordedBy", "cancelledAt", "cancelledBy", "cancelReason")) {
            assertTrue(history.path("properties").has(name));
        }
        for (String actor : Set.of("recordedBy", "cancelledBy")) {
            JsonNode schema = resolveSchema(document, history.path("properties").path(actor));
            assertTrue(schema.path("properties").has("groupMemberId"));
            assertTrue(schema.path("properties").has("nickname"));
        }
        for (String code : Set.of("400", "401", "403", "404")) {
            assertTrue(operation.path("responses").has(code));
        }
    }

    private long assertPaymentSummaryThroughHttp(long paidAmount, long remainingAmount,
                                               String itemStatus, long version) throws Exception {
        entityManager.flush();
        entityManager.clear();
        String listPath = "/api/v1/groups/" + group.getId() + "/settlements";
        int paidCount = itemStatus.equals("PAID") ? 1 : 0;
        int pendingCount = itemStatus.equals("PENDING") ? 1 : 0;
        var detail = mockMvc.perform(get(listPath + "/" + settlement.getId()).cookie(accessCookie(target)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SETTLEMENT200_3"))
                .andExpect(jsonPath("$.result.status").value("OPEN"))
                .andExpect(jsonPath("$.result.targetCount").value(1))
                .andExpect(jsonPath("$.result.paidAmount").value(paidAmount))
                .andExpect(jsonPath("$.result.remainingAmount").value(remainingAmount))
                .andExpect(jsonPath("$.result.paidCount").value(paidCount))
                .andExpect(jsonPath("$.result.pendingCount").value(pendingCount))
                .andExpect(jsonPath("$.result.items[0].settlementItemId").value(item.getId()))
                .andExpect(jsonPath("$.result.items[0].status").value(itemStatus))
                .andExpect(jsonPath("$.result.items[0].paidAmount").value(paidAmount))
                .andExpect(jsonPath("$.result.items[0].remainingAmount").value(remainingAmount))
                .andExpect(jsonPath("$.result.items[0].version").value(version))
                .andReturn().getResponse();
        mockMvc.perform(get(listPath).cookie(accessCookie(target)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SETTLEMENT200_2"))
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].settlementId").value(settlement.getId()))
                .andExpect(jsonPath("$.result.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.result.items[0].targetCount").value(1))
                .andExpect(jsonPath("$.result.items[0].paidAmount").value(paidAmount))
                .andExpect(jsonPath("$.result.items[0].remainingAmount").value(remainingAmount))
                .andExpect(jsonPath("$.result.items[0].paidCount").value(paidCount))
                .andExpect(jsonPath("$.result.items[0].pendingCount").value(pendingCount));
        return objectMapper.readTree(detail.getContentAsString()).path("result").path("items").get(0)
                .path("version").asLong();
    }

    private Long preparePayment() {
        SettlementItem stored = entityManager.find(SettlementItem.class, item.getId());
        LocalDateTime occurredAt = LocalDateTime.of(2000, 1, 1, 9, 0);
        long paidAmount = stored.applyPayment(0L, 0L, 4000L);
        SettlementPayment first = SettlementPayment.builder().settlementItem(stored).amount(4000L)
                .occurredAt(occurredAt).recordedByGroupMember(owner).build();
        entityManager.persist(first);
        stored.applyPayment(1L, paidAmount, 6000L);
        entityManager.persist(SettlementPayment.builder().settlementItem(stored).amount(6000L)
                .occurredAt(occurredAt).recordedByGroupMember(owner).build());
        entityManager.flush();
        entityManager.clear();
        return first.getId();
    }

    private String cancelPath(Long paymentId) {
        return path() + "/" + paymentId + "/cancel";
    }

    private GroupMember persistMembership(GroupMemberRole role) {
        Member member = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).build();
        entityManager.persist(member);
        GroupMember membership = GroupMember.builder().group(group).member(member).role(role)
                .status(GroupMemberStatus.ACTIVE).joinedAt(LocalDateTime.of(2000, 1, 1, 0, 0)).build();
        entityManager.persist(membership);
        return membership;
    }

    private MockHttpServletRequestBuilder authenticatedRequest(GroupMember actor) throws Exception {
        return authenticatedRequest(actor, path());
    }

    private MockHttpServletRequestBuilder authenticatedRequest(GroupMember actor, String requestPath) throws Exception {
        CsrfCredentials csrf = requestCsrf();
        return post(requestPath).cookie(accessCookie(actor), csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private CsrfCredentials requestCsrf() throws Exception {
        var result = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).path("result").path("token").asText();
        assertNotNull(cookie);
        assertFalse(token.isBlank());
        return new CsrfCredentials(cookie, token);
    }

    private Cookie accessCookie(GroupMember actor) {
        return new Cookie(AuthCookieNames.ACCESS_TOKEN, jwtTokenProvider.issueAccessToken(actor.getMember().getId()).value());
    }

    private String path() {
        return path(group.getId().toString(), settlement.getId().toString(), item.getId().toString());
    }

    private String path(String groupId, String settlementId, String itemId) {
        return "/api/v1/groups/" + groupId + "/settlements/" + settlementId + "/items/" + itemId + "/payments";
    }

    private String validBody() {
        return """
                {"amount":4000,"expectedVersion":0}
                """;
    }

    private void assertStored(String status, long version, int paymentCount) {
        entityManager.flush();
        entityManager.clear();
        SettlementItem stored = entityManager.find(SettlementItem.class, item.getId());
        assertAll(
                () -> assertEquals(status, stored.getStatus().name()),
                () -> assertEquals(version, stored.getVersion()),
                () -> assertEquals(paymentCount, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM settlement_payment WHERE settlement_item_id = ?", Integer.class, item.getId()))
        );
    }

    private JsonNode resolveSchema(JsonNode document, JsonNode schema) {
        return schema.has("$ref") ? document.at(schema.path("$ref").asText().substring(1)) : schema;
    }

    private record CsrfCredentials(Cookie cookie, String token) {
    }
}
