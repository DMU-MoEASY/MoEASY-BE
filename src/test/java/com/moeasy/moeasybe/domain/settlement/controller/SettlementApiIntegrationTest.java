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
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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
class SettlementApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ObjectMapper objectMapper;

    private Group group;
    private GroupMember owner;
    private GroupMember target;

    @BeforeEach
    void setUp() {
        Category category = entityManager.createQuery("select c from Category c where c.code = :code", Category.class)
                .setParameter("code", "RUNNING").getSingleResult();
        group = Group.builder().category(category).name("정산 API " + UUID.randomUUID())
                .description("설명").regionCode("11680").placeName("장소").address("주소")
                .maxMembers(20).joinPolicy(GroupJoinPolicy.APPROVAL).build();
        entityManager.persist(group);
        owner = persistGroupMember(GroupMemberRole.OWNER);
        target = persistGroupMember(GroupMemberRole.MEMBER);
    }

    @ParameterizedTest
    @EnumSource(GroupMemberRole.class)
    @DisplayName("활성 회원은 정산 대상이 아니어도 JWT 쿠키만으로 최신순 목록과 페이지 정보를 조회한다")
    void getSettlements_activeMember_returnsPageWithoutCsrf(GroupMemberRole role) throws Exception {
        // given
        GroupMember requester = persistGroupMember(role);
        persistListSettlement("이전 정산", List.of(owner, target));
        Settlement latest = persistListSettlement("최근 정산", List.of(owner));
        Cookie cookie = accessTokenCookie(requester);
        entityManager.flush();
        entityManager.clear();
        var request = get(path()).cookie(cookie).param("size", "1");

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SETTLEMENT200_2"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(1))
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].settlementId").value(latest.getId()))
                .andExpect(jsonPath("$.result.items[0].title").value("최근 정산"))
                .andExpect(jsonPath("$.result.items[0].targetCount").value(1))
                .andExpect(jsonPath("$.result.items[0].totalAmount").value(100))
                .andExpect(jsonPath("$.result.items[0].scheduleId").isEmpty())
                .andExpect(jsonPath("$.result.items[0].dueAt").isEmpty())
                .andExpect(jsonPath("$.result.items[0].createdAt").value("2026-10-20T09:00:00"))
                .andExpect(jsonPath("$.result.items[0].myItem").doesNotExist());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlement WHERE group_id = ?", Integer.class, group.getId()));
    }

    @Test
    @DisplayName("폐쇄된 모임의 일반 회원도 상태 필터로 기존 정산을 조회한다")
    void getSettlements_closedGroup_returnsFilteredHistory() throws Exception {
        // given
        Settlement closed = persistListSettlement("마감 정산", List.of(owner));
        persistListSettlement("진행 정산", List.of(owner));
        Cookie cookie = accessTokenCookie(target);
        entityManager.flush();
        jdbcTemplate.update("UPDATE member_group SET status = 'CLOSED' WHERE id = ?", group.getId());
        jdbcTemplate.update("UPDATE settlement SET status = 'CLOSED' WHERE id = ?", closed.getId());
        entityManager.clear();
        var request = get(path()).cookie(cookie).param("status", "CLOSED");

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].settlementId").value(closed.getId()))
                .andExpect(jsonPath("$.result.items[0].status").value("CLOSED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "INVALID", "REFRESH"})
    @DisplayName("목록 조회도 유효한 Access Token이 없으면 CSRF와 무관하게 401이다")
    void getSettlements_invalidAuthentication_unauthorized(String condition) throws Exception {
        // given
        var request = get(path());
        if (!condition.equals("MISSING")) {
            String token = condition.equals("INVALID") ? "invalid-token"
                    : jwtTokenProvider.issueRefreshToken(owner.getMember().getId()).value();
            request.cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, token));
        }

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401_1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEFT", "KICKED", "DELETED_MEMBERSHIP", "DELETED_MEMBER", "NON_MEMBER"})
    @DisplayName("유효한 JWT가 있어도 탈퇴·강퇴·삭제 요청자와 비회원은 목록 조회가 거부된다")
    void getSettlements_unauthorizedMembership_forbidden(String condition) throws Exception {
        // given
        Long memberId = owner.getMember().getId();
        if (condition.equals("NON_MEMBER")) {
            Member member = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).build();
            entityManager.persist(member);
            memberId = member.getId();
        }
        Cookie cookie = new Cookie(AuthCookieNames.ACCESS_TOKEN, jwtTokenProvider.issueAccessToken(memberId).value());
        entityManager.flush();
        switch (condition) {
            case "LEFT", "KICKED" -> jdbcTemplate.update(
                    "UPDATE group_member SET status = ? WHERE id = ?", condition, owner.getId());
            case "DELETED_MEMBERSHIP" -> jdbcTemplate.update(
                    "UPDATE group_member SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", owner.getId());
            case "DELETED_MEMBER" -> jdbcTemplate.update(
                    "UPDATE member SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", memberId);
            default -> { }
        }
        entityManager.clear();
        var request = get(path()).cookie(cookie);

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH403_1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "DELETED"})
    @DisplayName("목록의 모임이 없거나 삭제되면 404이며 폐쇄 상태와 구분한다")
    void getSettlements_unavailableGroup_notFound(String condition) throws Exception {
        // given
        Cookie cookie = accessTokenCookie(owner);
        entityManager.flush();
        if (condition.equals("DELETED")) {
            jdbcTemplate.update("UPDATE member_group SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", group.getId());
        }
        entityManager.clear();
        Long groupId = condition.equals("MISSING") ? Long.MAX_VALUE : group.getId();
        var request = get("/api/v1/groups/" + groupId + "/settlements").cookie(cookie);

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GROUP404_1"));
    }

    @ParameterizedTest
    @CsvSource({"size,101,VALID400_1", "status,UNKNOWN,COMMON400_1"})
    @DisplayName("실제 필터를 거쳐도 목록의 범위·타입 오류는 계약된 400 코드로 반환된다")
    void getSettlements_invalidQuery_returnsBadRequest(String field, String value, String code) throws Exception {
        // given
        var request = get(path()).cookie(accessTokenCookie(owner)).param(field, value);

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
    }

    @Test
    @DisplayName("Swagger 목록에는 쿼리 필터·페이지 스키마가 있고 요청 본문·CSRF 헤더는 없다")
    void apiDocs_getSettlements_matchesHttpContract() throws Exception {
        // given
        String operationPath = "/api/v1/groups/{groupId}/settlements";

        // when
        var response = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse();

        // then
        JsonNode document = objectMapper.readTree(response.getContentAsString());
        JsonNode operation = document.path("paths").path(operationPath).path("get");
        JsonNode content = operation.path("responses").path("200").path("content").path("application/json");
        JsonNode envelope = resolveSchema(document, content.path("schema"));
        JsonNode page = resolveSchema(document, envelope.path("properties").path("result"));
        JsonNode item = resolveSchema(document, page.path("properties").path("items").path("items"));
        Map<String, JsonNode> parameters = new HashMap<>();
        operation.path("parameters").forEach(parameter -> parameters.put(parameter.path("name").asText(), parameter));
        assertAll(
                () -> assertFalse(operation.has("requestBody")),
                () -> assertEquals(5, parameters.size()),
                () -> assertEquals("path", parameters.get("groupId").path("in").asText()),
                () -> assertTrue(parameters.get("groupId").path("required").asBoolean()),
                () -> assertEquals("query", parameters.get("page").path("in").asText()),
                () -> assertEquals(0, parameters.get("page").path("schema").path("default").asInt()),
                () -> assertEquals(20, parameters.get("size").path("schema").path("default").asInt()),
                () -> assertEquals("query", parameters.get("status").path("in").asText()),
                () -> assertEquals("query", parameters.get("scheduleId").path("in").asText()),
                () -> assertFalse(parameters.containsKey("X-XSRF-TOKEN")),
                () -> assertFalse(parameters.containsKey("memberId")),
                () -> assertTrue(envelope.path("properties").has("isSuccess")),
                () -> assertTrue(page.path("properties").has("totalElements")),
                () -> assertTrue(page.path("properties").has("totalPages")),
                () -> assertTrue(page.path("properties").has("hasNext")),
                () -> assertTrue(item.path("properties").has("targetCount")),
                () -> assertTrue(item.path("properties").has("createdAt")),
                () -> assertFalse(item.path("properties").has("myItem")),
                () -> assertTrue(content.path("examples").has("정산 목록")),
                () -> assertTrue(content.path("examples").has("빈 목록")),
                () -> assertTrue(operation.path("responses").has("400")),
                () -> assertTrue(operation.path("responses").has("401")),
                () -> assertTrue(operation.path("responses").has("403")),
                () -> assertTrue(operation.path("responses").has("404"))
        );
    }

    @Test
    @DisplayName("JWT·발급받은 CSRF 토큰으로 균등 정산을 생성하면 201 응답과 실제 청구가 저장된다")
    void createSettlement_ownerEqual_persistsChargesThroughHttp() throws Exception {
        // given
        GroupMember third = persistGroupMember(GroupMemberRole.MEMBER);
        CsrfCredentials csrf = requestCsrfCredentials();
        String body = """
                {"title":" 정산 ","totalAmount":500,"splitMode":"EQUAL","groupMemberIds":[%d,%d,%d]}
                """.formatted(third.getId(), target.getId(), owner.getId());

        // when
        var result = mockMvc.perform(authenticatedRequest(owner, csrf).content(body));

        // then
        String response = result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SETTLEMENT201_1"))
                .andReturn().getResponse().getContentAsString();
        Long settlementId = objectMapper.readTree(response).path("result").path("settlementId").asLong();
        entityManager.flush();
        assertAll(
                () -> assertEquals(owner.getId(), jdbcTemplate.queryForObject(
                        "SELECT created_by_group_member_id FROM settlement WHERE id = ?", Long.class, settlementId)),
                () -> assertEquals("정산", jdbcTemplate.queryForObject(
                        "SELECT title FROM settlement WHERE id = ?", String.class, settlementId)),
                () -> assertEquals(List.of(167L, 167L, 166L), jdbcTemplate.queryForList("""
                        SELECT amount FROM settlement_item WHERE settlement_id = ? ORDER BY group_member_id
                        """, Long.class, settlementId)),
                () -> assertEquals("OPEN", jdbcTemplate.queryForObject(
                        "SELECT status FROM settlement WHERE id = ?", String.class, settlementId))
        );
    }

    @Test
    @DisplayName("운영진은 HTTP 요청으로 회원별 지정 금액 정산을 생성할 수 있다")
    void createSettlement_managerCustom_persistsSpecifiedCharges() throws Exception {
        // given
        GroupMember manager = persistGroupMember(GroupMemberRole.MANAGER);
        CsrfCredentials csrf = requestCsrfCredentials();
        String body = """
                {"title":"개별 정산","totalAmount":500,"splitMode":"CUSTOM",
                 "memberAmounts":[{"groupMemberId":%d,"amount":300},{"groupMemberId":%d,"amount":200}]}
                """.formatted(manager.getId(), target.getId());

        // when
        var result = mockMvc.perform(authenticatedRequest(manager, csrf).content(body));

        // then
        String response = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long settlementId = objectMapper.readTree(response).path("result").path("settlementId").asLong();
        assertEquals(300L, jdbcTemplate.queryForObject("""
                SELECT amount FROM settlement_item WHERE settlement_id = ? AND group_member_id = ?
                """, Long.class, settlementId, manager.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "INVALID", "REFRESH"})
    @DisplayName("유효한 CSRF를 보내도 Access Token이 없거나 잘못됐거나 Refresh Token이면 401이다")
    void createSettlement_invalidAuthentication_unauthorized(String condition) throws Exception {
        // given
        CsrfCredentials csrf = requestCsrfCredentials();
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
        assertNoSettlement();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING_BOTH", "MISSING_COOKIE", "MISSING_HEADER", "MISMATCH"})
    @DisplayName("인증되어도 CSRF 쿠키·헤더가 누락되거나 일치하지 않으면 403이다")
    void createSettlement_invalidCsrf_forbidden(String condition) throws Exception {
        // given
        CsrfCredentials csrf = requestCsrfCredentials();
        var request = post(path()).cookie(accessTokenCookie(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(validBody());
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
        assertNoSettlement();
    }

    @Test
    @DisplayName("JWT와 CSRF가 유효해도 일반 모임 회원은 생성 권한이 없어 403이다")
    void createSettlement_regularMember_forbidden() throws Exception {
        // given
        CsrfCredentials csrf = requestCsrfCredentials();

        // when
        var result = mockMvc.perform(authenticatedRequest(target, csrf).content(validBody()));

        // then
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH403_1"));
        assertNoSettlement();
    }

    @ParameterizedTest
    @CsvSource({"DUPLICATE,SETTLEMENT400_2", "FIELDS,SETTLEMENT400_3", "AMOUNT,SETTLEMENT400_4",
            "SCHEDULE,SETTLEMENT400_1", "DUE_AT,SETTLEMENT400_5"})
    @DisplayName("실제 서비스의 업무 규칙 오류를 HTTP 400으로 전달하고 저장하지 않는다")
    void createSettlement_invalidBusinessInput_returnsDomainCode(String condition, String code) throws Exception {
        // given
        CsrfCredentials csrf = requestCsrfCredentials();
        String body = switch (condition) {
            case "DUPLICATE" -> validBody().replace("[%d]".formatted(target.getId()),
                    "[%d,%d]".formatted(target.getId(), target.getId()));
            case "FIELDS" -> validBody().replace("\"title\":", "\"memberAmounts\":[{\"groupMemberId\":1,\"amount\":500}],\"title\":");
            case "AMOUNT" -> """
                    {"title":"정산","totalAmount":500,"splitMode":"CUSTOM","memberAmounts":[{"groupMemberId":%d,"amount":499}]}
                    """.formatted(target.getId());
            case "SCHEDULE" -> validBody().replace("\"title\":", "\"scheduleId\":9223372036854775807,\"title\":");
            case "DUE_AT" -> validBody().replace("\"title\":", "\"dueAt\":\"2000-01-01T00:00:00\",\"title\":");
            default -> throw new IllegalArgumentException(condition);
        };

        // when
        var result = mockMvc.perform(authenticatedRequest(owner, csrf).content(body));

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
        assertNoSettlement();
    }

    @Test
    @DisplayName("Swagger는 공통 생성 응답 스키마·필수 CSRF 헤더·두 요청 예시를 문서화한다")
    void apiDocs_createSettlement_matchesHttpContract() throws Exception {
        // given
        String operationPath = "/api/v1/groups/{groupId}/settlements";

        // when
        var response = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse();

        // then
        JsonNode document = objectMapper.readTree(response.getContentAsString());
        JsonNode operation = document.path("paths").path(operationPath).path("post");
        JsonNode success = resolveSchema(document,
                operation.path("responses").path("201").path("content").path("application/json").path("schema"));
        JsonNode created = resolveSchema(document, success.path("properties").path("result"));
        JsonNode csrfHeader = null;
        for (JsonNode parameter : operation.path("parameters")) {
            if (parameter.path("name").asText().equals("X-XSRF-TOKEN")) {
                csrfHeader = parameter;
            }
            assertFalse(parameter.path("name").asText().equals("memberId"));
        }
        assertNotNull(csrfHeader);
        assertTrue(csrfHeader.path("required").asBoolean());
        assertEquals("header", csrfHeader.path("in").asText());
        assertTrue(success.path("properties").has("isSuccess"));
        assertTrue(success.path("properties").has("code"));
        assertTrue(created.path("properties").has("settlementId"));
        JsonNode examples = operation.path("requestBody").path("content").path("application/json").path("examples");
        assertAll(
                () -> assertEquals("EQUAL", examples.path("균등 분할").path("value").path("splitMode").asText()),
                () -> assertEquals("CUSTOM", examples.path("일정별 개별 금액").path("value").path("splitMode").asText()),
                () -> assertTrue(operation.path("responses").has("400")),
                () -> assertTrue(operation.path("responses").has("401")),
                () -> assertTrue(operation.path("responses").has("403")),
                () -> assertTrue(operation.path("responses").has("404"))
        );
    }

    @Test
    @DisplayName("JWT·CSRF로 미리보기를 요청하면 정렬된 닉네임·청구액·나머지를 반환하고 저장하지 않는다")
    void previewSplit_ownerEqual_returnsAllocationsWithoutSaving() throws Exception {
        // given
        GroupMember third = persistGroupMember(GroupMemberRole.MEMBER);
        CsrfCredentials csrf = requestCsrfCredentials();
        String body = """
                {"totalAmount":500,"splitMode":"EQUAL","groupMemberIds":[%d,%d,%d]}
                """.formatted(third.getId(), target.getId(), owner.getId());
        entityManager.flush();
        entityManager.clear();

        // when
        var result = mockMvc.perform(previewRequest(owner, csrf).content(body));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SETTLEMENT200_1"))
                .andExpect(jsonPath("$.message").value("정산 분할 미리보기에 성공했습니다."))
                .andExpect(jsonPath("$.result.splitMode").value("EQUAL"))
                .andExpect(jsonPath("$.result.totalAmount").value(500))
                .andExpect(jsonPath("$.result.targetCount").value(3))
                .andExpect(jsonPath("$.result.items.length()").value(3))
                .andExpect(jsonPath("$.result.items[0].groupMemberId").value(owner.getId()))
                .andExpect(jsonPath("$.result.items[0].nickname").value(owner.getMember().getNickname()))
                .andExpect(jsonPath("$.result.items[0].amount").value(167))
                .andExpect(jsonPath("$.result.items[0].remainderAmount").value(1))
                .andExpect(jsonPath("$.result.items[1].groupMemberId").value(target.getId()))
                .andExpect(jsonPath("$.result.items[1].amount").value(167))
                .andExpect(jsonPath("$.result.items[1].remainderAmount").value(1))
                .andExpect(jsonPath("$.result.items[2].groupMemberId").value(third.getId()))
                .andExpect(jsonPath("$.result.items[2].amount").value(166))
                .andExpect(jsonPath("$.result.items[2].remainderAmount").value(0));
        assertNoPreviewWrites();
    }

    @Test
    @DisplayName("운영진은 본인을 포함한 개별 금액을 HTTP로 미리볼 수 있다")
    void previewSplit_managerCustom_returnsSpecifiedAmounts() throws Exception {
        // given
        GroupMember manager = persistGroupMember(GroupMemberRole.MANAGER);
        CsrfCredentials csrf = requestCsrfCredentials();
        String body = """
                {"totalAmount":500,"splitMode":"CUSTOM",
                 "memberAmounts":[{"groupMemberId":%d,"amount":300},{"groupMemberId":%d,"amount":200}]}
                """.formatted(manager.getId(), target.getId());

        // when
        var result = mockMvc.perform(previewRequest(manager, csrf).content(body));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.result.splitMode").value("CUSTOM"))
                .andExpect(jsonPath("$.result.targetCount").value(2))
                .andExpect(jsonPath("$.result.items[0].groupMemberId").value(target.getId()))
                .andExpect(jsonPath("$.result.items[0].amount").value(200))
                .andExpect(jsonPath("$.result.items[0].remainderAmount").value(0))
                .andExpect(jsonPath("$.result.items[1].groupMemberId").value(manager.getId()))
                .andExpect(jsonPath("$.result.items[1].amount").value(300))
                .andExpect(jsonPath("$.result.items[1].remainderAmount").value(0));
        assertNoPreviewWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "INVALID", "REFRESH"})
    @DisplayName("미리보기에도 유효한 Access Token이 필요하다")
    void previewSplit_invalidAuthentication_unauthorized(String condition) throws Exception {
        // given
        CsrfCredentials csrf = requestCsrfCredentials();
        var request = post(path() + "/preview").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(validPreviewBody());
        if (!condition.equals("MISSING")) {
            String token = condition.equals("INVALID") ? "invalid-token"
                    : jwtTokenProvider.issueRefreshToken(owner.getMember().getId()).value();
            request.cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, token));
        }

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401_1"));
        assertNoPreviewWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING_BOTH", "MISSING_COOKIE", "MISSING_HEADER", "MISMATCH"})
    @DisplayName("미리보기는 저장하지 않아도 POST이므로 CSRF 쿠키·헤더를 검증한다")
    void previewSplit_invalidCsrf_forbidden(String condition) throws Exception {
        // given
        CsrfCredentials csrf = requestCsrfCredentials();
        var request = post(path() + "/preview").cookie(accessTokenCookie(owner))
                .contentType(MediaType.APPLICATION_JSON).content(validPreviewBody());
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
        assertNoPreviewWrites();
    }

    @Test
    @DisplayName("JWT·CSRF가 유효해도 일반 모임 회원의 미리보기는 거부한다")
    void previewSplit_regularMember_forbidden() throws Exception {
        // given
        CsrfCredentials csrf = requestCsrfCredentials();

        // when
        var result = mockMvc.perform(previewRequest(target, csrf).content(validPreviewBody()));

        // then
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH403_1"));
        assertNoPreviewWrites();
    }

    @ParameterizedTest
    @CsvSource({"DUPLICATE,SETTLEMENT400_2", "FIELDS,SETTLEMENT400_3", "AMOUNT,SETTLEMENT400_4"})
    @DisplayName("미리보기 실제 업무 오류는 HTTP 400과 정산 도메인 코드로 반환한다")
    void previewSplit_invalidBusinessInput_returnsDomainCode(String condition, String code) throws Exception {
        // given
        CsrfCredentials csrf = requestCsrfCredentials();
        String body = switch (condition) {
            case "DUPLICATE" -> validPreviewBody().replace("[%d]".formatted(target.getId()),
                    "[%d,%d]".formatted(target.getId(), target.getId()));
            case "FIELDS" -> validPreviewBody().replace("\"totalAmount\":",
                    "\"memberAmounts\":[{\"groupMemberId\":%d,\"amount\":500}],\"totalAmount\":".formatted(target.getId()));
            case "AMOUNT" -> """
                    {"totalAmount":500,"splitMode":"CUSTOM","memberAmounts":[{"groupMemberId":%d,"amount":499}]}
                    """.formatted(target.getId());
            default -> throw new IllegalArgumentException(condition);
        };

        // when
        var result = mockMvc.perform(previewRequest(owner, csrf).content(body));

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
        assertNoPreviewWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "CLOSED", "DELETED"})
    @DisplayName("미리보기 모임이 없거나 폐쇄·삭제됐으면 GROUP404_1을 반환한다")
    void previewSplit_unavailableGroup_notFound(String condition) throws Exception {
        // given
        CsrfCredentials csrf = requestCsrfCredentials();
        Long requestedId = group.getId();
        if (condition.equals("MISSING")) {
            requestedId = Long.MAX_VALUE;
        } else if (condition.equals("CLOSED")) {
            jdbcTemplate.update("UPDATE member_group SET status = 'CLOSED' WHERE id = ?", group.getId());
        } else {
            jdbcTemplate.update("UPDATE member_group SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", group.getId());
        }
        entityManager.clear();
        var request = post("/api/v1/groups/" + requestedId + "/settlements/preview")
                .cookie(accessTokenCookie(owner), csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(validPreviewBody());

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GROUP404_1"));
        assertNoPreviewWrites();
    }

    @Test
    @DisplayName("Swagger 미리보기에는 공통 200·회원별 결과·필수 CSRF 헤더·두 요청 예시가 정의된다")
    void apiDocs_previewSplit_matchesHttpContract() throws Exception {
        // given
        String operationPath = "/api/v1/groups/{groupId}/settlements/preview";

        // when
        var response = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse();

        // then
        JsonNode document = objectMapper.readTree(response.getContentAsString());
        JsonNode operation = document.path("paths").path(operationPath).path("post");
        JsonNode success = resolveSchema(document,
                operation.path("responses").path("200").path("content").path("application/json").path("schema"));
        JsonNode preview = resolveSchema(document, success.path("properties").path("result"));
        JsonNode item = resolveSchema(document, preview.path("properties").path("items").path("items"));
        JsonNode requestContent = operation.path("requestBody").path("content").path("application/json");
        JsonNode request = resolveSchema(document, requestContent.path("schema"));
        JsonNode csrfHeader = null;
        for (JsonNode parameter : operation.path("parameters")) {
            if (parameter.path("name").asText().equals("X-XSRF-TOKEN")) {
                csrfHeader = parameter;
            }
            assertFalse(parameter.path("name").asText().equals("memberId"));
        }
        assertNotNull(csrfHeader);
        assertTrue(csrfHeader.path("required").asBoolean());
        assertEquals("header", csrfHeader.path("in").asText());
        assertAll(
                () -> assertTrue(success.path("properties").has("isSuccess")),
                () -> assertTrue(success.path("properties").has("code")),
                () -> assertTrue(preview.path("properties").has("splitMode")),
                () -> assertTrue(preview.path("properties").has("totalAmount")),
                () -> assertTrue(preview.path("properties").has("targetCount")),
                () -> assertTrue(item.path("properties").has("groupMemberId")),
                () -> assertTrue(item.path("properties").has("nickname")),
                () -> assertTrue(item.path("properties").has("amount")),
                () -> assertTrue(item.path("properties").has("remainderAmount")),
                () -> assertFalse(request.path("properties").has("title")),
                () -> assertFalse(request.path("properties").has("scheduleId")),
                () -> assertFalse(request.path("properties").has("dueAt")),
                () -> assertEquals("EQUAL", requestContent.path("examples").path("균등 분할").path("value").path("splitMode").asText()),
                () -> assertEquals("CUSTOM", requestContent.path("examples").path("회원별 금액 지정").path("value").path("splitMode").asText()),
                () -> assertTrue(operation.path("responses").has("400")),
                () -> assertTrue(operation.path("responses").has("401")),
                () -> assertTrue(operation.path("responses").has("403")),
                () -> assertTrue(operation.path("responses").has("404")),
                () -> assertFalse(operation.path("responses").has("201"))
        );
    }

    private GroupMember persistGroupMember(GroupMemberRole role) {
        String key = UUID.randomUUID().toString();
        Member member = Member.builder().socialType(SocialType.KAKAO).socialId(key).nickname("회원" + key).build();
        entityManager.persist(member);
        GroupMember membership = GroupMember.builder().group(group).member(member).role(role)
                .status(GroupMemberStatus.ACTIVE).joinedAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
        entityManager.persist(membership);
        return membership;
    }

    private Settlement persistListSettlement(String title, List<GroupMember> targets) {
        Settlement settlement = Settlement.builder().group(group).createdByGroupMember(owner).title(title)
                .totalAmount(targets.size() * 100L).splitMode(SettlementSplitMode.EQUAL).build();
        entityManager.persist(settlement);
        targets.forEach(member -> entityManager.persist(SettlementItem.builder()
                .settlement(settlement).groupMember(member).amount(100L).build()));
        entityManager.flush();
        jdbcTemplate.update("UPDATE settlement SET created_at = '2026-10-20 09:00:00' WHERE id = ?", settlement.getId());
        return settlement;
    }

    private CsrfCredentials requestCsrfCredentials() throws Exception {
        var result = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("result").path("token").asText();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        assertFalse(token.isBlank());
        return new CsrfCredentials(cookie, token);
    }

    private MockHttpServletRequestBuilder authenticatedRequest(GroupMember actor, CsrfCredentials csrf) {
        return post(path()).cookie(accessTokenCookie(actor), csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder previewRequest(GroupMember actor, CsrfCredentials csrf) {
        return post(path() + "/preview").cookie(accessTokenCookie(actor), csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON);
    }

    private String validPreviewBody() {
        return """
                {"totalAmount":500,"splitMode":"EQUAL","groupMemberIds":[%d]}
                """.formatted(target.getId());
    }

    private void assertNoPreviewWrites() {
        entityManager.flush();
        assertAll(
                this::assertNoSettlement,
                () -> assertEquals(0, jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM settlement_item si JOIN group_member gm
                        ON gm.id = si.group_member_id WHERE gm.group_id = ?
                        """, Integer.class, group.getId()))
        );
    }

    private Cookie accessTokenCookie(GroupMember actor) {
        return new Cookie(AuthCookieNames.ACCESS_TOKEN, jwtTokenProvider.issueAccessToken(actor.getMember().getId()).value());
    }

    private String path() {
        return "/api/v1/groups/" + group.getId() + "/settlements";
    }

    private String validBody() {
        return """
                {"title":"정산","totalAmount":500,"splitMode":"EQUAL","groupMemberIds":[%d]}
                """.formatted(target.getId());
    }

    private void assertNoSettlement() {
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlement WHERE group_id = ?", Integer.class, group.getId()));
    }

    private JsonNode resolveSchema(JsonNode document, JsonNode schema) {
        return schema.has("$ref") ? document.at(schema.path("$ref").asText().substring(1)) : schema;
    }

    private record CsrfCredentials(Cookie cookie, String token) {
    }
}
