package com.moeasy.moeasybe.domain.group.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moeasy.moeasybe.domain.auth.config.AuthCookieNames;
import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.service.command.GroupCommandService;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GroupControllerSecurityIntegrationTest {

    private static final String GROUPS_PATH = "/api/v1/groups";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GroupCommandService groupCommandService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void access_token_쿠키가_없는_그룹_조회는_401을_반환한다() throws Exception {
        // when
        mockMvc.perform(get(GROUPS_PATH + "/1"))
                // then
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증된_비회원도_csrf_토큰_없이_그룹을_조회할_수_있다() throws Exception {
        // given
        Long ownerId = insertMember();
        Long nonMemberId = insertMember();
        Long groupId = createGroup(ownerId);

        // when
        mockMvc.perform(get(GROUPS_PATH + "/" + groupId)
                        .cookie(accessTokenCookie(nonMemberId)))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.groupId").value(groupId));
    }

    @Test
    void csrf_토큰이_없는_인증된_post_patch_delete는_거부된다() throws Exception {
        // given
        Long memberId = insertMember();
        Long groupId = createGroup(memberId);
        Cookie accessToken = accessTokenCookie(memberId);

        // when / then
        mockMvc.perform(post(GROUPS_PATH)
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(GROUPS_PATH + "/" + groupId)
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CSRF 없이 수정\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(GROUPS_PATH + "/" + groupId)
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeRequestBody("검증 모임")))
                .andExpect(status().isForbidden());

        // then
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM member_group WHERE id = ?", String.class, groupId
        )).isEqualTo("검증 모임");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_deletion_history WHERE group_id = ?", Integer.class, groupId
        )).isZero();
    }

    @Test
    void jwt와_csrf를_보내_그룹을_생성하면_인증_회원을_owner로_저장한다() throws Exception {
        // given
        Long memberId = insertMember();
        CsrfCredentials csrf = requestCsrfCredentials();

        // when
        MvcResult result = mockMvc.perform(post(GROUPS_PATH)
                        .cookie(accessTokenCookie(memberId), csrf.cookie())
                        .header(CSRF_HEADER_NAME, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                // then
                .andExpect(status().isCreated())
                .andReturn();

        Long groupId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("result").path("groupId").asLong();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_member WHERE group_id = ? AND member_id = ? "
                        + "AND role = 'OWNER' AND status = 'ACTIVE'",
                Integer.class, groupId, memberId
        )).isEqualTo(1);
    }

    @Test
    void owner는_csrf가_포함된_patch로_그룹을_수정할_수_있다() throws Exception {
        // given
        Long ownerId = insertMember();
        Long groupId = createGroup(ownerId);
        CsrfCredentials csrf = requestCsrfCredentials();

        // when
        mockMvc.perform(patch(GROUPS_PATH + "/" + groupId)
                        .cookie(accessTokenCookie(ownerId), csrf.cookie())
                        .header(CSRF_HEADER_NAME, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"수정된 모임\"}"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("수정된 모임"));
    }

    @Test
    void 비_owner의_csrf가_포함된_patch는_403을_반환한다() throws Exception {
        // given
        Long ownerId = insertMember();
        Long nonOwnerId = insertMember();
        Long groupId = createGroup(ownerId);
        CsrfCredentials csrf = requestCsrfCredentials();

        // when
        mockMvc.perform(patch(GROUPS_PATH + "/" + groupId)
                        .cookie(accessTokenCookie(nonOwnerId), csrf.cookie())
                        .header(CSRF_HEADER_NAME, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"권한 없는 수정\"}"))
                // then
                .andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM member_group WHERE id = ?", String.class, groupId
        )).isEqualTo("검증 모임");
    }

    @Test
    void owner는_csrf가_포함된_delete로_폐쇄하고_인증_회원이_수행자로_기록된다() throws Exception {
        // given
        Long ownerId = insertMember();
        Long groupId = createGroup(ownerId);
        CsrfCredentials csrf = requestCsrfCredentials();

        // when
        mockMvc.perform(delete(GROUPS_PATH + "/" + groupId)
                        .cookie(accessTokenCookie(ownerId), csrf.cookie())
                        .header(CSRF_HEADER_NAME, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeRequestBody("검증 모임")))
                // then
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_deletion_history WHERE group_id = ? AND deleted_by_member_id = ?",
                Integer.class, groupId, ownerId
        )).isEqualTo(1);
    }

    @Test
    void 비_owner의_csrf가_포함된_delete는_403을_반환한다() throws Exception {
        // given
        Long ownerId = insertMember();
        Long nonOwnerId = insertMember();
        Long groupId = createGroup(ownerId);
        CsrfCredentials csrf = requestCsrfCredentials();

        // when
        mockMvc.perform(delete(GROUPS_PATH + "/" + groupId)
                        .cookie(accessTokenCookie(nonOwnerId), csrf.cookie())
                        .header(CSRF_HEADER_NAME, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closeRequestBody("검증 모임")))
                // then
                .andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_deletion_history WHERE group_id = ?",
                Integer.class, groupId
        )).isZero();
    }

    private Long insertMember() {
        String socialId = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO member (social_id, social_type, nickname) VALUES (?, 'KAKAO', ?)",
                socialId, "member-" + socialId.substring(0, 8));
        return jdbcTemplate.queryForObject("SELECT id FROM member WHERE social_id = ?", Long.class, socialId);
    }

    private Long insertCategory() {
        String code = UUID.randomUUID().toString().substring(0, 12);
        jdbcTemplate.update("INSERT INTO category (code, name) VALUES (?, ?)", code, code);
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }

    private Long createGroup(Long ownerId) {
        GroupReqDTO.Create request = new GroupReqDTO.Create(
                "검증 모임", "설명", insertCategory(), "11680", "한강공원", "서울",
                null, null, 30, GroupJoinPolicy.APPROVAL, null);
        return groupCommandService.createGroup(request, ownerId).groupId();
    }

    private Cookie accessTokenCookie(Long memberId) {
        return new Cookie(AuthCookieNames.ACCESS_TOKEN, jwtTokenProvider.issueAccessToken(memberId).value());
    }

    private CsrfCredentials requestCsrfCredentials() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("result").path("token").asText();
        Cookie cookie = result.getResponse().getCookie(CSRF_COOKIE_NAME);
        assertThat(token).isNotBlank();
        assertThat(cookie).isNotNull();
        return new CsrfCredentials(cookie, token);
    }

    private String createRequestBody() {
        return """
                {
                  "name": "검증 모임",
                  "description": "설명",
                  "categoryId": %d,
                  "regionCode": "11680",
                  "placeName": "한강공원",
                  "address": "서울",
                  "maxMembers": 30,
                  "joinPolicy": "APPROVAL"
                }
                """.formatted(insertCategory());
    }

    private String closeRequestBody(String confirmationText) {
        return """
                {
                  "reason": "테스트 종료",
                  "confirmationText": "%s",
                  "archiveContent": false
                }
                """.formatted(confirmationText);
    }

    private record CsrfCredentials(Cookie cookie, String token) {
    }
}
