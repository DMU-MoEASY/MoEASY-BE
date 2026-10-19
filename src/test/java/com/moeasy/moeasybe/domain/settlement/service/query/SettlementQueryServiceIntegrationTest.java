package com.moeasy.moeasybe.domain.settlement.service.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moeasy.moeasybe.domain.group.entity.Category;
import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.domain.group.repository.GroupMemberRepository;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.dto.response.SettlementResDTO;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import com.moeasy.moeasybe.domain.settlement.service.command.SettlementCommandService;
import com.moeasy.moeasybe.global.apiPayload.code.BaseErrorCode;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SettlementQueryServiceIntegrationTest {

    @Autowired private SettlementQueryService service;
    @Autowired private SettlementCommandService commandService;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    private final List<Long> groupIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();
    private Group group;
    private GroupMember owner;
    private GroupMember target;

    @BeforeEach
    void setUp() {
        group = persistGroup();
        owner = persistGroupMember(group, GroupMemberRole.OWNER);
        target = persistGroupMember(group, GroupMemberRole.MEMBER);
    }

    @AfterEach
    void cleanUp() {
        // 외부 테스트 트랜잭션 없이 실제 서비스 트랜잭션 종료 후 DB 상태를 확인한다.
        for (Long groupId : groupIds) {
            jdbcTemplate.update("""
                    DELETE si FROM settlement_item si JOIN settlement s
                    ON s.id = si.settlement_id WHERE s.group_id = ?
                    """, groupId);
            jdbcTemplate.update("DELETE FROM settlement WHERE group_id = ?", groupId);
            jdbcTemplate.update("DELETE FROM group_member WHERE group_id = ?", groupId);
            jdbcTemplate.update("DELETE FROM member_group WHERE id = ?", groupId);
        }
        for (Long memberId : memberIds) {
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
        }
    }

    @Test
    @DisplayName("균등 미리보기는 ID 순서·닉네임·나머지를 반환하고 정산과 청구를 저장하지 않는다")
    void previewSplit_equal_returnsSortedAmountsWithoutSaving() {
        // given
        GroupMember third = persistGroupMember(group, GroupMemberRole.MEMBER);
        SettlementReqDTO.Preview request = equalRequest(List.of(third.getId(), target.getId(), owner.getId()));

        // when
        SettlementResDTO.Preview result = service.previewSplit(group.getId(), request, owner.getMember().getId());

        // then
        assertAll(
                () -> assertEquals(SettlementSplitMode.EQUAL, result.splitMode()),
                () -> assertEquals(500L, result.totalAmount()),
                () -> assertEquals(3, result.targetCount()),
                () -> assertEquals(List.of(owner.getId(), target.getId(), third.getId()),
                        result.items().stream().map(SettlementResDTO.PreviewItem::groupMemberId).toList()),
                () -> assertEquals(List.of(owner.getMember().getNickname(), target.getMember().getNickname(),
                                third.getMember().getNickname()),
                        result.items().stream().map(SettlementResDTO.PreviewItem::nickname).toList()),
                () -> assertEquals(List.of(167L, 167L, 166L),
                        result.items().stream().map(SettlementResDTO.PreviewItem::amount).toList()),
                () -> assertEquals(List.of(1L, 1L, 0L),
                        result.items().stream().map(SettlementResDTO.PreviewItem::remainderAmount).toList()),
                this::assertNoSettlementWrites
        );
    }

    @Test
    @DisplayName("운영진은 본인을 포함한 회원별 금액을 미리보며 지정 금액과 합계를 유지한다")
    void previewSplit_managerCustom_returnsSpecifiedAmounts() {
        // given
        GroupMember manager = persistGroupMember(group, GroupMemberRole.MANAGER);
        SettlementReqDTO.Preview request = new SettlementReqDTO.Preview(SettlementSplitMode.CUSTOM, 500L, null,
                List.of(new SettlementReqDTO.MemberAmount(manager.getId(), 300L),
                        new SettlementReqDTO.MemberAmount(target.getId(), 200L)));

        // when
        SettlementResDTO.Preview result = service.previewSplit(group.getId(), request, manager.getMember().getId());

        // then
        assertAll(
                () -> assertEquals(SettlementSplitMode.CUSTOM, result.splitMode()),
                () -> assertEquals(500L, result.totalAmount()),
                () -> assertEquals(2, result.targetCount()),
                () -> assertEquals(List.of(target.getId(), manager.getId()),
                        result.items().stream().map(SettlementResDTO.PreviewItem::groupMemberId).toList()),
                () -> assertEquals(List.of(200L, 300L),
                        result.items().stream().map(SettlementResDTO.PreviewItem::amount).toList()),
                () -> assertTrue(result.items().stream().allMatch(item -> item.remainderAmount() == 0L)),
                this::assertNoSettlementWrites
        );
    }

    @Test
    @DisplayName("같은 입력의 미리보기 금액과 실제 생성 청구액이 일치한다")
    void previewSplit_sameInputAsCreate_returnsSameCharges() {
        // given
        GroupMember third = persistGroupMember(group, GroupMemberRole.MEMBER);
        List<Long> ids = List.of(third.getId(), target.getId(), owner.getId());
        SettlementResDTO.Preview preview = service.previewSplit(group.getId(), equalRequest(ids), owner.getMember().getId());
        assertNoSettlementWrites();
        SettlementReqDTO.Create request = new SettlementReqDTO.Create("정산", null, null, null,
                SettlementSplitMode.EQUAL, 500L, ids, null);

        // when
        SettlementResDTO.Created created = commandService.createSettlement(group.getId(), request, owner.getMember().getId());

        // then
        List<Long> savedAmounts = jdbcTemplate.queryForList(
                "SELECT amount FROM settlement_item WHERE settlement_id = ? ORDER BY group_member_id",
                Long.class, created.settlementId());
        assertEquals(preview.items().stream().map(SettlementResDTO.PreviewItem::amount).toList(), savedAmounts);
    }

    @Test
    @DisplayName("대상 목록은 회원 정보도 함께 로딩해 닉네임별 추가 조회를 피한다")
    void findTargets_validMemberships_fetchesMembersTogether() {
        // given
        List<Long> ids = List.of(owner.getId(), target.getId());

        // when
        List<Boolean> loaded = transactionTemplate.execute(status -> {
            entityManager.clear();
            return groupMemberRepository
                    .findAllByGroupIdAndIdInAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                            group.getId(), ids, GroupMemberStatus.ACTIVE)
                    .stream().map(membership -> entityManager.getEntityManagerFactory()
                            .getPersistenceUnitUtil().isLoaded(membership, "member")).toList();
        });

        // then
        assertEquals(List.of(true, true), loaded);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "CLOSED", "DELETED"})
    @DisplayName("없거나 폐쇄·삭제된 모임은 미리볼 수 없다")
    void previewSplit_unavailableGroup_rejectsRequest(String condition) {
        // given
        Long groupId = group.getId();
        if (condition.equals("MISSING")) {
            groupId = Long.MAX_VALUE;
        } else if (condition.equals("CLOSED")) {
            jdbcTemplate.update("UPDATE member_group SET status = 'CLOSED' WHERE id = ?", groupId);
        } else {
            jdbcTemplate.update("UPDATE member_group SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", groupId);
        }
        Long requestedGroupId = groupId;
        SettlementReqDTO.Preview request = equalRequest(List.of(target.getId()));

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.previewSplit(requestedGroupId, request, owner.getMember().getId()));

        // then
        assertRejected(exception, GroupErrorCode.GROUP_NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "MISSING", "OTHER_GROUP", "LEFT", "KICKED", "DELETED_MEMBERSHIP", "DELETED_MEMBER"})
    @DisplayName("일반 회원·미가입·다른 모임·비활성·삭제된 요청자는 미리볼 수 없다")
    void previewSplit_unauthorizedRequester_rejectsRequest(String condition) {
        // given
        Long requesterId = owner.getMember().getId();
        if (condition.equals("MEMBER")) {
            requesterId = target.getMember().getId();
        } else if (condition.equals("MISSING")) {
            requesterId = Long.MAX_VALUE;
        } else if (condition.equals("OTHER_GROUP")) {
            requesterId = persistGroupMember(persistGroup(), GroupMemberRole.OWNER).getMember().getId();
        } else {
            invalidateMembership(owner, condition);
        }
        Long memberId = requesterId;
        SettlementReqDTO.Preview request = equalRequest(List.of(target.getId()));

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.previewSplit(group.getId(), request, memberId));

        // then
        assertRejected(exception, GeneralErrorCode.FORBIDDEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "OTHER_GROUP", "LEFT", "KICKED", "DELETED_MEMBERSHIP", "DELETED_MEMBER"})
    @DisplayName("유효하지 않은 대상이 포함되면 일부만 제외하지 않고 전체를 거부한다")
    void previewSplit_invalidTarget_rejectsWholeRequest(String condition) {
        // given
        Long targetId = target.getId();
        if (condition.equals("MISSING")) {
            targetId = Long.MAX_VALUE;
        } else if (condition.equals("OTHER_GROUP")) {
            targetId = persistGroupMember(persistGroup(), GroupMemberRole.MEMBER).getId();
        } else {
            invalidateMembership(target, condition);
        }
        SettlementReqDTO.Preview request = equalRequest(List.of(owner.getId(), targetId));

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.previewSplit(group.getId(), request, owner.getMember().getId()));

        // then
        assertRejected(exception, SettlementErrorCode.INVALID_TARGET_MEMBERS);
    }

    @Test
    @DisplayName("대상 ID가 중복되면 미리보기도 거부한다")
    void previewSplit_duplicateTarget_rejectsRequest() {
        // given
        SettlementReqDTO.Preview request = equalRequest(List.of(target.getId(), target.getId()));

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.previewSplit(group.getId(), request, owner.getMember().getId()));

        // then
        assertRejected(exception, SettlementErrorCode.INVALID_TARGET_MEMBERS);
    }

    @ParameterizedTest
    @MethodSource("invalidSplitRequests")
    @DisplayName("분할 방식의 필수 배열 누락·불필요한 배열은 미리보기에서도 거부한다")
    void previewSplit_invalidFields_rejectsRequest(SettlementReqDTO.Preview request) {
        // given: 조건부 필드는 계산기의 공통 진입점에서 검증한다.

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.previewSplit(group.getId(), request, owner.getMember().getId()));

        // then
        assertRejected(exception, SettlementErrorCode.INVALID_SPLIT_MODE_FIELDS);
    }

    private static Stream<SettlementReqDTO.Preview> invalidSplitRequests() {
        return Stream.of(
                new SettlementReqDTO.Preview(null, 500L, List.of(1L), null),
                new SettlementReqDTO.Preview(SettlementSplitMode.EQUAL, 500L, null, null),
                new SettlementReqDTO.Preview(SettlementSplitMode.EQUAL, 500L, List.of(1L), List.of()),
                new SettlementReqDTO.Preview(SettlementSplitMode.CUSTOM, 500L, null, null),
                new SettlementReqDTO.Preview(SettlementSplitMode.CUSTOM, 500L, List.of(),
                        List.of(new SettlementReqDTO.MemberAmount(1L, 500L)))
        );
    }

    @Test
    @DisplayName("회원별 금액 합계가 총액과 다르면 미리보기를 거부한다")
    void previewSplit_customTotalMismatch_rejectsRequest() {
        // given
        SettlementReqDTO.Preview request = new SettlementReqDTO.Preview(SettlementSplitMode.CUSTOM, 500L, null,
                List.of(new SettlementReqDTO.MemberAmount(target.getId(), 499L)));

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.previewSplit(group.getId(), request, owner.getMember().getId()));

        // then
        assertRejected(exception, SettlementErrorCode.INVALID_AMOUNT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GROUP", "REQUEST", "MEMBER"})
    @DisplayName("서비스 직접 호출 시 필수 인자가 null이면 잘못된 요청으로 거부한다")
    void previewSplit_nullArgument_rejectsRequest(String missing) {
        // given
        Long groupId = missing.equals("GROUP") ? null : group.getId();
        Long memberId = missing.equals("MEMBER") ? null : owner.getMember().getId();
        SettlementReqDTO.Preview request = missing.equals("REQUEST") ? null : equalRequest(List.of(target.getId()));

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.previewSplit(groupId, request, memberId));

        // then
        assertRejected(exception, GeneralErrorCode.BAD_REQUEST);
    }

    private SettlementReqDTO.Preview equalRequest(List<Long> ids) {
        return new SettlementReqDTO.Preview(SettlementSplitMode.EQUAL, 500L, ids, null);
    }

    private Group persistGroup() {
        Group created = transactionTemplate.execute(status -> {
            Category category = entityManager.createQuery("select c from Category c where c.code = :code", Category.class)
                    .setParameter("code", "RUNNING").getSingleResult();
            Group entity = Group.builder().category(category).name("미리보기 " + UUID.randomUUID())
                    .description("설명").regionCode("11680").placeName("장소").address("주소")
                    .maxMembers(20).joinPolicy(GroupJoinPolicy.APPROVAL).build();
            entityManager.persist(entity);
            return entity;
        });
        groupIds.add(created.getId());
        return created;
    }

    private GroupMember persistGroupMember(Group memberGroup, GroupMemberRole role) {
        return transactionTemplate.execute(status -> {
            String key = UUID.randomUUID().toString();
            Member member = Member.builder().socialType(SocialType.KAKAO).socialId(key)
                    .nickname("미리보기" + key).build();
            entityManager.persist(member);
            memberIds.add(member.getId());
            GroupMember membership = GroupMember.builder().group(memberGroup).member(member).role(role)
                    .status(GroupMemberStatus.ACTIVE).joinedAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build();
            entityManager.persist(membership);
            return membership;
        });
    }

    private void invalidateMembership(GroupMember membership, String condition) {
        switch (condition) {
            case "LEFT", "KICKED" -> jdbcTemplate.update(
                    "UPDATE group_member SET status = ? WHERE id = ?", condition, membership.getId());
            case "DELETED_MEMBERSHIP" -> jdbcTemplate.update(
                    "UPDATE group_member SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", membership.getId());
            case "DELETED_MEMBER" -> jdbcTemplate.update(
                    "UPDATE member SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", membership.getMember().getId());
            default -> throw new IllegalArgumentException("알 수 없는 테스트 조건: " + condition);
        }
    }

    private void assertRejected(GeneralException exception, BaseErrorCode expected) {
        assertAll(() -> assertEquals(expected, exception.getCode()), this::assertNoSettlementWrites);
    }

    private void assertNoSettlementWrites() {
        assertAll(
                () -> assertEquals(0, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM settlement WHERE group_id = ?", Integer.class, group.getId())),
                () -> assertEquals(0, jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM settlement_item si JOIN group_member gm
                        ON gm.id = si.group_member_id WHERE gm.group_id = ?
                        """, Integer.class, group.getId()))
        );
    }
}
