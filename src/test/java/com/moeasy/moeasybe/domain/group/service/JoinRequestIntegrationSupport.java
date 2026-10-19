package com.moeasy.moeasybe.domain.group.service;

import com.moeasy.moeasybe.domain.group.dto.request.GroupJoinRequestReqDTO;
import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.service.command.GroupCommandService;
import com.moeasy.moeasybe.domain.group.service.command.GroupJoinRequestCommandService;
import com.moeasy.moeasybe.domain.group.service.query.GroupJoinRequestQueryService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

abstract class JoinRequestIntegrationSupport {
    @Autowired protected GroupJoinRequestCommandService commands;
    @Autowired protected GroupJoinRequestQueryService queries;
    @Autowired protected GroupCommandService groups;
    @Autowired protected JdbcTemplate jdbc;
    private final List<Long> groupIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();

    protected Long member() {
        String suffix = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO member (social_id, social_type, nickname) VALUES (?, 'KAKAO', ?)", suffix, suffix);
        Long id = jdbc.queryForObject("SELECT id FROM member WHERE social_id = ?", Long.class, suffix);
        memberIds.add(id);
        return id;
    }

    protected Long group(Long owner, int capacity) {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO category (code, name) VALUES (?, ?)", suffix, suffix);
        Long category = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, suffix);
        categoryIds.add(category);
        Long id = groups.createGroup(new GroupReqDTO.Create("가입 테스트", "설명", category,
                "11680", "장소", "주소", null, null, capacity, GroupJoinPolicy.APPROVAL, null), owner).groupId();
        groupIds.add(id);
        return id;
    }

    protected Long membership(Long group, Long member, String role, String status, boolean deleted) {
        jdbc.update("INSERT INTO group_member (group_id, member_id, role, status, deleted_at) VALUES (?, ?, ?, ?, "
                + (deleted ? "CURRENT_TIMESTAMP(6)" : "NULL") + ")", group, member, role, status);
        return jdbc.queryForObject("SELECT id FROM group_member WHERE group_id = ? AND member_id = ?",
                Long.class, group, member);
    }

    protected Long apply(Long group, Long member) {
        return commands.create(group, new GroupJoinRequestReqDTO.Create("참여하고 싶습니다."), member).requestId();
    }

    protected String requestStatus(Long request) {
        return jdbc.queryForObject("SELECT status FROM group_join_request WHERE id = ?", String.class, request);
    }

    @AfterEach
    void cleanJoinFixtures() {
        for (Long group : groupIds) {
            jdbc.update("DELETE FROM group_join_request WHERE group_id = ?", group);
            jdbc.update("DELETE FROM group_deletion_history WHERE group_id = ?", group);
            jdbc.update("DELETE FROM group_member WHERE group_id = ?", group);
            jdbc.update("DELETE FROM member_group WHERE id = ?", group);
        }
        for (Long member : memberIds) jdbc.update("DELETE FROM member WHERE id = ?", member);
        for (Long category : categoryIds) jdbc.update("DELETE FROM category WHERE id = ?", category);
    }
}
