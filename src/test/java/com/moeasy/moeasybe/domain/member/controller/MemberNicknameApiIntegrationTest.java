package com.moeasy.moeasybe.domain.member.controller;

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
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class MemberNicknameApiIntegrationTest {

    private static final String PATH = "/api/v1/members/nicknames/availability";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @ValueSource(strings = {"새별", "열두글자닉네임확인테스트"})
    @DisplayName("길이 경계의 사용 가능한 닉네임은 CSRF 없이 조회한다")
    void getNicknameAvailability_unusedNickname_returnsTrue(String nickname) throws Exception {
        // given
        Cookie cookie = accessCookie();
        // when
        ResultActions result = mockMvc.perform(get(PATH).param("nickname", nickname).cookie(cookie));
        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("MEMBER200_1"))
                .andExpect(jsonPath("$.result.available").value(true));
    }

    @Test
    @DisplayName("중복 닉네임은 오류 대신 사용 불가를 반환한다")
    void getNicknameAvailability_duplicateNickname_returnsFalse() throws Exception {
        // given
        Cookie cookie = accessCookie();
        memberRepository.saveAndFlush(member("기존회원"));
        // when
        ResultActions result = mockMvc.perform(get(PATH).param("nickname", "기존회원").cookie(cookie));
        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.result.available").value(false));
    }

    @Test
    @DisplayName("삭제된 회원의 닉네임도 DB 유니크 제약에 맞춰 사용 불가를 반환한다")
    void getNicknameAvailability_deletedMemberNickname_returnsFalse() throws Exception {
        // given
        Cookie cookie = accessCookie();
        Member member = memberRepository.saveAndFlush(member("탈퇴회원"));
        jdbcTemplate.update("UPDATE member SET deleted_at = '2026-09-30 12:00:00' WHERE id = ?", member.getId());
        // when
        ResultActions result = mockMvc.perform(get(PATH).param("nickname", "탈퇴회원").cookie(cookie));
        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.result.available").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "가", "열세글자닉네임확인테스트임"})
    @DisplayName("빈 값, 공백, 길이 범위 밖 닉네임은 400으로 거절한다")
    void getNicknameAvailability_invalidNickname_returnsBadRequest(String nickname) throws Exception {
        // given
        Cookie cookie = accessCookie();
        // when
        ResultActions result = mockMvc.perform(get(PATH).param("nickname", nickname).cookie(cookie));
        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID400_1"))
                .andExpect(jsonPath("$.result.nickname").isArray());
    }

    @Test
    @DisplayName("닉네임 파라미터 누락은 400으로 거절한다")
    void getNicknameAvailability_missingNickname_returnsBadRequest() throws Exception {
        // given
        Cookie cookie = accessCookie();
        // when
        ResultActions result = mockMvc.perform(get(PATH).cookie(cookie));
        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALID400_1"));
    }

    @Test
    @DisplayName("익명 요청은 401로 거절한다")
    void getNicknameAvailability_anonymous_returnsUnauthorized() throws Exception {
        // given
        var request = get(PATH).param("nickname", "새별");
        // when
        ResultActions result = mockMvc.perform(request);
        // then
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401_1"));
    }

    @Test
    @DisplayName("유효하지 않은 인증 쿠키는 401로 거절한다")
    void getNicknameAvailability_invalidCookie_returnsUnauthorized() throws Exception {
        // given
        Cookie cookie = new Cookie(AuthCookieNames.ACCESS_TOKEN, "invalid-token");
        // when
        ResultActions result = mockMvc.perform(get(PATH).param("nickname", "새별").cookie(cookie));
        // then
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401_1"));
    }

    @Test
    @DisplayName("Swagger는 nickname 쿼리 파라미터와 available 응답을 설명한다")
    void apiDocs_nicknameAvailability_describesQueryAndResponse() throws Exception {
        // given
        var request = get("/v3/api-docs");
        // when
        var response = mockMvc.perform(request).andReturn().getResponse();
        var document = JsonPath.parse(response.getContentAsString());
        String operation = "$.paths['" + PATH + "'].get";
        String responseRef = document.read(operation + ".responses['200'].content['application/json'].schema['$ref']");
        String responseSchema = responseRef.substring(responseRef.lastIndexOf('/') + 1);
        String resultRef = document.read("$.components.schemas['" + responseSchema + "'].properties.result['$ref']");
        String resultSchema = resultRef.substring(resultRef.lastIndexOf('/') + 1);
        // then
        assertAll(
                () -> assertEquals(200, response.getStatus()),
                () -> assertEquals(java.util.Set.of("application/json"), document.read(
                        "$.paths['" + PATH + "'].get.responses['200'].content", java.util.Map.class).keySet()),
                () -> assertEquals("nickname", document.read(operation + ".parameters[0].name", String.class)),
                () -> assertEquals("query", document.read(operation + ".parameters[0].in", String.class)),
                () -> assertEquals("boolean", document.read("$.components.schemas['" + resultSchema
                        + "'].properties.available.type", String.class))
        );
    }

    private Cookie accessCookie() {
        Member member = memberRepository.saveAndFlush(member(null));
        return new Cookie(AuthCookieNames.ACCESS_TOKEN, jwtTokenProvider.issueAccessToken(member.getId()).value());
    }

    private Member member(String nickname) {
        return Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString())
                .nickname(nickname).build();
    }
}
