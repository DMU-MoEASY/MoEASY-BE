package com.moeasy.moeasybe.domain.region.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moeasy.moeasybe.domain.auth.config.AuthCookieNames;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegionApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("온보딩 전 회원도 CSRF 헤더 없이 전체 지역 목록을 조회한다")
    void getRegions_authenticatedMember_returnsGroupedReferenceData() throws Exception {
        // given
        Cookie accessCookie = accessCookie();

        // when
        ResultActions result = mockMvc.perform(get("/api/v1/regions").cookie(accessCookie));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("REGION200_1"))
                .andExpect(jsonPath("$.message").value("지역 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.result", hasSize(16)))
                .andExpect(jsonPath("$.result[*].regions[*].code", hasSize(230)))
                .andExpect(jsonPath("$.result[?(@.provinceName == '서울특별시')].regions[*].code", hasItem("11680")))
                .andExpect(jsonPath("$.result[?(@.provinceName == '서울특별시')].regions[*].name", hasItem("강남구")))
                .andExpect(jsonPath("$.result[?(@.provinceName == '세종특별자치시')].regions[*].code", hasItem("36110")));
    }

    @Test
    @DisplayName("소프트 삭제된 지역은 목록에서 제외한다")
    void getRegions_deletedRegion_excludesDeletedRegion() throws Exception {
        // given
        Cookie accessCookie = accessCookie();
        jdbcTemplate.update("UPDATE region SET deleted_at = ? WHERE code = '11740'",
                LocalDateTime.of(2026, 9, 30, 12, 0));

        // when
        ResultActions result = mockMvc.perform(get("/api/v1/regions").cookie(accessCookie));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.result[*].regions[*].code", hasItem("11680")))
                .andExpect(jsonPath("$.result[*].regions[*].code", not(hasItem("11740"))))
                .andExpect(jsonPath("$.result[*].regions[*].code", hasItem("41280")));
    }

    @Test
    @DisplayName("인증 쿠키가 없는 지역 목록 요청은 401로 거절한다")
    void getRegions_anonymous_returnsUnauthorized() throws Exception {
        // given
        var request = get("/api/v1/regions");

        // when
        ResultActions result = mockMvc.perform(request);

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH401_1"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.result").value(nullValue()));
    }

    @Test
    @DisplayName("유효하지 않은 인증 쿠키의 지역 목록 요청은 401로 거절한다")
    void getRegions_invalidAccessCookie_returnsUnauthorized() throws Exception {
        // given
        Cookie invalidCookie = new Cookie(AuthCookieNames.ACCESS_TOKEN, "invalid-token");

        // when
        ResultActions result = mockMvc.perform(get("/api/v1/regions").cookie(invalidCookie));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_1"));
    }

    @Test
    @DisplayName("Swagger 지역 조회 성공 스키마는 공통 응답 안의 시도별 배열을 설명한다")
    void apiDocs_regions_describesGroupedResponseSchema() throws Exception {
        // given
        var request = get("/v3/api-docs");

        // when
        var response = mockMvc.perform(request).andReturn().getResponse();
        var document = JsonPath.parse(response.getContentAsString());
        String responseRef = document.read(
                "$.paths['/api/v1/regions'].get.responses['200'].content['application/json'].schema['$ref']");
        String responseSchema = responseRef.substring(responseRef.lastIndexOf('/') + 1);
        String provinceRef = document.read(
                "$.components.schemas['" + responseSchema + "'].properties.result.items['$ref']");
        String provinceSchema = provinceRef.substring(provinceRef.lastIndexOf('/') + 1);

        // then
        assertAll(
                () -> assertEquals(200, response.getStatus()),
                () -> assertEquals("array", document.read(
                        "$.components.schemas['" + responseSchema + "'].properties.result.type", String.class)),
                () -> assertEquals("array", document.read(
                        "$.components.schemas['" + provinceSchema + "'].properties.regions.type", String.class)),
                () -> assertEquals("string", document.read(
                        "$.components.schemas['" + provinceSchema + "'].properties.provinceName.type", String.class))
        );
    }

    private Cookie accessCookie() {
        Member member = memberRepository.save(Member.builder()
                .socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).build());
        return new Cookie(AuthCookieNames.ACCESS_TOKEN, jwtTokenProvider.issueAccessToken(member.getId()).value());
    }
}
