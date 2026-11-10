package com.moeasy.moeasybe.domain.group.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moeasy.moeasybe.domain.group.support.GroupFeedTestDatabase;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ContextConfiguration(initializers = GroupFeedTestDatabase.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GroupFeedDemoIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider jwt;
    @MockitoBean(name = "clock") Clock clock;

    @RepeatedTest(2)
    @DisplayName("격리된 DB에서 시연 SQL을 반복 실행해도 19개 모임과 A·B 광고를 중복 없이 소비한다")
    void getFeed_localDummySql_reproducesDemo() throws Exception {
        // given
        ZoneId kst = ZoneId.of("Asia/Seoul");
        when(clock.withZone(kst)).thenReturn(Clock.fixed(
                LocalDateTime.of(2026, 11, 10, 12, 0).atZone(kst).toInstant(), kst));
        assertAll(() -> assertTrue(db.queryForObject("SELECT DATABASE()", String.class).startsWith("group_feed_test_")),
                () -> assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM member", Long.class)),
                () -> assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM member_group", Long.class)),
                () -> assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM feed_advertisement", Long.class)));
        db.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/dummy/V20261110164908__group_recommendation_demo.sql"));
            return null;
        });

        // when
        List<JsonNode> pages = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 3; page++) {
            var request = get("/api/v1/groups").cookie(new Cookie("moeasy_access_token",
                    jwt.issueAccessToken(380001L).value())).param("sort", "RECOMMENDED").param("query", "추천38");
            if (cursor != null) request.param("cursor", cursor);
            JsonNode result = mapper.readTree(mvc.perform(request).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString()).path("result");
            pages.add(result);
            cursor = result.path("nextCursor").isNull() ? null : result.path("nextCursor").stringValue();
        }

        // then
        List<Long> actual = new ArrayList<>();
        for (int page = 0; page < pages.size(); page++) {
            JsonNode result = pages.get(page);
            assertEquals(19, result.path("totalGroupCount").intValue());
            assertEquals(page < 2 ? 10 : 1, result.path("items").size());
            assertEquals(page < 2, result.path("hasNext").booleanValue());
            if (page < 2) {
                for (int i = 0; i < 9; i++) {
                    assertEquals("GROUP", result.path("items").get(i).path("type").stringValue());
                    assertEquals(i < 8 ? "RUNNING" : "STUDY",
                            result.path("items").get(i).path("categoryCode").stringValue());
                }
                assertEquals("AD", result.path("items").get(9).path("type").stringValue());
                assertEquals(380001 + page, result.path("items").get(9).path("adId").longValue());
            }
            result.path("items").forEach(item -> {
                if (item.has("groupId")) actual.add(item.path("groupId").longValue());
            });
        }
        assertNull(cursor);
        assertEquals(19, actual.size());
        assertEquals(LongStream.rangeClosed(380101, 380119).boxed().collect(Collectors.toSet()), new HashSet<>(actual));
    }
}
