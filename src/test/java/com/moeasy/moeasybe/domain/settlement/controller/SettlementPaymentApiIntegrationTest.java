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
