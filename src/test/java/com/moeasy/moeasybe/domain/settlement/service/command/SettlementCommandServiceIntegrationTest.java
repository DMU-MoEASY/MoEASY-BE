package com.moeasy.moeasybe.domain.settlement.service.command;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.moeasy.moeasybe.domain.group.entity.Category;
import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.dto.response.SettlementResDTO;
import com.moeasy.moeasybe.domain.settlement.entity.SettlementItem;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementItemRepository;
import com.moeasy.moeasybe.global.apiPayload.code.BaseErrorCode;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SettlementCommandServiceIntegrationTest {

    private static final LocalDateTime SEOUL_NOW = LocalDateTime.of(2026, 10, 19, 9, 0);

    @Autowired private SettlementCommandService service;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;
    @MockitoBean(name = "clock") private Clock clock;
    @MockitoSpyBean private SettlementItemRepository itemRepository;

    private final List<Long> groupIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();
    private Group group;
    private GroupMember owner;
    private GroupMember target;

    @BeforeEach
    void setUp() {
        // UTC 자정은 서울 09:00이다. 서버 기본 시간대에 의존하지 않는지 함께 검증한다.
        when(clock.instant()).thenReturn(Instant.parse("2026-10-19T00:00:00Z"));
        group = persistGroup();
        owner = persistGroupMember(group, GroupMemberRole.OWNER);
        target = persistGroupMember(group, GroupMemberRole.MEMBER);
    }

    @AfterEach
    void cleanUp() {
        // 테스트 자체에는 트랜잭션을 걸지 않아 서비스의 실제 커밋·롤백 경계를 확인한다.
        for (Long groupId : groupIds) {
            jdbcTemplate.update("""
                    DELETE si FROM settlement_item si
                    JOIN settlement s ON s.id = si.settlement_id WHERE s.group_id = ?
                    """, groupId);
            jdbcTemplate.update("DELETE FROM settlement WHERE group_id = ?", groupId);
            jdbcTemplate.update("DELETE FROM schedule WHERE group_id = ?", groupId);
            jdbcTemplate.update("DELETE FROM group_member WHERE group_id = ?", groupId);
            jdbcTemplate.update("DELETE FROM member_group WHERE id = ?", groupId);
        }
        for (Long memberId : memberIds) {
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
        }
    }

    @Test
    @DisplayName("모임장은 균등 청구를 생성하며 나머지를 포함한 합계와 OPEN·PENDING 상태가 저장된다")
    void createSettlement_equal_commitsSettlementAndItems() {
        // given
        GroupMember third = persistGroupMember(group, GroupMemberRole.MEMBER);
        SettlementReqDTO.Create request = equalRequest(List.of(third.getId(), target.getId(), owner.getId()));

        // when
        SettlementResDTO.Created result = service.createSettlement(group.getId(), request, owner.getMember().getId());

        // then
        var saved = jdbcTemplate.queryForMap("SELECT * FROM settlement WHERE id = ?", result.settlementId());
        List<Long> amounts = jdbcTemplate.queryForList("""
                SELECT amount FROM settlement_item WHERE settlement_id = ? ORDER BY group_member_id
                """, Long.class, result.settlementId());
        assertAll(
                () -> assertNotNull(result.settlementId()),
                () -> assertEquals(group.getId(), ((Number) saved.get("group_id")).longValue()),
                () -> assertEquals(owner.getId(), ((Number) saved.get("created_by_group_member_id")).longValue()),
                () -> assertEquals("정산", saved.get("title")),
                () -> assertEquals(500L, ((Number) saved.get("total_amount")).longValue()),
                () -> assertEquals("EQUAL", saved.get("split_mode")),
                () -> assertEquals("OPEN", saved.get("status")),
                () -> assertNull(saved.get("schedule_id")),
                () -> assertNull(saved.get("due_at")),
                () -> assertNull(saved.get("fee_rule_id")),
                () -> assertEquals(List.of(167L, 167L, 166L), amounts),
                () -> assertEquals(500L, amounts.stream().mapToLong(Long::longValue).sum()),
                () -> assertEquals(List.of("PENDING", "PENDING", "PENDING"), jdbcTemplate.queryForList(
                        "SELECT status FROM settlement_item WHERE settlement_id = ?", String.class, result.settlementId())),
                () -> assertEquals(0, jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM settlement_payment sp JOIN settlement_item si
                        ON si.id = sp.settlement_item_id WHERE si.settlement_id = ?
                        """, Integer.class, result.settlementId()))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-19T09:00:01", "9999-12-31T23:59:59"})
    @DisplayName("운영진도 일정 참석 여부와 무관하게 본인을 포함한 회원별 지정 청구를 생성한다")
    void createSettlement_managerCustomWithSchedule_commitsSpecifiedAmounts(String dueAtValue) {
        // given
        GroupMember manager = persistGroupMember(group, GroupMemberRole.MANAGER);
        Long scheduleId = insertSchedule(group.getId());
        LocalDateTime dueAt = LocalDateTime.parse(dueAtValue);
        SettlementReqDTO.Create request = new SettlementReqDTO.Create("정산", "설명", dueAt,
                scheduleId, SettlementSplitMode.CUSTOM, 20000L, null, List.of(
                        new SettlementReqDTO.MemberAmount(manager.getId(), 12000L),
                        new SettlementReqDTO.MemberAmount(target.getId(), 8000L)));

        // when
        SettlementResDTO.Created result = service.createSettlement(group.getId(), request, manager.getMember().getId());

        // then
        assertAll(
                () -> assertEquals(manager.getId(), jdbcTemplate.queryForObject(
                        "SELECT created_by_group_member_id FROM settlement WHERE id = ?", Long.class, result.settlementId())),
                () -> assertEquals(scheduleId, jdbcTemplate.queryForObject(
                        "SELECT schedule_id FROM settlement WHERE id = ?", Long.class, result.settlementId())),
                () -> assertEquals(dueAt, transactionTemplate.execute(status ->
                        entityManager.createQuery("select s.dueAt from Settlement s where s.id = :id", LocalDateTime.class)
                                .setParameter("id", result.settlementId()).getSingleResult())),
                () -> assertEquals("설명", jdbcTemplate.queryForObject(
                        "SELECT description FROM settlement WHERE id = ?", String.class, result.settlementId())),
                () -> assertEquals("CUSTOM", jdbcTemplate.queryForObject(
                        "SELECT split_mode FROM settlement WHERE id = ?", String.class, result.settlementId())),
                () -> assertEquals(12000L, jdbcTemplate.queryForObject("""
                        SELECT amount FROM settlement_item WHERE settlement_id = ? AND group_member_id = ?
                        """, Long.class, result.settlementId(), manager.getId())),
                () -> assertEquals("PENDING", jdbcTemplate.queryForObject("""
                        SELECT status FROM settlement_item WHERE settlement_id = ? AND group_member_id = ?
                        """, String.class, result.settlementId(), manager.getId())),
                () -> assertEquals(8000L, jdbcTemplate.queryForObject("""
                        SELECT amount FROM settlement_item WHERE settlement_id = ? AND group_member_id = ?
                        """, Long.class, result.settlementId(), target.getId()))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "LEFT", "KICKED", "DELETED_MEMBERSHIP", "DELETED_MEMBER", "OTHER_GROUP"})
    @DisplayName("일반 회원·탈퇴·강퇴·삭제·다른 모임 회원은 정산을 생성할 수 없다")
    void createSettlement_invalidCreator_forbidden(String condition) {
        // given
        GroupMember actor = condition.equals("OTHER_GROUP")
                ? persistGroupMember(persistGroup(), GroupMemberRole.OWNER) : owner;
        if (condition.equals("MEMBER")) {
            jdbcTemplate.update("UPDATE group_member SET role = 'MEMBER' WHERE id = ?", actor.getId());
        } else if (!condition.equals("OTHER_GROUP")) {
            invalidateMembership(actor, condition);
        }
        SettlementReqDTO.Create request = equalRequest(List.of(target.getId()));

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.createSettlement(group.getId(), request, actor.getMember().getId()));

        // then
        assertRejected(exception, GeneralErrorCode.FORBIDDEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "CLOSED", "DELETED"})
    @DisplayName("없거나 폐쇄·삭제된 모임에는 정산을 생성하지 않는다")
    void createSettlement_invalidGroup_notFound(String condition) {
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
        SettlementReqDTO.Create request = equalRequest(List.of(target.getId()));

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.createSettlement(requestedGroupId, request, owner.getMember().getId()));

        // then
        assertRejected(exception, GroupErrorCode.GROUP_NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "LEFT", "KICKED", "DELETED_MEMBERSHIP", "DELETED_MEMBER", "OTHER_GROUP"})
    @DisplayName("대상 중 하나라도 같은 모임의 유효한 활성 회원이 아니면 전체 요청을 거부한다")
    void createSettlement_invalidTarget_rejectsWholeRequest(String condition) {
        // given
        Long targetId = target.getId();
        if (condition.equals("MISSING")) {
            targetId = Long.MAX_VALUE;
        } else if (condition.equals("OTHER_GROUP")) {
            targetId = persistGroupMember(persistGroup(), GroupMemberRole.MEMBER).getId();
        } else {
            invalidateMembership(target, condition);
        }
        SettlementReqDTO.Create request = equalRequest(List.of(owner.getId(), targetId));

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.createSettlement(group.getId(), request, owner.getMember().getId()));

        // then
        assertRejected(exception, SettlementErrorCode.INVALID_TARGET_MEMBERS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "DELETED", "OTHER_GROUP"})
    @DisplayName("없거나 삭제됐거나 다른 모임에 속한 일정은 정산에 연결할 수 없다")
    void createSettlement_invalidSchedule_rejectsRequest(String condition) {
        // given
        Long scheduleId = condition.equals("MISSING") ? Long.MAX_VALUE
                : insertSchedule(condition.equals("OTHER_GROUP") ? persistGroup().getId() : group.getId());
        if (condition.equals("DELETED")) {
            jdbcTemplate.update("UPDATE schedule SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", scheduleId);
        }
        SettlementReqDTO.Create request = new SettlementReqDTO.Create("정산", null, null, scheduleId,
                SettlementSplitMode.EQUAL, 500L, List.of(target.getId()), null);

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.createSettlement(group.getId(), request, owner.getMember().getId()));

        // then
        assertRejected(exception, SettlementErrorCode.INVALID_SCHEDULE);
    }

    @ParameterizedTest
    @MethodSource("invalidSplitRequests")
    @DisplayName("분할 방식의 필수 목록이 없거나 사용하지 않는 목록을 보내면 거부한다")
    void createSettlement_invalidSplitFields_rejectsRequest(SettlementReqDTO.Create request) {
        // given: 대상 조회 전에 조건부 필드 구성을 검증한다.

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.createSettlement(group.getId(), request, owner.getMember().getId()));

        // then
        assertRejected(exception, SettlementErrorCode.INVALID_SPLIT_MODE_FIELDS);
    }

    @ParameterizedTest
    @CsvSource({"-1", "0"})
    @DisplayName("서울 현재 시각과 같거나 지난 납부 기한은 거부한다")
    void createSettlement_nonFutureDueAt_rejectsRequest(long seconds) {
        // given
        SettlementReqDTO.Create request = new SettlementReqDTO.Create("정산", null,
                SEOUL_NOW.plusSeconds(seconds), null, SettlementSplitMode.EQUAL,
                500L, List.of(target.getId()), null);

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.createSettlement(group.getId(), request, owner.getMember().getId()));

        // then
        assertRejected(exception, SettlementErrorCode.INVALID_DUE_AT);
    }

    @Test
    @DisplayName("회원별 금액 합계가 총액과 다르면 정산을 저장하지 않는다")
    void createSettlement_customTotalMismatch_rejectsBeforeSaving() {
        // given
        SettlementReqDTO.Create request = new SettlementReqDTO.Create("정산", null, null, null,
                SettlementSplitMode.CUSTOM, 500L, null,
                List.of(new SettlementReqDTO.MemberAmount(target.getId(), 499L)));

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.createSettlement(group.getId(), request, owner.getMember().getId()));

        // then
        assertRejected(exception, SettlementErrorCode.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("청구 일부를 실제 저장한 뒤 실패해도 정산과 청구가 모두 롤백된다")
    void createSettlement_itemSaveFailure_rollsBackAllWrites() {
        // given
        SettlementReqDTO.Create request = equalRequest(List.of(owner.getId(), target.getId()));
        doAnswer(invocation -> {
            List<SettlementItem> items = invocation.getArgument(0);
            entityManager.persist(items.getFirst());
            entityManager.flush();
            assertEquals(1, jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM settlement_item WHERE settlement_id = ?
                    """, Integer.class, items.getFirst().getSettlement().getId()));
            throw new IllegalStateException("청구 저장 실패");
        }).when(itemRepository).saveAll(anyList());

        // when
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.createSettlement(group.getId(), request, owner.getMember().getId()));

        // then
        assertAll(
                () -> assertEquals("청구 저장 실패", exception.getMessage()),
                () -> assertEquals(0, settlementCount()),
                () -> assertEquals(0, jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM settlement_item si JOIN group_member gm
                        ON gm.id = si.group_member_id WHERE gm.group_id = ?
                        """, Integer.class, group.getId()))
        );
    }

    private static Stream<SettlementReqDTO.Create> invalidSplitRequests() {
        return Stream.of(
                new SettlementReqDTO.Create("정산", null, null, null, null, 500L, List.of(1L), null),
                new SettlementReqDTO.Create("정산", null, null, null, SettlementSplitMode.EQUAL, 500L, null, null),
                new SettlementReqDTO.Create("정산", null, null, null, SettlementSplitMode.EQUAL, 500L, List.of(1L), List.of()),
                new SettlementReqDTO.Create("정산", null, null, null, SettlementSplitMode.CUSTOM, 500L, null, null),
                new SettlementReqDTO.Create("정산", null, null, null, SettlementSplitMode.CUSTOM, 500L, List.of(),
                        List.of(new SettlementReqDTO.MemberAmount(1L, 500L)))
        );
    }

    private SettlementReqDTO.Create equalRequest(List<Long> ids) {
        return new SettlementReqDTO.Create(" 정산 ", null, null, null, SettlementSplitMode.EQUAL, 500L, ids, null);
    }

    private Group persistGroup() {
        Group created = transactionTemplate.execute(status -> {
            Category category = entityManager.createQuery("select c from Category c where c.code = :code", Category.class)
                    .setParameter("code", "RUNNING").getSingleResult();
            Group entity = Group.builder().category(category).name("정산 테스트 " + UUID.randomUUID())
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
            Member member = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).build();
            entityManager.persist(member);
            memberIds.add(member.getId());
            GroupMember membership = GroupMember.builder().group(memberGroup).member(member).role(role)
                    .status(GroupMemberStatus.ACTIVE).joinedAt(SEOUL_NOW.minusDays(1)).build();
            entityManager.persist(membership);
            return membership;
        });
    }

    private Long insertSchedule(Long groupId) {
        String title = "정산 일정 " + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO schedule (title, group_id, description, starts_at, ends_at,
                                      location_name, location_address)
                VALUES (?, ?, '설명', '2026-10-20 09:00:00', '2026-10-20 10:00:00', '장소', '주소')
                """, title, groupId);
        return jdbcTemplate.queryForObject("SELECT id FROM schedule WHERE title = ?", Long.class, title);
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

    private void assertRejected(GeneralException exception, BaseErrorCode expectedCode) {
        assertAll(
                () -> assertEquals(expectedCode, exception.getCode()),
                () -> assertEquals(0, settlementCount())
        );
    }

    private int settlementCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlement WHERE group_id = ?", Integer.class, group.getId());
    }
}
