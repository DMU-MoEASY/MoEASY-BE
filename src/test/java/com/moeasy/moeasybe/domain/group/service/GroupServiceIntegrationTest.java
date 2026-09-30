package com.moeasy.moeasybe.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.ObjectMapper;
import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupResDTO;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.service.command.GroupCommandService;
import com.moeasy.moeasybe.domain.group.service.query.GroupQueryService;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class GroupServiceIntegrationTest {
    @Autowired private GroupCommandService commandService;
    @Autowired private GroupQueryService queryService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private GroupCoverImageService coverImageService;

    @Test
    @DisplayName("그룹 생성은 활성 OWNER 멤버십을 함께 저장한다")
    void createGroup_validRequest_persistsGroupAndOwner() {
        // given
        Long memberId = insertMember();
        Long categoryId = insertCategory();

        // when
        GroupResDTO.Created created = commandService.createGroup(createRequest(categoryId), memberId);
        entityManager.flush();
        GroupResDTO.Created secondCreated = commandService.createGroup(createRequest(categoryId), memberId);
        entityManager.flush();

        // then
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member_group WHERE id = ?", Integer.class,
                created.groupId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM group_member WHERE group_id = ? AND member_id = ? AND role = 'OWNER' AND status = 'ACTIVE'",
                Integer.class, created.groupId(), memberId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member_group WHERE id = ?", Integer.class,
                secondCreated.groupId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM group_member WHERE group_id = ? AND member_id = ? AND role = 'OWNER' AND status = 'ACTIVE'",
                Integer.class, secondCreated.groupId(), memberId)).isEqualTo(1);
        assertThat(queryService.getGroup(created.groupId(), memberId).groupId()).isEqualTo(created.groupId());
    }

    @Test
    @DisplayName("PATCH는 생략 필드를 보존하고 명시적 null 커버 키를 제거한다")
    void updateGroup_partialRequest_changesOnlyPresentFields() throws Exception {
        // given
        Long memberId = insertMember();
        Long groupId = commandService.createGroup(createRequest(insertCategory()), memberId).groupId();
        GroupReqDTO.Update request = objectMapper.readValue(
                "{\"name\":\"새 모임\",\"coverImageKey\":null,\"latitude\":null,\"longitude\":null}",
                GroupReqDTO.Update.class);

        // when
        GroupResDTO.Detail detail = commandService.updateGroup(groupId, request, memberId);
        entityManager.flush();

        // then
        assertThat(detail.name()).isEqualTo("새 모임");
        assertThat(detail.description()).isEqualTo("설명");
        assertThat(detail.latitude()).isNull();
        assertThat(detail.longitude()).isNull();
        assertThat(detail.coverImageUrl()).isNull();
    }

    @Test
    @DisplayName("좌표 한쪽만 전달하면 Group이 바뀌지 않는다")
    void updateGroup_oneCoordinate_rejectsWithoutChange() throws Exception {
        // given
        Long memberId = insertMember();
        Long groupId = commandService.createGroup(createRequest(insertCategory()), memberId).groupId();
        GroupReqDTO.Update request = objectMapper.readValue("{\"latitude\":37.1}", GroupReqDTO.Update.class);

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> commandService.updateGroup(groupId, request, memberId));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("COMMON400_1");
        assertThat(queryService.getGroup(groupId, memberId).name()).isEqualTo("원래 모임");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"latitude\":null,\"longitude\":37.1}",
            "{\"maxMembers\":50}"})
    @DisplayName("빈 PATCH, 필수값 null, 좌표 불일치, 수정 불가 필드는 거부한다")
    void updateGroup_invalidPatch_rejectsWithoutChange(String json) throws Exception {
        // given
        Long memberId = insertMember();
        Long groupId = commandService.createGroup(createRequest(insertCategory()), memberId).groupId();
        GroupReqDTO.Update request = objectMapper.readValue(json, GroupReqDTO.Update.class);

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> commandService.updateGroup(groupId, request, memberId));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("COMMON400_1");
        assertThat(queryService.getGroup(groupId, memberId).name()).isEqualTo("원래 모임");
    }

    @Test
    @DisplayName("좌표 두 값을 함께 보내면 함께 변경한다")
    void updateGroup_coordinatePair_changesBothCoordinates() throws Exception {
        // given
        Long memberId = insertMember();
        Long groupId = commandService.createGroup(createRequest(insertCategory()), memberId).groupId();
        GroupReqDTO.Update request = objectMapper.readValue(
                "{\"latitude\":37.5665000,\"longitude\":126.9780000}", GroupReqDTO.Update.class);

        // when
        GroupResDTO.Detail detail = commandService.updateGroup(groupId, request, memberId);

        // then
        assertThat(detail.latitude()).isEqualByComparingTo("37.5665000");
        assertThat(detail.longitude()).isEqualByComparingTo("126.9780000");
    }

    @Test
    @DisplayName("인증 회원은 Group 멤버가 아니어도 상세 조회할 수 있다")
    void getGroup_nonMember_returnsDetail() {
        // given
        Long ownerId = insertMember();
        Long otherId = insertMember();
        Long groupId = commandService.createGroup(createRequest(insertCategory()), ownerId).groupId();

        // when
        GroupResDTO.Detail detail = queryService.getGroup(groupId, otherId);

        // then
        assertThat(detail.groupId()).isEqualTo(groupId);
    }

    @Test
    @DisplayName("다른 회원은 Group을 수정할 수 없다")
    void updateGroup_nonOwner_rejectsChange() throws Exception {
        // given
        Long ownerId = insertMember();
        Long otherId = insertMember();
        Long groupId = commandService.createGroup(createRequest(insertCategory()), ownerId).groupId();
        GroupReqDTO.Update request = objectMapper.readValue("{\"name\":\"변경\"}", GroupReqDTO.Update.class);

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> commandService.updateGroup(groupId, request, otherId));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("AUTH403_1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "MEMBER", "LEFT_OWNER"})
    @DisplayName("활성 OWNER가 아닌 멤버십은 수정 권한이 없다")
    void updateGroup_nonActiveOwnerRole_rejectsChange(String membership) throws Exception {
        // given
        Long ownerId = insertMember();
        Long otherId = insertMember();
        Long groupId = commandService.createGroup(createRequest(insertCategory()), ownerId).groupId();
        String role = "LEFT_OWNER".equals(membership) ? "OWNER" : membership;
        String status = "LEFT_OWNER".equals(membership) ? "LEFT" : "ACTIVE";
        jdbcTemplate.update("INSERT INTO group_member (group_id, member_id, role, status) VALUES (?, ?, ?, ?)",
                groupId, otherId, role, status);
        GroupReqDTO.Update request = objectMapper.readValue("{\"name\":\"변경\"}", GroupReqDTO.Update.class);

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> commandService.updateGroup(groupId, request, otherId));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("AUTH403_1");
    }

    @Test
    @DisplayName("OWNER가 아닌 회원은 Group을 폐쇄할 수 없다")
    void closeGroup_nonOwner_rejectsClosure() {
        // given
        Long ownerId = insertMember();
        Long otherId = insertMember();
        Long groupId = commandService.createGroup(createRequest(insertCategory()), ownerId).groupId();

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> commandService.closeGroup(groupId,
                        new GroupReqDTO.Close("종료", "원래 모임", false), otherId));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("AUTH403_1");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM group_deletion_history WHERE group_id = ?",
                Integer.class, groupId)).isZero();
    }

    @Test
    @DisplayName("이름 확인 문구가 다르면 폐쇄와 이력이 남지 않는다")
    void closeGroup_mismatchedConfirmation_preservesGroup() {
        // given
        Long memberId = insertMember();
        Long groupId = commandService.createGroup(createRequest(insertCategory()), memberId).groupId();

        // when
        GroupException exception = assertThrows(GroupException.class,
                () -> commandService.closeGroup(groupId,
                        new GroupReqDTO.Close("종료", "다른 이름", true), memberId));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("GROUP400_1");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM group_deletion_history WHERE group_id = ?",
                Integer.class, groupId)).isZero();
        assertThat(queryService.getGroup(groupId, memberId).status().name()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("폐쇄는 Group 상태와 삭제 이력을 같은 시각에 기록한다")
    void closeGroup_validRequest_softDeletesWithHistory() {
        // given
        Long memberId = insertMember();
        Long groupId = commandService.createGroup(createRequest(insertCategory()), memberId).groupId();

        // when
        commandService.closeGroup(groupId, new GroupReqDTO.Close("운영 종료", "원래 모임", true), memberId);
        entityManager.flush();

        // then
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM member_group WHERE id = ?", String.class,
                groupId)).isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM group_deletion_history WHERE group_id = ? AND deleted_by_member_id = ? AND archive_content = true AND closed_at = (SELECT deleted_at FROM member_group WHERE id = ?)",
                Integer.class, groupId, memberId, groupId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member_group WHERE id = ?", Integer.class,
                groupId)).isEqualTo(1);
        assertThat(assertThrows(GroupException.class, () -> queryService.getGroup(groupId, memberId))
                .getCode().getCode()).isEqualTo("GROUP404_1");
    }

    private Long insertMember() {
        String socialId = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO member (social_id, social_type, nickname) VALUES (?, 'KAKAO', ?)",
                socialId, "user-" + socialId.substring(0, 8));
        return jdbcTemplate.queryForObject("SELECT id FROM member WHERE social_id = ?", Long.class, socialId);
    }

    private Long insertCategory() {
        String code = UUID.randomUUID().toString().substring(0, 12);
        jdbcTemplate.update("INSERT INTO category (code, name) VALUES (?, ?)", code, code);
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }

    private GroupReqDTO.Create createRequest(Long categoryId) {
        return new GroupReqDTO.Create("원래 모임", "설명", categoryId, "11680", "한강공원", "서울",
                null, null, 30, GroupJoinPolicy.APPROVAL, null);
    }
}
