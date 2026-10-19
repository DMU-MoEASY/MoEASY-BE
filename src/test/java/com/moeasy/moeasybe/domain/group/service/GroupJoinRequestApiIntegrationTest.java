package com.moeasy.moeasybe.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moeasy.moeasybe.domain.auth.config.AuthCookieNames;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class GroupJoinRequestApiIntegrationTest extends JoinRequestIntegrationSupport {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokens;
    private static final String CSRF = "join-request-test-csrf";

    @ParameterizedTest
    @CsvSource({"POST,''", "DELETE,/me", "GET,''", "GET,/me", "POST,/1/approve", "POST,/1/reject"})
    @DisplayName("모든 가입 신청 API는 인증이 필요하다")
    void endpoint_anonymous_unauthorized(String method, String suffix) throws Exception {
        // given
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .request(HttpMethod.valueOf(method), path(1L) + suffix)
                .cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF)
                .contentType(MediaType.APPLICATION_JSON).content("{}");

        // when
        var result = mvc.perform(request);

        // then
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401_1"));
    }

    @Test
    @DisplayName("잘못된 JWT는 401이며 인증된 쓰기도 CSRF 토큰이 필요하다")
    void endpoint_invalidJwtOrMissingCsrf_rejected() throws Exception {
        // given
        Long applicant = member();

        // when
        var invalidJwt = mvc.perform(get(path(1L) + "/me").cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, "invalid")));
        var missingCsrf = mvc.perform(post(path(1L)).cookie(token(applicant))
                .contentType(MediaType.APPLICATION_JSON).content("{\"introduction\":\"소개\"}"));

        // then
        invalidJwt.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401_1"));
        missingCsrf.andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증된 실제 API로 신청·목록·거절·재신청·취소·승인 전체 흐름을 수행한다")
    void endpoints_authenticatedLifecycle_preservesContract() throws Exception {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 3);

        // when: apply then reject
        var created = mvc.perform(auth(post(path(group)), applicant)
                        .content("{\"introduction\":\"함께하고 싶어요\",\"memberId\":" + owner + "}"))
                .andExpect(status().isCreated()).andReturn();
        Long first = JsonPath.parse(created.getResponse().getContentAsString()).read("$.result.requestId", Long.class);
        mvc.perform(auth(get(path(group)), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.items[0].memberId").value(applicant))
                .andExpect(jsonPath("$.result.items[0].requestId").value(first))
                .andExpect(jsonPath("$.result.items[0].nickname").exists());
        mvc.perform(auth(post(path(group) + "/" + first + "/reject"), owner).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(auth(get(path(group) + "/me"), applicant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("REJECTED"))
                .andExpect(jsonPath("$.result.processedAt").exists());

        // when: reapply then cancel, reapply then approve
        mvc.perform(auth(post(path(group)), applicant).content("{\"introduction\":\"재신청\"}"))
                .andExpect(status().isCreated());
        mvc.perform(auth(delete(path(group) + "/me"), applicant)).andExpect(status().isOk());
        Long third = apply(group, applicant);
        mvc.perform(auth(post(path(group) + "/" + third + "/approve"), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("GROUP200_7"));

        // then
        assertThat(requestStatus(first)).isEqualTo("REJECTED");
        assertThat(requestStatus(third)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_member WHERE group_id = ? AND member_id = ?",
                Integer.class, group, applicant)).isEqualTo(1);
    }

    @Test
    @DisplayName("실제 보안 필터 환경에서도 비숫자 페이지 입력은 공통 400이다")
    void getPending_invalidNumber_commonBadRequest() throws Exception {
        // given
        Long owner = member();
        Long group = group(owner, 3);

        // when
        var result = mvc.perform(auth(get(path(group)).param("page", "abc"), owner));

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALID400_1"));
    }

    @ParameterizedTest
    @CsvSource({"107374182,20,200", "107374183,20,400", "2147483647,1,200",
            "1073741824,2,400", "2147483647,100,400"})
    @DisplayName("페이지 offset의 int 상한 이하는 조회하고 초과하면 공통 400을 반환한다")
    void getPending_offsetBoundary_returnsSupportedStatus(int page, int size, int expectedStatus) throws Exception {
        // given
        Long owner = member();
        Long group = group(owner, 3);

        // when
        var result = mvc.perform(auth(get(path(group)).param("page", Integer.toString(page))
                .param("size", Integer.toString(size)), owner));

        // then
        result.andExpect(status().is(expectedStatus));
        if (expectedStatus == 200) {
            result.andExpect(jsonPath("$.code").value("GROUP200_5"))
                    .andExpect(jsonPath("$.result.page").value(page))
                    .andExpect(jsonPath("$.result.size").value(size))
                    .andExpect(jsonPath("$.result.items").isEmpty())
                    .andExpect(jsonPath("$.result.hasNext").value(false));
        } else {
            result.andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.code").value("COMMON400_1"));
        }
    }

    @Test
    @DisplayName("Swagger에 여섯 가입 관리 API가 노출된다")
    void apiDocs_joinRequests_containsAllEndpoints() throws Exception {
        // when
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();

        // then
        var doc = JsonPath.parse(result.getResponse().getContentAsString());
        String base = "/api/v1/groups/{groupId}/join-requests";
        assertThat(doc.read("$.paths['" + base + "'].post.responses['201'].description", String.class)).isNotBlank();
        assertThat(doc.read("$.paths['" + base + "'].get.responses['403'].description", String.class)).isNotBlank();
        assertThat(doc.read("$.paths['" + base + "/me'].get.responses['200'].description", String.class)).isNotBlank();
        assertThat(doc.read("$.paths['" + base + "/me'].delete.responses['200'].description", String.class)).isNotBlank();
        assertThat(doc.read("$.paths['" + base + "/{requestId}/approve'].post.responses['409'].description", String.class)).isNotBlank();
        assertThat(doc.read("$.paths['" + base + "/{requestId}/reject'].post.responses['200'].description", String.class)).isNotBlank();
    }

    private String path(Long group) {
        return "/api/v1/groups/" + group + "/join-requests";
    }

    private Cookie token(Long member) {
        return new Cookie(AuthCookieNames.ACCESS_TOKEN, tokens.issueAccessToken(member).value());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, Long member) {
        return request.cookie(token(member), new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF)
                .contentType(MediaType.APPLICATION_JSON);
    }
}
