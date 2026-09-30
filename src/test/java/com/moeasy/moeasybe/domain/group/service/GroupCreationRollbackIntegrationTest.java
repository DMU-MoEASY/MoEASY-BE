package com.moeasy.moeasybe.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.repository.GroupMemberRepository;
import com.moeasy.moeasybe.domain.group.service.command.GroupCommandService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class GroupCreationRollbackIntegrationTest {
    @Autowired private GroupCommandService commandService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private GroupMemberRepository groupMemberRepository;
    @MockitoBean private GroupCoverImageService coverImageService;

    @Test
    @DisplayName("OWNER 멤버십 저장 실패 시 Group 생성도 롤백된다")
    void createGroup_ownerSaveFailure_rollsBackGroup() {
        // given
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        jdbcTemplate.update("INSERT INTO category (code, name) VALUES (?, ?)", suffix, suffix);
        jdbcTemplate.update("INSERT INTO member (social_id, social_type, nickname) VALUES (?, 'KAKAO', ?)",
                suffix, suffix);
        Long categoryId = jdbcTemplate.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, suffix);
        Long memberId = jdbcTemplate.queryForObject("SELECT id FROM member WHERE social_id = ?", Long.class, suffix);
        GroupReqDTO.Create request = new GroupReqDTO.Create("롤백 대상", "설명", categoryId,
                "11680", "장소", "주소", null, null, 10, GroupJoinPolicy.APPROVAL, null);
        when(groupMemberRepository.save(any(GroupMember.class)))
                .thenThrow(new IllegalStateException("owner save failed"));

        try {
            // when
            assertThrows(IllegalStateException.class, () -> commandService.createGroup(request, memberId));

            // then
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member_group WHERE name = '롤백 대상'",
                    Integer.class)).isZero();
        } finally {
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
            jdbcTemplate.update("DELETE FROM category WHERE id = ?", categoryId);
        }
    }
}
