package com.moeasy.moeasybe.domain.member.controller;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moeasy.moeasybe.domain.auth.config.AuthCookieNames;
import com.moeasy.moeasybe.domain.group.entity.Category;
import com.moeasy.moeasybe.domain.group.repository.CategoryRepository;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import com.moeasy.moeasybe.domain.region.entity.Region;
import com.moeasy.moeasybe.domain.region.repository.RegionRepository;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class MemberOnboardingApiIntegrationTest {

    private static final String PATH = "/api/v1/members/me/onboarding";
    private static final String CSRF_TOKEN = "onboarding-test-csrf";
    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> regionIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RegionRepository regionRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        for (Long id : memberIds) {
            jdbcTemplate.update("DELETE FROM member_category WHERE member_id = ?", id);
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", id);
        }
        for (Long id : regionIds) {
            jdbcTemplate.update("DELETE FROM region WHERE id = ?", id);
        }
        for (Long id : categoryIds) {
            jdbcTemplate.update("DELETE FROM category WHERE id = ?", id);
        }
    }

    @Test
    @DisplayName("온보딩 성공 시 회원과 선택한 관심사를 실제 DB에 함께 저장한다")
    void completeOnboarding_validRequest_persistsMemberAndInterests() throws Exception {
        // given
        Member member = member();
        String nickname = nickname();
        String body = body(nickname, "함께 달려요", "11680", "RUNNING", "STUDY", "READING");
        // when
        ResultActions result = mockMvc.perform(request(member).content(body));
        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MEMBER200_2"))
                .andExpect(jsonPath("$.result.memberId").value(member.getId()))
                .andExpect(jsonPath("$.result.onboardingCompleted").value(true));
        assertAll(
                () -> assertEquals(nickname, value(member.getId(), "nickname", String.class)),
                () -> assertEquals("함께 달려요", value(member.getId(), "status_message", String.class)),
                () -> assertTrue(value(member.getId(), "onboarding_completed", Boolean.class)),
                () -> assertEquals("11680", jdbcTemplate.queryForObject(
                        "SELECT r.code FROM member m JOIN region r ON r.id = m.primary_region_id WHERE m.id = ?",
                        String.class, member.getId())),
                () -> assertEquals(List.of("READING", "RUNNING", "STUDY"), interestCodes(member.getId()))
        );
    }

    @Test
    @DisplayName("소개를 생략하고 관심사 5개를 선택할 수 있다")
    void completeOnboarding_optionalIntroductionAndFiveCategories_succeeds() throws Exception {
        // given
        Member member = member();
        String body = body(nickname(), null, "11680", "RUNNING", "STUDY", "READING", "HIKING", "TRAVEL")
                .replace("\"statusMessage\":null,", "");
        // when
        ResultActions result = mockMvc.perform(request(member).content(body));
        // then
        result.andExpect(status().isOk());
        assertAll(
                () -> assertNull(value(member.getId(), "status_message", String.class)),
                () -> assertEquals(5, interestCodes(member.getId()).size())
        );
    }

    @Test
    @DisplayName("사전 중복 확인 이후 다른 회원이 사용한 닉네임은 409로 거절한다")
    void completeOnboarding_duplicateNickname_returnsConflictWithoutChanges() throws Exception {
        // given
        Member member = member();
        Member other = member();
        String nickname = nickname();
        jdbcTemplate.update("UPDATE member SET nickname = ? WHERE id = ?", nickname, other.getId());
        // when
        ResultActions result = mockMvc.perform(request(member).content(validBody(nickname)));
        // then
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBER409_1"));
        assertUnchanged(member.getId());
    }

    @Test
    @DisplayName("이미 온보딩을 완료한 회원은 재요청으로 프로필을 덮어쓰지 않는다")
    void completeOnboarding_completedMember_returnsConflict() throws Exception {
        // given
        Member member = member();
        jdbcTemplate.update("UPDATE member SET nickname = ?, onboarding_completed = TRUE WHERE id = ?",
                "기존" + nickname(), member.getId());
        String previous = value(member.getId(), "nickname", String.class);
        // when
        ResultActions result = mockMvc.perform(request(member).content(validBody(nickname())));
        // then
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBER409_2"));
        assertEquals(previous, value(member.getId(), "nickname", String.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"99999", "99998"})
    @DisplayName("존재하지 않거나 삭제된 지역은 거절하고 회원을 변경하지 않는다")
    void completeOnboarding_unavailableRegion_returnsBadRequest(String code) throws Exception {
        // given
        Member member = member();
        if (code.equals("99998")) {
            Region region = regionRepository.saveAndFlush(Region.builder()
                    .code(code).name("테스트 지역").provinceName("테스트 시도").build());
            regionIds.add(region.getId());
            jdbcTemplate.update("UPDATE region SET deleted_at = '2026-09-30 12:00:00' WHERE id = ?", region.getId());
        }
        String body = body(nickname(), null, code, "RUNNING", "STUDY", "READING");
        // when
        ResultActions result = mockMvc.perform(request(member).content(body));
        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MEMBER400_1"));
        assertUnchanged(member.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN_CATEGORY", "DELETED_CATEGORY", "RUNNING"})
    @DisplayName("없는 관심사, 삭제된 관심사, 중복 선택은 거절한다")
    void completeOnboarding_invalidCategory_returnsBadRequest(String code) throws Exception {
        // given
        Member member = member();
        if (code.equals("DELETED_CATEGORY")) {
            Category category = categoryRepository.saveAndFlush(Category.builder().code(code).name("테스트 관심사").build());
            categoryIds.add(category.getId());
            jdbcTemplate.update("UPDATE category SET deleted_at = '2026-09-30 12:00:00' WHERE id = ?", category.getId());
        }
        String body = body(nickname(), null, "11680", "RUNNING", "STUDY", code);
        // when
        ResultActions result = mockMvc.perform(request(member).content(body));
        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MEMBER400_2"));
        assertUnchanged(member.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BANNED", "DELETED"})
    @DisplayName("사용 불가 상태의 회원은 온보딩할 수 없다")
    void completeOnboarding_unavailableMember_returnsForbidden(String memberStatus) throws Exception {
        // given
        Member member = member();
        jdbcTemplate.update("UPDATE member SET status = ? WHERE id = ?", memberStatus, member.getId());
        // when
        ResultActions result = mockMvc.perform(request(member).content(validBody(nickname())));
        // then
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MEMBER403_1"));
        assertUnchanged(member.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"nickname\":\"가\",\"regionCode\":\"11680\",\"categoryCodes\":[\"RUNNING\",\"STUDY\",\"READING\"]}",
            "{\"nickname\":\"   \",\"regionCode\":\"11680\",\"categoryCodes\":[\"RUNNING\",\"STUDY\",\"READING\"]}",
            "{\"regionCode\":\"11680\",\"categoryCodes\":[\"RUNNING\",\"STUDY\",\"READING\"]}",
            "{\"nickname\":\"모이지\",\"categoryCodes\":[\"RUNNING\",\"STUDY\",\"READING\"]}",
            "{\"nickname\":\"모이지\",\"regionCode\":\"서울\",\"categoryCodes\":[\"RUNNING\",\"STUDY\",\"READING\"]}",
            "{\"nickname\":\"모이지\",\"regionCode\":\"11680\"}",
            "{\"nickname\":\"모이지\",\"regionCode\":\"11680\",\"categoryCodes\":[\"RUNNING\",\"STUDY\"]}",
            "{\"nickname\":\"모이지\",\"regionCode\":\"11680\",\"categoryCodes\":[\"RUNNING\",\"STUDY\",\"READING\",\"HIKING\",\"TRAVEL\",\"FOOD\"]}",
            "{\"nickname\":\"모이지\",\"regionCode\":\"11680\",\"categoryCodes\":[null,\"STUDY\",\"READING\"]}"
    })
    @DisplayName("필수 필드와 길이, 지역 형식, 관심사 개수와 요소를 검증한다")
    void completeOnboarding_invalidFields_returnsValidationError(String body) throws Exception {
        // given
        Member member = member();
        // when
        ResultActions result = mockMvc.perform(request(member).content(body));
        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALID400_1"));
        assertUnchanged(member.getId());
    }

    @Test
    @DisplayName("60자를 초과하는 소개는 거절한다")
    void completeOnboarding_longIntroduction_returnsValidationError() throws Exception {
        // given
        Member member = member();
        String body = body(nickname(), "가".repeat(61), "11680", "RUNNING", "STUDY", "READING");
        // when
        ResultActions result = mockMvc.perform(request(member).content(body));
        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.result.statusMessage").isArray());
        assertUnchanged(member.getId());
    }

    @Test
    @DisplayName("CSRF 쿠키만 있고 헤더가 없으면 403으로 거절한다")
    void completeOnboarding_missingCsrfHeader_returnsForbidden() throws Exception {
        // given
        Member member = member();
        var request = post(PATH).cookie(accessCookie(member), new Cookie("XSRF-TOKEN", CSRF_TOKEN))
                .contentType(MediaType.APPLICATION_JSON).content(validBody(nickname()));
        // when
        ResultActions result = mockMvc.perform(request);
        // then
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH403_1"));
        assertUnchanged(member.getId());
    }

    @Test
    @DisplayName("CSRF 쿠키와 헤더가 다르면 403으로 거절한다")
    void completeOnboarding_invalidCsrfHeader_returnsForbidden() throws Exception {
        // given
        Member member = member();
        var request = post(PATH).cookie(accessCookie(member), new Cookie("XSRF-TOKEN", CSRF_TOKEN))
                .header("X-XSRF-TOKEN", "wrong-token").contentType(MediaType.APPLICATION_JSON)
                .content(validBody(nickname()));
        // when
        ResultActions result = mockMvc.perform(request);
        // then
        result.andExpect(status().isForbidden());
        assertUnchanged(member.getId());
    }

    @Test
    @DisplayName("인증 쿠키가 없으면 유효한 CSRF 토큰이 있어도 401로 거절한다")
    void completeOnboarding_anonymous_returnsUnauthorized() throws Exception {
        // given
        var request = post(PATH).cookie(new Cookie("XSRF-TOKEN", CSRF_TOKEN)).header("X-XSRF-TOKEN", CSRF_TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(validBody(nickname()));
        // when
        ResultActions result = mockMvc.perform(request);
        // then
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401_1"));
    }

    @Test
    @DisplayName("토큰의 회원이 존재하지 않으면 404로 응답한다")
    void completeOnboarding_missingMember_returnsNotFound() throws Exception {
        // given
        Member member = member();
        var request = request(member).content(validBody(nickname()));
        memberRepository.deleteById(member.getId());
        // when
        ResultActions result = mockMvc.perform(request);
        // then
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER404_1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{invalid-json"})
    @DisplayName("누락되거나 잘못된 JSON 본문은 400으로 응답한다")
    void completeOnboarding_invalidBody_returnsBadRequest(String body) throws Exception {
        // given
        Member member = member();
        // when
        ResultActions result = mockMvc.perform(request(member).content(body));
        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON400_1"));
        assertUnchanged(member.getId());
    }

    @Test
    @DisplayName("Swagger는 온보딩 JSON 요청과 응답, CSRF 헤더를 설명한다")
    void apiDocs_onboarding_describesRequestResponseAndCsrf() throws Exception {
        // given
        var request = get("/v3/api-docs");
        // when
        var response = mockMvc.perform(request).andReturn().getResponse();
        var document = JsonPath.parse(response.getContentAsString());
        String operation = "$.paths['" + PATH + "'].post";
        String responseRef = document.read(operation + ".responses['200'].content['application/json'].schema['$ref']");
        String schema = responseRef.substring(responseRef.lastIndexOf('/') + 1);
        String resultRef = document.read("$.components.schemas['" + schema + "'].properties.result['$ref']");
        String resultSchema = resultRef.substring(resultRef.lastIndexOf('/') + 1);
        // then
        assertAll(
                () -> assertEquals(200, response.getStatus()),
                () -> assertEquals(java.util.Set.of("application/json"), document.read(
                        "$.paths['" + PATH + "'].post.responses['200'].content", java.util.Map.class).keySet()),
                () -> assertEquals("#/components/schemas/MemberOnboardingRequest", document.read(operation
                        + ".requestBody.content['application/json'].schema['$ref']", String.class)),
                () -> assertEquals(List.of("X-XSRF-TOKEN"), document.read(operation + ".parameters[*].name")),
                () -> assertEquals("boolean", document.read("$.components.schemas['" + resultSchema
                        + "'].properties.onboardingCompleted.type", String.class))
        );
    }

    @Test
    @DisplayName("서로 다른 회원이 같은 닉네임으로 동시에 온보딩하면 한 요청만 저장된다")
    void completeOnboarding_concurrentNicknameRequests_onlyOneCommits() throws Exception {
        // given
        Member first = member();
        Member second = member();
        String nickname = nickname();
        var firstRequest = request(first).content(validBody(nickname));
        var secondRequest = request(second).content(validBody(nickname));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        // when
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return mockMvc.perform(firstRequest).andReturn().getResponse().getStatus();
            });
            var secondResult = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return mockMvc.perform(secondRequest).andReturn().getResponse().getStatus();
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int firstStatus = firstResult.get(10, TimeUnit.SECONDS);
            int secondStatus = secondResult.get(10, TimeUnit.SECONDS);
            List<Integer> statuses = new ArrayList<>(List.of(firstStatus, secondStatus));
            statuses.sort(Integer::compareTo);
            // then
            assertEquals(List.of(200, 409), statuses);
            assertEquals(3, interestCodes(first.getId()).size() + interestCodes(second.getId()).size());
            assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member WHERE nickname = ?", Integer.class, nickname));
            assertUnchanged(firstStatus == 409 ? first.getId() : second.getId());
        } finally {
            start.countDown();
        }
    }

    @Test
    @DisplayName("자신의 기존 닉네임과 소개 60자는 온보딩에 사용할 수 있다")
    void completeOnboarding_ownNicknameAndIntroductionBoundary_succeeds() throws Exception {
        // given
        Member member = member();
        String nickname = nickname();
        jdbcTemplate.update("UPDATE member SET nickname = ? WHERE id = ?", nickname, member.getId());
        String body = body(nickname, "가".repeat(60), "11680", "RUNNING", "STUDY", "READING");
        // when
        ResultActions result = mockMvc.perform(request(member).content(body));
        // then
        result.andExpect(status().isOk());
        assertEquals("가".repeat(60), value(member.getId(), "status_message", String.class));
    }

    @Test
    @DisplayName("13자 닉네임은 저장하지 않는다")
    void completeOnboarding_longNickname_returnsValidationError() throws Exception {
        // given
        Member member = member();
        String body = validBody("가".repeat(13));
        // when
        ResultActions result = mockMvc.perform(request(member).content(body));
        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.result.nickname").isArray());
        assertUnchanged(member.getId());
    }

    @Test
    @DisplayName("deleted_at이 설정된 회원은 ACTIVE 상태라도 저장하지 않는다")
    void completeOnboarding_softDeletedMember_returnsForbidden() throws Exception {
        // given
        Member member = member();
        jdbcTemplate.update("UPDATE member SET deleted_at = '2026-09-30 12:00:00' WHERE id = ?", member.getId());
        // when
        ResultActions result = mockMvc.perform(request(member).content(validBody(nickname())));
        // then
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MEMBER403_1"));
        assertUnchanged(member.getId());
    }

    @Test
    @DisplayName("미완료 회원의 기존 관심사는 최종 제출한 관심사로 교체한다")
    void completeOnboarding_existingInterests_replacesWithSubmittedCategories() throws Exception {
        // given
        Member member = member();
        jdbcTemplate.update("INSERT INTO member_category (member_id, category_id, created_at) "
                + "SELECT ?, id, CURRENT_TIMESTAMP FROM category WHERE code = 'FOOD'", member.getId());
        // when
        ResultActions result = mockMvc.perform(request(member).content(validBody(nickname())));
        // then
        result.andExpect(status().isOk());
        assertEquals(List.of("READING", "RUNNING", "STUDY"), interestCodes(member.getId()));
    }

    private MockHttpServletRequestBuilder request(Member member) {
        return post(PATH).cookie(accessCookie(member), new Cookie("XSRF-TOKEN", CSRF_TOKEN))
                .header("X-XSRF-TOKEN", CSRF_TOKEN).contentType(MediaType.APPLICATION_JSON);
    }

    private Cookie accessCookie(Member member) {
        return new Cookie(AuthCookieNames.ACCESS_TOKEN, jwtTokenProvider.issueAccessToken(member.getId()).value());
    }

    private Member member() {
        Member member = memberRepository.saveAndFlush(Member.builder().socialType(SocialType.KAKAO)
                .socialId(UUID.randomUUID().toString()).build());
        memberIds.add(member.getId());
        return member;
    }

    private String nickname() {
        return "온" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String validBody(String nickname) {
        return body(nickname, null, "11680", "RUNNING", "STUDY", "READING");
    }

    private String body(String nickname, String introduction, String region, String... codes) {
        return "{\"nickname\":\"" + nickname + "\",\"statusMessage\":"
                + (introduction == null ? "null" : "\"" + introduction + "\"")
                + ",\"regionCode\":\"" + region + "\",\"categoryCodes\":[\"" + String.join("\",\"", codes) + "\"]}";
    }

    private <T> T value(Long memberId, String column, Class<T> type) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM member WHERE id = ?", type, memberId);
    }

    private List<String> interestCodes(Long memberId) {
        return jdbcTemplate.queryForList("SELECT c.code FROM member_category mc JOIN category c ON c.id = mc.category_id "
                + "WHERE mc.member_id = ? ORDER BY c.code", String.class, memberId);
    }

    private void assertUnchanged(Long memberId) {
        assertAll(
                () -> assertNull(value(memberId, "nickname", String.class)),
                () -> assertNull(value(memberId, "primary_region_id", Long.class)),
                () -> assertFalse(value(memberId, "onboarding_completed", Boolean.class)),
                () -> assertTrue(interestCodes(memberId).isEmpty())
        );
    }
}
