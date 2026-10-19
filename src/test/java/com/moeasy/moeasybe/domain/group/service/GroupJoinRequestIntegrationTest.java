package com.moeasy.moeasybe.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moeasy.moeasybe.domain.group.dto.request.GroupJoinRequestReqDTO;
import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GroupJoinRequestIntegrationTest extends JoinRequestIntegrationSupport {
    @Test
    @DisplayName("신청 응답 시각과 DB 생성 시각이 일치하고 취소 이력을 보존한다")
    void createAndCancel_validRequest_preservesAuditAndHistory() {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 3);

        // when
        var created = commands.create(group, new GroupJoinRequestReqDTO.Create("소개"), applicant);
        LocalDateTime previousAudit = LocalDateTime.of(2020, 1, 1, 0, 0);
        jdbc.update("UPDATE group_join_request SET updated_at = ? WHERE id = ?", previousAudit, created.requestId());
        commands.cancel(group, applicant);
        // 커밋 후 별도 트랜잭션에서 저장과 동일한 JPA 날짜 매핑으로 재조회한다.
        var latest = queries.getMine(group, applicant);

        // then
        var row = jdbc.queryForMap("SELECT * FROM group_join_request WHERE id = ?", created.requestId());
        assertAll(
                () -> assertThat(created.createdAt()).isEqualTo(latest.createdAt()),
                () -> assertThat(latest.status().name()).isEqualTo("CANCELLED"),
                () -> assertThat(row.get("processed_by_group_member_id")).isNull(),
                () -> assertThat(row.get("processed_at")).isNotNull(),
                () -> assertThat(jdbc.queryForObject("SELECT updated_at FROM group_join_request WHERE id = ?",
                        LocalDateTime.class, created.requestId())).isAfter(previousAudit));
    }

    @ParameterizedTest
    @CsvSource({"OWNER,LEFT,false", "MANAGER,LEFT,true", "OWNER,ACTIVE,true", "MANAGER,ACTIVE,true"})
    @DisplayName("탈퇴 또는 소프트 삭제 멤버십은 기존 ID와 MEMBER 역할로 복구한다")
    void approve_rejoiningMember_restoresIdAndResetsRole(String role, String status, boolean deleted) {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 2);
        Long membership = membership(group, applicant, role, status, deleted);
        Long request = apply(group, applicant);

        // when
        commands.approve(group, request, owner);

        // then
        var row = jdbc.queryForMap("SELECT * FROM group_member WHERE id = ?", membership);
        var processedAt = jdbc.queryForObject("SELECT processed_at FROM group_join_request WHERE id = ?",
                LocalDateTime.class, request);
        assertAll(
                () -> assertThat(row.get("role")).isEqualTo("MEMBER"),
                () -> assertThat(row.get("status")).isEqualTo("ACTIVE"),
                () -> assertThat(row.get("deleted_at")).isNull(),
                () -> assertThat(jdbc.queryForObject("SELECT joined_at FROM group_member WHERE id = ?",
                        LocalDateTime.class, membership)).isEqualTo(processedAt),
                () -> assertThat(requestStatus(request)).isEqualTo("APPROVED"),
                () -> assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_member WHERE group_id = ? AND member_id = ?",
                        Integer.class, group, applicant)).isEqualTo(1));
    }

    @Test
    @DisplayName("MANAGER 승인은 MEMBER 멤버십과 운영진 처리자 ID를 저장한다")
    void approve_manager_createsMembershipAndProcessor() {
        // given
        Long owner = member();
        Long manager = member();
        Long applicant = member();
        Long group = group(owner, 3);
        Long managerMembership = membership(group, manager, "MANAGER", "ACTIVE", false);
        Long request = apply(group, applicant);

        // when
        commands.approve(group, request, manager);

        // then
        assertAll(
                () -> assertThat(requestStatus(request)).isEqualTo("APPROVED"),
                () -> assertThat(jdbc.queryForObject("SELECT processed_by_group_member_id FROM group_join_request WHERE id = ?",
                        Long.class, request)).isEqualTo(managerMembership),
                () -> assertThat(jdbc.queryForObject("SELECT joined_at FROM group_member WHERE group_id = ? AND member_id = ?",
                        LocalDateTime.class, group, applicant)).isEqualTo(jdbc.queryForObject(
                                "SELECT processed_at FROM group_join_request WHERE id = ?", LocalDateTime.class, request)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"reject", "cancel"})
    @DisplayName("거절·취소 후 새 신청을 만들고 이전 이력은 보존한다")
    void create_afterProcessed_preservesBothRequests(String action) {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 3);
        Long first = apply(group, applicant);
        if (action.equals("reject")) commands.reject(group, first, new GroupJoinRequestReqDTO.Reject("사유"), owner);
        else commands.cancel(group, applicant);

        // when
        Long second = apply(group, applicant);

        // then
        assertAll(
                () -> assertThat(second).isGreaterThan(first),
                () -> assertThat(queries.getMine(group, applicant).requestId()).isEqualTo(second),
                () -> assertThat(requestStatus(first)).isEqualTo(action.equals("reject") ? "REJECTED" : "CANCELLED"),
                () -> assertThat(requestStatus(second)).isEqualTo("PENDING"));
    }

    @ParameterizedTest
    @CsvSource({"OWNER,ACTIVE,true", "MANAGER,ACTIVE,true", "OWNER,LEFT,false", "MANAGER,KICKED,false", "MEMBER,ACTIVE,false"})
    @DisplayName("삭제·비활성 운영진과 일반 회원의 목록·승인·거절은 차단한다")
    void manage_ineligibleRole_forbidden(String role, String status, boolean deleted) {
        // given
        Long owner = member();
        Long actor = member();
        Long applicant = member();
        Long group = group(owner, 5);
        membership(group, actor, role, status, deleted);
        Long request = apply(group, applicant);

        // when
        var listError = assertThrows(GeneralException.class, () -> queries.getPending(group, 0, 20, actor));
        var approveError = assertThrows(GeneralException.class, () -> commands.approve(group, request, actor));
        var rejectError = assertThrows(GeneralException.class,
                () -> commands.reject(group, request, new GroupJoinRequestReqDTO.Reject(null), actor));

        // then
        assertAll(
                () -> assertThat(listError.getCode().getCode()).isEqualTo("AUTH403_1"),
                () -> assertThat(approveError.getCode().getCode()).isEqualTo("AUTH403_1"),
                () -> assertThat(rejectError.getCode().getCode()).isEqualTo("AUTH403_1"),
                () -> assertThat(requestStatus(request)).isEqualTo("PENDING"));
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,GROUP409_2", "KICKED,GROUP409_5"})
    @DisplayName("이미 가입하거나 강퇴된 회원의 신청은 구분된 코드로 거부한다")
    void create_ineligibleMember_returnsSpecificConflict(String status, String code) {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 5);
        membership(group, applicant, "MEMBER", status, false);

        // when
        var error = assertThrows(GroupException.class, () -> apply(group, applicant));

        // then
        assertThat(error.getCode().getCode()).isEqualTo(code);
    }

    @Test
    @DisplayName("동일 대기 신청과 취소 대상 없음은 서로 다른 409를 반환한다")
    void create_duplicateAndCancelMissing_returnSpecificCodes() {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 3);
        apply(group, applicant);

        // when
        var duplicate = assertThrows(GroupException.class, () -> apply(group, applicant));
        var absent = assertThrows(GroupException.class, () -> commands.cancel(group, owner));

        // then
        assertAll(
                () -> assertThat(duplicate.getCode().getCode()).isEqualTo("GROUP409_3"),
                () -> assertThat(absent.getCode().getCode()).isEqualTo("GROUP409_7"));
    }

    @Test
    @DisplayName("정원은 운영진을 포함하고 대기 신청은 자리를 예약하지 않는다")
    void approve_capacityFilledAfterApply_rechecksCapacity() {
        // given
        Long owner = member();
        Long group = group(owner, 2);
        Long first = apply(group, member());
        Long second = apply(group, member());
        commands.approve(group, first, owner);

        // when
        var error = assertThrows(GroupException.class, () -> commands.approve(group, second, owner));
        var createError = assertThrows(GroupException.class, () -> apply(group, member()));

        // then
        assertAll(
                () -> assertThat(error.getCode().getCode()).isEqualTo("GROUP409_4"),
                () -> assertThat(createError.getCode().getCode()).isEqualTo("GROUP409_4"),
                () -> assertThat(requestStatus(second)).isEqualTo("PENDING"));
    }

    @Test
    @DisplayName("ARCHIVED는 조회·취소만 허용한다")
    void archivedGroup_onlyReadsAndCancellationAllowed() {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 3);
        Long request = apply(group, applicant);
        jdbc.update("UPDATE member_group SET status = 'ARCHIVED' WHERE id = ?", group);

        // when
        var createError = assertThrows(GroupException.class, () -> apply(group, member()));
        var approveError = assertThrows(GroupException.class, () -> commands.approve(group, request, owner));
        var rejectError = assertThrows(GroupException.class,
                () -> commands.reject(group, request, new GroupJoinRequestReqDTO.Reject(null), owner));
        var mine = queries.getMine(group, applicant);
        var pending = queries.getPending(group, 0, 20, owner);
        commands.cancel(group, applicant);

        // then
        assertAll(
                () -> assertThat(createError.getCode().getCode()).isEqualTo("GROUP409_1"),
                () -> assertThat(approveError.getCode().getCode()).isEqualTo("GROUP409_1"),
                () -> assertThat(rejectError.getCode().getCode()).isEqualTo("GROUP409_1"),
                () -> assertThat(mine.requestId()).isEqualTo(request),
                () -> assertThat(pending.items()).hasSize(1),
                () -> assertThat(requestStatus(request)).isEqualTo("CANCELLED"));
    }

    @Test
    @DisplayName("OPEN 그룹 신청은 400으로 차단한다")
    void create_openGroup_badRequest() {
        // given
        Long group = group(member(), 3);
        jdbc.update("UPDATE member_group SET join_policy = 'OPEN' WHERE id = ?", group);

        // when
        var error = assertThrows(GroupException.class, () -> apply(group, member()));

        // then
        assertThat(error.getCode().getCode()).isEqualTo("GROUP400_3");
    }

    @ParameterizedTest
    @ValueSource(strings = {"reject", "cancel", "approve"})
    @DisplayName("처리된 신청의 재승인·재거절은 409이다")
    void process_nonPending_conflict(String action) {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 3);
        Long request = apply(group, applicant);
        switch (action) {
            case "reject" -> commands.reject(group, request, new GroupJoinRequestReqDTO.Reject(null), owner);
            case "cancel" -> commands.cancel(group, applicant);
            default -> commands.approve(group, request, owner);
        }

        // when
        var approveError = assertThrows(GroupException.class, () -> commands.approve(group, request, owner));
        var rejectError = assertThrows(GroupException.class,
                () -> commands.reject(group, request, new GroupJoinRequestReqDTO.Reject(null), owner));

        // then
        assertAll(
                () -> assertThat(approveError.getCode().getCode()).isEqualTo("GROUP409_6"),
                () -> assertThat(rejectError.getCode().getCode()).isEqualTo("GROUP409_6"));
    }

    @Test
    @DisplayName("다른 그룹 신청 ID는 승인·거절 모두 404이다")
    void process_otherGroupRequest_notFound() {
        // given
        Long owner = member();
        Long first = group(owner, 3);
        Long second = group(owner, 3);
        Long request = apply(first, member());

        // when
        var approveError = assertThrows(GroupException.class, () -> commands.approve(second, request, owner));
        var rejectError = assertThrows(GroupException.class,
                () -> commands.reject(second, request, new GroupJoinRequestReqDTO.Reject(null), owner));

        // then
        assertAll(
                () -> assertThat(approveError.getCode().getCode()).isEqualTo("GROUP404_3"),
                () -> assertThat(rejectError.getCode().getCode()).isEqualTo("GROUP404_3"));
    }

    @Test
    @DisplayName("폐쇄된 그룹의 조회·변경을 막고 신청 이력을 보존한다")
    void close_group_preservesHistoryAndBlocksRequests() {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 3);
        Long request = apply(group, applicant);
        groups.closeGroup(group, new GroupReqDTO.Close("종료", "가입 테스트", false), owner);

        // when
        var mineError = assertThrows(GroupException.class, () -> queries.getMine(group, applicant));
        var cancelError = assertThrows(GroupException.class, () -> commands.cancel(group, applicant));
        var createError = assertThrows(GroupException.class, () -> apply(group, applicant));
        var approveError = assertThrows(GroupException.class, () -> commands.approve(group, request, owner));
        var rejectError = assertThrows(GroupException.class,
                () -> commands.reject(group, request, new GroupJoinRequestReqDTO.Reject(null), owner));
        var listError = assertThrows(GroupException.class, () -> queries.getPending(group, 0, 20, owner));

        // then
        assertAll(
                () -> assertThat(mineError.getCode().getCode()).isEqualTo("GROUP404_1"),
                () -> assertThat(cancelError.getCode().getCode()).isEqualTo("GROUP404_1"),
                () -> assertThat(createError.getCode().getCode()).isEqualTo("GROUP404_1"),
                () -> assertThat(approveError.getCode().getCode()).isEqualTo("GROUP404_1"),
                () -> assertThat(rejectError.getCode().getCode()).isEqualTo("GROUP404_1"),
                () -> assertThat(listError.getCode().getCode()).isEqualTo("GROUP404_1"),
                () -> assertThat(requestStatus(request)).isEqualTo("PENDING"));
    }

    @Test
    @DisplayName("대기 목록은 필터·동률 ID 정렬·페이지 경계와 빈 결과를 지킨다")
    void getPending_paginationAndTies_orderedAndFiltered() {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 5);
        Long old = apply(group, applicant);
        commands.cancel(group, applicant);
        Long first = apply(group, applicant);
        Long second = apply(group, member());
        Long third = apply(group, member());
        LocalDateTime same = LocalDateTime.of(2026, 10, 19, 12, 0);
        jdbc.update("UPDATE group_join_request SET created_at = ? WHERE group_id = ?", same, group);

        // when
        var page0 = queries.getPending(group, 0, 2, owner);
        var page1 = queries.getPending(group, 1, 2, owner);
        var page2 = queries.getPending(group, 2, 2, owner);
        var latest = queries.getMine(group, applicant);
        var absent = queries.getMine(group, owner);

        // then
        assertAll(
                () -> assertThat(page0.items()).extracting("requestId").containsExactly(first, second),
                () -> assertThat(page0.hasNext()).isTrue(),
                () -> assertThat(page0.page()).isZero(),
                () -> assertThat(page0.size()).isEqualTo(2),
                () -> assertThat(page1.items()).extracting("requestId").containsExactly(third),
                () -> assertThat(page1.hasNext()).isFalse(),
                () -> assertThat(page2.items()).isEmpty(),
                () -> assertThat(page2.hasNext()).isFalse(),
                () -> assertThat(latest.requestId()).isEqualTo(first).isGreaterThan(old),
                () -> assertThat(absent).isNull());
    }
    @ParameterizedTest
    @ValueSource(strings = {"missing", "deleted"})
    @DisplayName("없는 그룹과 ACTIVE이지만 삭제된 그룹은 404이다")
    void getMine_missingOrDeletedGroup_notFound(String kind) {
        // given
        Long owner = member();
        Long group = kind.equals("missing") ? Long.MAX_VALUE : group(owner, 3);
        if (kind.equals("deleted")) {
            jdbc.update("UPDATE member_group SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", group);
        }

        // when
        var readError = assertThrows(GroupException.class, () -> queries.getMine(group, owner));
        var writeError = assertThrows(GroupException.class, () -> commands.cancel(group, owner));

        // then
        assertAll(
                () -> assertThat(readError.getCode().getCode()).isEqualTo("GROUP404_1"),
                () -> assertThat(writeError.getCode().getCode()).isEqualTo("GROUP404_1"));
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,false,GROUP409_2", "KICKED,false,GROUP409_5", "KICKED,true,GROUP409_5"})
    @DisplayName("승인 직전 가입·강퇴 상태를 재검사한다")
    void approve_membershipChangedAfterApply_conflict(String status, boolean deleted, String code) {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 5);
        Long request = apply(group, applicant);
        membership(group, applicant, "MEMBER", status, deleted);

        // when
        var error = assertThrows(GroupException.class, () -> commands.approve(group, request, owner));

        // then
        assertAll(
                () -> assertThat(error.getCode().getCode()).isEqualTo(code),
                () -> assertThat(requestStatus(request)).isEqualTo("PENDING"));
    }

}
