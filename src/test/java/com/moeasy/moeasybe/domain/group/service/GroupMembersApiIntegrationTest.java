package com.moeasy.moeasybe.domain.group.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.moeasy.moeasybe.domain.auth.config.AuthCookieNames;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class GroupMembersApiIntegrationTest extends JoinRequestIntegrationSupport {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokens;

    @Test
    @DisplayName("일반 회원도 전체 활성 명단을 ID 순서로 조회하고 비활성·삭제 계정은 제외한다")
    void getMembers_activeMember_returnsFilteredOrderedMembers() throws Exception {
        // given
        Long owner = member();
        Long group = group(owner, 20);
        Long ownerMembership = jdbc.queryForObject("SELECT id FROM group_member WHERE group_id=? AND member_id=?", Long.class, group, owner);
        Long manager = membership(group, member(), "MANAGER", "ACTIVE", false);
        Long viewer = member();
        Long viewerMembership = membership(group, viewer, "MEMBER", "ACTIVE", false);
        membership(group, member(), "MEMBER", "LEFT", false);
        membership(group, member(), "MEMBER", "KICKED", false);
        membership(group, member(), "MEMBER", "ACTIVE", true);
        Long deletedAccount = member();
        membership(group, deletedAccount, "MEMBER", "ACTIVE", false);
        jdbc.update("UPDATE member SET deleted_at=CURRENT_TIMESTAMP(6) WHERE id=?", deletedAccount);

        // when
        var result = mvc.perform(get(path(group)).cookie(token(viewer)));

        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("GROUP200_9"))
                .andExpect(jsonPath("$.result.members.length()").value(3))
                .andExpect(jsonPath("$.result.members[0].memberId").value(owner))
                .andExpect(jsonPath("$.result.members[0].groupMemberId").value(ownerMembership))
                .andExpect(jsonPath("$.result.members[0].role").value("OWNER"))
                .andExpect(jsonPath("$.result.members[1].role").value("MANAGER"))
                .andExpect(jsonPath("$.result.members[2].role").value("MEMBER"))
                .andExpect(jsonPath("$.result.members[2].nickname").isNotEmpty())
                .andExpect(jsonPath("$.result.members[1].groupMemberId").value(manager))
                .andExpect(jsonPath("$.result.members[2].groupMemberId").value(viewerMembership));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OUTSIDER", "LEFT", "DELETED"})
    @DisplayName("비회원·탈퇴 회원·삭제 계정의 조회는 거부한다")
    void getMembers_inactiveRequester_forbidden(String kind) throws Exception {
        // given
        Long group = group(member(), 5);
        Long actor = member();
        if (!kind.equals("OUTSIDER")) membership(group, actor, "MEMBER",
                kind.equals("LEFT") ? "LEFT" : "ACTIVE", false);
        if (kind.equals("DELETED")) jdbc.update("UPDATE member SET deleted_at=CURRENT_TIMESTAMP(6) WHERE id=?", actor);
        // when
        var result = mvc.perform(get(path(group)).cookie(token(actor)));
        // then
        result.andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLOSED", "DELETED", "MISSING", "DELETION_HISTORY"})
    @DisplayName("폐쇄·삭제·없는 모임과 삭제 이력이 있는 모임은 404이다")
    void getMembers_unavailableGroup_notFound(String kind) throws Exception {
        // given
        Long owner = member();
        Long group = group(owner, 5);
        if (kind.equals("CLOSED")) jdbc.update("UPDATE member_group SET status='CLOSED' WHERE id=?", group);
        if (kind.equals("DELETED")) jdbc.update("UPDATE member_group SET deleted_at=CURRENT_TIMESTAMP(6) WHERE id=?", group);
        if (kind.equals("DELETION_HISTORY")) {
            jdbc.update("INSERT INTO group_deletion_history "
                    + "(group_id, deleted_by_member_id, reason, confirmation_text, archive_content, closed_at) "
                    + "VALUES (?, ?, '삭제 이력 검증', '가입 테스트', false, CURRENT_TIMESTAMP(6))", group, owner);
        }
        // when
        var result = mvc.perform(get(path(kind.equals("MISSING") ? Long.MAX_VALUE : group)).cookie(token(owner)));
        // then
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GROUP404_1"));
    }

    @Test
    @DisplayName("익명 요청은 401이며 음수 모임 ID는 400이다")
    void getMembers_invalidHttpInput_rejected() throws Exception {
        // given
        Long actor = member();
        // when
        var anonymous = mvc.perform(get(path(1L)));
        var invalid = mvc.perform(get(path(-1L)).cookie(token(actor)));
        // then
        anonymous.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH401_1"));
        invalid.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALID400_1"));
    }

    private Cookie token(Long member) {
        return new Cookie(AuthCookieNames.ACCESS_TOKEN, tokens.issueAccessToken(member).value());
    }

    private String path(Long group) { return "/api/v1/groups/" + group + "/members"; }
}
