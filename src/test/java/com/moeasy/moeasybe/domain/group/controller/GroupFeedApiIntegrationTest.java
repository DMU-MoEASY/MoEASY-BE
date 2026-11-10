package com.moeasy.moeasybe.domain.group.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.moeasy.moeasybe.domain.group.entity.*;
import com.moeasy.moeasybe.domain.group.enums.*;
import com.moeasy.moeasybe.domain.member.entity.*;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.region.entity.Region;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GroupFeedApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider jwt;
    @MockitoBean(name = "clock") Clock clock;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private Member viewer;
    private Member owner;
    private Category running;
    private Category study;
    private final List<Long> expected = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(clock.withZone(KST)).thenReturn(Clock.fixed(NOW.atZone(KST).toInstant(), KST));
        db.update("UPDATE feed_advertisement SET active=FALSE");
        running = category("RUNNING");
        study = category("STUDY");
        viewer = member();
        owner = member();
        Region region = em.createQuery("select r from Region r where r.code='11680'", Region.class).getSingleResult();
        viewer.completeOnboarding(viewer.getNickname(), "", region);
        em.persist(MemberCategory.builder().member(viewer).category(running).build());
        em.flush();
    }

    @Test
    @DisplayName("추천 피드는 최신 공통 인증 계약의 Bearer 액세스 토큰도 허용한다")
    void getFeed_bearerAccessToken_returnsGroups() throws Exception {
        // given
        seed(1, 0);
        String token = jwt.issueAccessToken(viewer.getId()).value();
        // when
        var response = mvc.perform(get("/api/v1/groups").param("sort", "RECOMMENDED").param("query", "feed38")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token));
        // then
        response.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("GROUP200_10"))
                .andExpect(jsonPath("$.result.totalGroupCount").value(1))
                .andExpect(jsonPath("$.result.items[0].groupId").value(expected.getFirst()));
    }

    @Test
    @DisplayName("실제 인증 API로 17+2+광고2를 8+1+A, 8+1+B, 1로 소비하고 중복·누락 없이 종료한다")
    void getFeed_threePages_consumesNineteenGroupsOnce() throws Exception {
        // given
        seed(17, 2);
        long adA = ad(true, NOW, NOW.plusDays(1));
        long adB = ad(true, NOW.minusDays(1), NOW.plusDays(1));
        // when
        JsonNode first = feed(null);
        String cursor = first.path("nextCursor").stringValue();
        JsonNode second = feed(cursor);
        JsonNode last = feed(second.path("nextCursor").stringValue());
        // then
        assertPage(first, 8, 1, adA);
        assertPage(second, 8, 1, adB);
        assertAll(() -> assertEquals(1, last.path("items").size()),
                () -> assertFalse(last.path("hasNext").booleanValue()),
                () -> assertTrue(last.path("nextCursor").isNull()));
        List<Long> actual = new ArrayList<>();
        for (JsonNode page : List.of(first, second, last)) {
            assertEquals(19, page.path("totalGroupCount").longValue());
            page.path("items").forEach(item -> {
                if (item.path("type").stringValue().equals("GROUP")) actual.add(item.path("groupId").longValue());
                assertFalse(item.has("score"));
            });
        }
        assertAll(() -> assertEquals(19, actual.size()),
                () -> assertEquals(new HashSet<>(expected), new HashSet<>(actual)),
                () -> assertEquals(second, feed(cursor)));
    }

    @ParameterizedTest
    @CsvSource({"17,2,2,8,1,1", "19,0,1,9,0,1", "19,2,0,9,1,0", "21,0,0,10,0,0",
            "7,2,1,7,0,0", "8,2,0,8,0,0", "8,0,1,8,0,0", "0,2,1,0,0,0"})
    @DisplayName("광고·낮은 관련성 유무에 따른 완성 페이지와 짧은 끝 페이지 정책을 지킨다")
    void getFeed_combinations_matchesComposition(int normal, int low, int ads, int groups, int discoveries, int adCount)
            throws Exception {
        // given
        seed(normal, low);
        for (int i = 0; i < ads; i++) ad(true, NOW.minusDays(1), NOW.plusDays(1));
        // when
        JsonNode result = feed(null);
        // then
        assertEquals(groups + discoveries + adCount, result.path("items").size());
        assertEquals(normal + low, result.path("totalGroupCount").longValue());
        assertEquals(normal > groups, result.path("hasNext").booleanValue());
        if (adCount == 1) assertEquals("AD", result.path("items").get(9).path("type").stringValue());
        if (normal <= groups) assertTrue(result.path("nextCursor").isNull());
    }

    @Test
    @DisplayName("가입·대기·강퇴·만석·보관·폐쇄·삭제는 제외하고 LEFT·취소 신청은 허용한다")
    void getFeed_ineligibleCandidates_excludesWithApprovalCapacityRules() throws Exception {
        // given
        for (String state : List.of("ACTIVE", "KICKED", "PENDING", "FULL", "ARCHIVED", "CLOSED", "DELETED", "LEFT", "CANCELLED")) {
            long id = group(true);
            if (List.of("ACTIVE", "KICKED", "LEFT").contains(state)) {
                db.update("INSERT INTO group_member(group_id,member_id,status) VALUES (?,?,?)", id, viewer.getId(), state);
                if (state.equals("KICKED")) db.update("UPDATE group_member SET deleted_at=? WHERE group_id=? AND member_id=?", NOW, id, viewer.getId());
            } else if (state.equals("PENDING") || state.equals("CANCELLED")) {
                db.update("INSERT INTO group_join_request(group_id,member_id,introduction,status) VALUES (?,?,'hi',?)", id, viewer.getId(), state);
            } else if (state.equals("FULL")) {
                db.update("UPDATE member_group SET max_members=1 WHERE id=?", id);
                // 가입 승인 기준은 계정 상태와 무관한 ACTIVE 미삭제 멤버십이다.
            } else if (state.equals("DELETED")) db.update("UPDATE member_group SET deleted_at=? WHERE id=?", NOW, id);
            else db.update("UPDATE member_group SET status=? WHERE id=?", state, id);
        }
        db.update("UPDATE member SET status='BANNED' WHERE id=?", owner.getId());
        // when
        JsonNode result = feed(null);
        // then
        assertAll(() -> assertEquals(2, result.path("totalGroupCount").intValue()),
                () -> assertEquals(2, result.path("items").size()),
                () -> assertEquals(1, result.path("items").get(0).path("currentMemberCount").intValue()));
    }

    @Test
    @DisplayName("관심사 50·지역 30·티어 5/10·활동 10을 한번씩 가산하고 생성일·ID로 동점을 정렬한다")
    void getFeed_scoringAndTies_returnsReasonsWithoutScore() throws Exception {
        // given
        long interest = group(true); // 80
        long gold = group(true); // 90
        long recent = group(true); // 100 (multiple schedules still once)
        long silver = group(true); // 85
        long regionOnly = group(false); // 30, 일반 후보
        db.update("UPDATE member_group SET region_code='11680' WHERE id=?", regionOnly);
        db.update("UPDATE member_group SET tier='gold' WHERE id IN (?,?)", gold, recent);
        db.update("UPDATE member_group SET tier='silver' WHERE id=?", silver);
        schedule(recent, NOW, "SCHEDULED", false);
        schedule(recent, NOW.minusDays(1), "SCHEDULED", false);
        long tiedNew = group(true);
        long tiedOld = group(true);
        db.update("UPDATE member_group SET created_at=? WHERE id=?", NOW.minusDays(2), tiedOld);
        // when
        JsonNode result = feed(null);
        // then
        assertEquals(List.of(recent, gold, silver, tiedNew, interest, tiedOld, regionOnly), ids(result));
        JsonNode card = result.path("items").get(0);
        assertEquals(List.of("INTEREST_MATCH", "REGION_MATCH", "GOLD_TIER", "RECENT_ACTIVITY"),
                mapper.convertValue(card.path("recommendationReasons"), List.class));
        assertFalse(card.has("score"));
    }

    @ParameterizedTest
    @CsvSource({"-2592000000000,SCHEDULED,false,false", "-2591999999999,SCHEDULED,false,true",
            "0,SCHEDULED,false,true", "1,SCHEDULED,false,false", "-1,CANCELLED,false,false", "-1,SCHEDULED,true,false"})
    @DisplayName("최근 활동은 T-30일 초과 T 이하이며 취소·삭제 일정을 제외한다")
    void getFeed_activityBoundary_usesFirstRequestKoreanTime(long micros, String status, boolean deleted, boolean expectedRecent)
            throws Exception {
        // given
        long id = group(true);
        schedule(id, NOW.plusNanos(micros * 1000), status, deleted);
        // when
        JsonNode card = feed(null).path("items").get(0);
        // then
        assertEquals(expectedRecent, card.path("recommendationReasons").toString().contains("RECENT_ACTIVITY"));
    }

    @ParameterizedTest
    @CsvSource({"true,0,1,true", "true,-1,0,false", "true,1,2,false", "false,-1,1,false"})
    @DisplayName("광고 활성 여부와 시작 포함·종료 제외 경계를 지킨다")
    void getFeed_adBoundary_selectsOnlyEligible(boolean active, int starts, int ends, boolean eligible) throws Exception {
        // given
        seed(9, 0);
        ad(active, NOW.plusSeconds(starts), NOW.plusSeconds(ends));
        // when
        JsonNode result = feed(null);
        // then
        assertEquals(eligible ? 10 : 9, result.path("items").size());
        if (eligible) assertEquals("AD", result.path("items").get(9).path("type").stringValue());
    }

    @Test
    @DisplayName("광고 두 개는 A B A로 순환하며 관심사·지역 없는 회원도 일반 후보와 광고를 받는다")
    void getFeed_noPreferences_rotatesAds() throws Exception {
        // given
        db.update("DELETE FROM member_category WHERE member_id=?", viewer.getId());
        db.update("UPDATE member SET primary_region_id=NULL WHERE id=?", viewer.getId());
        em.clear();
        seed(28, 0);
        long a = ad(true, NOW.minusDays(1), NOW.plusDays(1));
        long b = ad(true, NOW.minusDays(1), NOW.plusDays(1));
        // when
        JsonNode first = feed(null);
        JsonNode second = feed(first.path("nextCursor").stringValue());
        JsonNode third = feed(second.path("nextCursor").stringValue());
        // then
        assertEquals(List.of(a, b, a), List.of(first, second, third).stream()
                .map(p -> p.path("items").get(9).path("adId").longValue()).toList());
    }

    @Test
    @DisplayName("검색·카테고리·지역 필터를 분류 전에 적용하고 와일드카드를 문자로 검색한다")
    void getFeed_filters_appliesBeforeClassification() throws Exception {
        // given
        seed(17, 2);
        ad(true, NOW.minusDays(1), NOW.plusDays(1));
        // when
        JsonNode result = request(null, "feed38", "RUNNING", "11680");
        // then
        assertAll(() -> assertEquals(17, result.path("totalGroupCount").intValue()),
                () -> assertEquals(10, result.path("items").size()),
                () -> assertEquals("GROUP", result.path("items").get(8).path("type").stringValue()));
        assertEquals(0, request(null, "%", null, null).path("totalGroupCount").intValue());
        assertEquals(17, request(null, "러닝", "RUNNING", null).path("totalGroupCount").intValue());
        assertEquals(17, request(null, "강남구", "RUNNING", null).path("totalGroupCount").intValue());
        assertEquals(19, request(null, "feed38", "RUNNING,STUDY", null).path("totalGroupCount").intValue());
        assertEquals(0, request(null, "feed38", "STUDY", null).path("items").size());
    }

    @Test
    @DisplayName("다음 요청에서 가입·만석·폐쇄 자격을 다시 적용하고 기준 시각과 광고 순번을 유지한다")
    void getFeed_changedEligibility_skipsUnavailableWithoutOffsetLoss() throws Exception {
        // given
        seed(20, 2);
        long a = ad(true, NOW.minusDays(1), NOW.plusDays(1));
        long b = ad(true, NOW.minusDays(1), NOW.plusDays(1));
        JsonNode first = feed(null);
        List<Long> unseen = expected.stream().filter(id -> !ids(first).contains(id)).toList();
        long joined = unseen.get(0), full = unseen.get(1), closed = unseen.get(2);
        db.update("INSERT INTO group_member(group_id,member_id,status) VALUES (?,?,'ACTIVE')", joined, viewer.getId());
        db.update("UPDATE member_group SET max_members=1 WHERE id=?", full);
        db.update("UPDATE member_group SET status='CLOSED' WHERE id=?", closed);
        when(clock.withZone(KST)).thenReturn(Clock.fixed(NOW.plusDays(40).atZone(KST).toInstant(), KST));
        // when
        JsonNode next = feed(first.path("nextCursor").stringValue());
        // then
        assertEquals(19, next.path("totalGroupCount").intValue());
        assertTrue(Collections.disjoint(ids(first), ids(next)));
        assertTrue(Collections.disjoint(List.of(joined, full, closed), ids(next)));
        assertEquals(a, first.path("items").get(9).path("adId").longValue());
        assertEquals(b, next.path("items").get(9).path("adId").longValue());
    }

    @Test
    @DisplayName("손상·타 회원·조건 변경 커서와 잘못된 요청을 400으로 거부하며 익명은 401이다")
    void getFeed_invalidCursorOrFilters_returns400() throws Exception {
        // given
        seed(20, 0);
        String cursor = feed(null).path("nextCursor").stringValue();
        Cookie auth = cookie(viewer.getId());
        // when
        var invalid = mvc.perform(get("/api/v1/groups").cookie(auth).param("cursor", "broken"));
        // then
        invalid.andExpect(status().isBadRequest());
        for (String value : List.of(cursor + "x", "", "not.base64")) {
            mvc.perform(get("/api/v1/groups").cookie(auth).param("cursor", value)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/groups").cookie(auth).param("cursor", cursor).param("query", "changed"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/groups").cookie(cookie(owner.getId())).param("cursor", cursor).param("query", "feed38"))
                .andExpect(status().isBadRequest());
        for (String[] pair : List.of(new String[]{"sort", "LATEST"}, new String[]{"categoryCodes", "UNKNOWN"},
                new String[]{"regionCode", "99999"}, new String[]{"regionCode", "서울"}, new String[]{"query", "x".repeat(101)})) {
            mvc.perform(get("/api/v1/groups").cookie(auth).param(pair[0], pair[1])).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/groups")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("광고 DB는 종료 시각이 시작 시각 이하인 소재를 저장하지 않는다")
    void advertisement_invalidPeriod_rejectsAtDatabase() {
        // given
        String sql = "INSERT INTO feed_advertisement(advertiser_name,title,image_url,landing_url,starts_at,ends_at) VALUES ('x','x','/feed-ads/a.svg',?,?,?)";
        // when
        var invalidPeriod = assertThrows(org.springframework.dao.DataAccessException.class,
                () -> db.update(sql, "https://example.com", NOW, NOW));
        // then
        assertTrue(invalidPeriod.getMostSpecificCause().getMessage().contains("ck_feed_ad_period"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com", "https://@", "https://", "https://[broken", "https://[]",
            "https://[:::]", "https://example..com", "https://-example.com", "https://example.com:abc",
            "https://example.com:65536", "https://999.999.999.999", "https://example.com/a b",
            "https://example.com/a\\b", "https://user@example.com"})
    @DisplayName("광고 DB는 호스트·포트가 잘못되거나 HTTPS가 아닌 랜딩 URL을 거부한다")
    void advertisement_invalidLandingUrl_rejectsAtDatabase(String landingUrl) {
        // given
        String sql = "INSERT INTO feed_advertisement(advertiser_name,title,image_url,landing_url,starts_at,ends_at) VALUES ('x','x','/feed-ads/ad-a.svg',?,?,?)";
        // when
        var invalid = assertThrows(org.springframework.dao.DataAccessException.class,
                () -> db.update(sql, landingUrl, NOW, NOW.plusSeconds(1)));
        // then
        assertTrue(invalid.getMostSpecificCause().getMessage().contains("ck_feed_ad_https"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "https://ads.example.com/campaign?q=a%20b#details",
            "https://xn--3e0b707e.com", "https://localhost:8443/", "https://127.0.0.1:65535/",
            "https://[::1]/", "https://[2001:db8::1]:443/?ad=1", "https://[::ffff:192.0.2.1]/"})
    @DisplayName("광고 DB는 HTTPS 도메인·IPv4·IPv6 및 정상 포트와 경로를 허용한다")
    void advertisement_validLandingUrl_storesWithoutChanges(String landingUrl) {
        // given
        String sql = "INSERT INTO feed_advertisement(advertiser_name,title,image_url,landing_url,starts_at,ends_at) VALUES ('x','x','/feed-ads/ad-a.svg',?,?,?)";
        // when
        db.update(sql, landingUrl, NOW, NOW.plusSeconds(1));
        // then
        assertEquals(landingUrl, db.queryForObject(
                "SELECT landing_url FROM feed_advertisement WHERE id=LAST_INSERT_ID()", String.class));
    }

    @Test
    @DisplayName("Swagger 성공 응답에서 ApiResponse·Feed·Item 스키마까지 연결하고 예시를 유지한다")
    void docs_feedResponse_linksReturnTypeAndExamples() throws Exception {
        // given
        var docs = get("/v3/api-docs");
        // when
        JsonNode document = mapper.readTree(mvc.perform(docs).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        // then
        JsonNode media = document.path("paths").path("/api/v1/groups").path("get").path("responses")
                .path("200").path("content").path("application/json");
        JsonNode schemas = document.path("components").path("schemas");
        assertAll(() -> assertEquals("#/components/schemas/ApiResponseFeed", media.path("schema").path("$ref").stringValue()),
                () -> assertEquals(4, media.path("examples").size()),
                () -> assertTrue(document.path("paths").path("/api/v1/groups").path("get")
                        .path("security").get(0).path("bearerAuth").isArray()),
                () -> assertEquals("#/components/schemas/Feed", schemas.path("ApiResponseFeed")
                        .path("properties").path("result").path("$ref").stringValue()),
                () -> assertEquals("#/components/schemas/Item", schemas.path("Feed").path("properties")
                        .path("items").path("items").path("$ref").stringValue()));
    }

    @Test
    @DisplayName("Swagger의 GROUP·AD 구분값을 실제 카드 스키마로 매핑하고 유형을 필수 상수로 명세한다")
    void docs_cardDiscriminator_mapsActualTypeValues() throws Exception {
        // given
        var docs = get("/v3/api-docs");
        // when
        JsonNode schemas = mapper.readTree(mvc.perform(docs).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("components").path("schemas");
        // then
        JsonNode item = schemas.path("Item");
        assertEquals(2, item.path("oneOf").size());
        assertEquals("type", item.path("discriminator").path("propertyName").stringValue());
        for (var entry : Map.of("GROUP", "GroupCard", "AD", "AdCard").entrySet()) {
            JsonNode card = schemas.path(entry.getValue());
            assertAll(() -> assertEquals("#/components/schemas/" + entry.getValue(), item.path("discriminator")
                            .path("mapping").path(entry.getKey()).stringValue()),
                    () -> assertEquals(List.of(entry.getKey()), mapper.convertValue(
                            card.path("properties").path("type").path("enum"), List.class)),
                    () -> assertTrue(mapper.convertValue(card.path("required"), List.class).contains("type")));
        }
    }

    @Test
    @DisplayName("배너는 인증 없이 SVG 이미지로 조회할 수 있다")
    void assets_anonymousRequest_returnsSvg() throws Exception {
        // given
        var request = get("/feed-ads/ad-a.svg");
        // when
        var result = mvc.perform(request);
        // then
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"));
    }

    @Test
    @DisplayName("로컬 더미 SQL 자체로도 19개 모임과 A·B 광고를 인증 API에서 재현한다")
    void getFeed_localDummySql_reproducesDemo() throws Exception {
        // given
        db.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.ClassPathResource("db/dummy/V20261110164908__group_recommendation_demo.sql"));
            return null;
        });
        // when
        String cursor = null;
        List<Long> actual = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            var request = get("/api/v1/groups").cookie(cookie(380001)).param("sort", "RECOMMENDED").param("query", "추천38");
            if (cursor != null) request.param("cursor", cursor);
            JsonNode result = mapper.readTree(mvc.perform(request).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString()).path("result");
            // then
            assertEquals(19, result.path("totalGroupCount").intValue());
            assertEquals(page < 2 ? 10 : 1, result.path("items").size());
            if (page < 2) assertPage(result, 8, 1, 380001 + page);
            else assertFalse(result.path("hasNext").booleanValue());
            actual.addAll(ids(result));
            cursor = result.path("nextCursor").isNull() ? null : result.path("nextCursor").stringValue();
        }
        assertNull(cursor);
        assertEquals(19, actual.size());
        assertEquals(java.util.stream.LongStream.rangeClosed(380101, 380119).boxed().collect(java.util.stream.Collectors.toSet()),
                new HashSet<>(actual));
    }

    private void seed(int normal, int low) {
        for (int i = 0; i < normal; i++) expected.add(group(true));
        for (int i = 0; i < low; i++) expected.add(group(false));
    }

    private long group(boolean normal) {
        Group g = Group.builder().category(normal ? running : study).name("feed38 " + UUID.randomUUID())
                .description("피드 검증 소개").regionCode(normal ? "11680" : "11110").placeName("장소").address("주소")
                .maxMembers(20).joinPolicy(GroupJoinPolicy.APPROVAL).build();
        em.persist(g);
        em.persist(GroupMember.builder().group(g).member(owner).role(GroupMemberRole.OWNER).status(GroupMemberStatus.ACTIVE).joinedAt(NOW.minusDays(2)).build());
        em.flush();
        db.update("UPDATE member_group SET created_at=? WHERE id=?", NOW.minusDays(1), g.getId());
        return g.getId();
    }

    private Member member() {
        Member m = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString())
                .nickname("feed38-" + UUID.randomUUID().toString().substring(0, 8)).build();
        em.persist(m);
        return m;
    }

    private Category category(String code) {
        return em.createQuery("select c from Category c where c.code=:code", Category.class).setParameter("code", code).getSingleResult();
    }

    private long ad(boolean active, LocalDateTime starts, LocalDateTime ends) {
        db.update("""
                INSERT INTO feed_advertisement(advertiser_name,title,image_url,landing_url,active,starts_at,ends_at)
                VALUES ('시연 광고주','시연 배너','/feed-ads/ad-a.svg','https://example.com',?,?,?)
                """, active, starts, ends);
        return db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void schedule(long group, LocalDateTime end, String status, boolean deleted) {
        db.update("""
                INSERT INTO schedule(group_id,title,description,starts_at,ends_at,location_name,location_address,status,deleted_at)
                VALUES (?,'피드 일정','설명',?,?,'장소','주소',?,?)
                """, group, end.minusHours(1), end, status, deleted ? NOW : null);
    }

    private JsonNode feed(String cursor) throws Exception { return request(cursor, "feed38", null, null); }

    private JsonNode request(String cursor, String query, String categories, String region) throws Exception {
        var request = get("/api/v1/groups").cookie(cookie(viewer.getId())).param("sort", "RECOMMENDED").param("query", query);
        if (cursor != null) request.param("cursor", cursor);
        if (categories != null) request.param("categoryCodes", categories);
        if (region != null) request.param("regionCode", region);
        String response = mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GROUP200_10"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("result");
    }

    private Cookie cookie(long memberId) {
        return new Cookie("moeasy_access_token", jwt.issueAccessToken(memberId).value());
    }

    private List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.path("items").forEach(item -> { if (item.has("groupId")) ids.add(item.path("groupId").longValue()); });
        return ids;
    }

    private void assertPage(JsonNode page, int normals, int discoveries, long ad) {
        assertEquals(normals + discoveries + 1, page.path("items").size());
        for (int i = 0; i < normals; i++) assertEquals("RUNNING", page.path("items").get(i).path("categoryCode").stringValue());
        assertEquals("STUDY", page.path("items").get(normals).path("categoryCode").stringValue());
        assertEquals("AD", page.path("items").get(9).path("type").stringValue());
        assertEquals(ad, page.path("items").get(9).path("adId").longValue());
        assertTrue(page.path("hasNext").booleanValue());
    }
}
