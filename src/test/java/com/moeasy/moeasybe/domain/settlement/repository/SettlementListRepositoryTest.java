package com.moeasy.moeasybe.domain.settlement.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moeasy.moeasybe.domain.group.entity.Category;
import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.settlement.entity.Settlement;
import com.moeasy.moeasybe.domain.settlement.entity.SettlementItem;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SettlementListRepositoryTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 20, 9, 0);

    @Autowired private SettlementRepository settlementRepository;
    @Autowired private SettlementItemRepository itemRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private GroupMember owner;

    @BeforeEach
    void setUp() {
        owner = persistMembership(persistGroup());
    }

    @ParameterizedTest
    @CsvSource({"0, 2, 2, true", "1, 2, 1, false", "2, 2, 0, false"})
    @DisplayName("삭제·다른 모임의 정산을 제외하고 생성 시각·ID 내림차순과 전체 페이지 수를 유지한다")
    void findPageByGroup_pagination_returnsSortedVisibleSettlements(
            int pageNumber, int pageSize, int expectedSize, boolean hasNext) {
        // given
        Settlement older = persistSettlement(owner, null, SettlementStatus.OPEN, CREATED_AT.minusDays(1));
        Settlement newer = persistSettlement(owner, null, SettlementStatus.CLOSED, CREATED_AT);
        Settlement tied = persistSettlement(owner, null, SettlementStatus.CANCELLED, CREATED_AT);
        Settlement deleted = persistSettlement(owner, null, SettlementStatus.OPEN, CREATED_AT.plusDays(1));
        jdbcTemplate.update("UPDATE settlement SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", deleted.getId());
        persistSettlement(persistMembership(persistGroup()), null, SettlementStatus.OPEN, CREATED_AT.plusDays(1));
        entityManager.flush();
        entityManager.clear();
        List<Long> expectedIds = List.of(tied.getId(), newer.getId(), older.getId()).stream()
                .skip((long) pageNumber * pageSize).limit(pageSize).toList();

        // when
        Page<Settlement> result = settlementRepository.findPageByGroup(
                owner.getGroup().getId(), null, null, pageRequest(pageNumber, pageSize));

        // then
        assertAll(
                () -> assertEquals(expectedIds, result.getContent().stream().map(Settlement::getId).toList()),
                () -> assertEquals(expectedSize, result.getNumberOfElements()),
                () -> assertEquals(3, result.getTotalElements()),
                () -> assertEquals(2, result.getTotalPages()),
                () -> assertEquals(pageNumber, result.getNumber()),
                () -> assertEquals(hasNext, result.hasNext())
        );
    }

    @ParameterizedTest
    @EnumSource(SettlementStatus.class)
    @DisplayName("상태와 일정 필터는 동시에 적용하고 전체 건수에도 같은 조건을 사용한다")
    void findPageByGroup_combinedFilters_countsOnlyMatchingSettlements(SettlementStatus status) {
        // given
        Long scheduleId = insertSchedule(owner.getGroup().getId());
        Long anotherScheduleId = insertSchedule(owner.getGroup().getId());
        for (SettlementStatus value : SettlementStatus.values()) {
            persistSettlement(owner, scheduleId, value, CREATED_AT);
            persistSettlement(owner, anotherScheduleId, value, CREATED_AT);
            persistSettlement(owner, null, value, CREATED_AT);
        }
        Settlement extra = persistSettlement(owner, scheduleId, status, CREATED_AT.plusHours(1));
        entityManager.flush();
        entityManager.clear();

        // when
        Page<Settlement> result = settlementRepository.findPageByGroup(
                owner.getGroup().getId(), status, scheduleId, pageRequest(0, 1));

        // then
        assertAll(
                () -> assertEquals(List.of(extra.getId()), result.getContent().stream().map(Settlement::getId).toList()),
                () -> assertEquals(2, result.getTotalElements()),
                () -> assertEquals(2, result.getTotalPages()),
                () -> assertTrue(result.hasNext())
        );
    }

    @Test
    @DisplayName("상태 필터만 사용하면 일정 연결 여부와 관계없이 같은 상태의 정산을 조회한다")
    void findPageByGroup_statusOnly_includesLinkedAndUnlinkedSettlements() {
        // given
        Long scheduleId = insertSchedule(owner.getGroup().getId());
        Settlement linked = persistSettlement(owner, scheduleId, SettlementStatus.OPEN, CREATED_AT);
        Settlement unlinked = persistSettlement(owner, null, SettlementStatus.OPEN, CREATED_AT);
        persistSettlement(owner, null, SettlementStatus.CLOSED, CREATED_AT);
        entityManager.flush();
        entityManager.clear();

        // when
        Page<Settlement> result = settlementRepository.findPageByGroup(
                owner.getGroup().getId(), SettlementStatus.OPEN, null, pageRequest(0, 20));

        // then
        assertEquals(List.of(unlinked.getId(), linked.getId()),
                result.getContent().stream().map(Settlement::getId).toList());
    }

    @Test
    @DisplayName("정산이 없으면 빈 목록과 0건의 페이지 정보를 반환한다")
    void findPageByGroup_noSettlements_returnsEmptyPage() {
        // given
        entityManager.flush();
        entityManager.clear();

        // when
        Page<Settlement> result = settlementRepository.findPageByGroup(
                owner.getGroup().getId(), null, null, pageRequest(0, 20));

        // then
        assertAll(
                () -> assertTrue(result.isEmpty()),
                () -> assertEquals(0, result.getTotalElements()),
                () -> assertEquals(0, result.getTotalPages()),
                () -> assertFalse(result.hasNext())
        );
    }

    @Test
    @DisplayName("청구 인원수는 요청한 정산만 일괄 집계하고 탈퇴·삭제된 대상도 포함한다")
    void countTargetsBySettlementIds_historicalTargets_countsStoredItems() {
        // given
        GroupMember left = persistMembership(owner.getGroup());
        GroupMember deleted = persistMembership(owner.getGroup());
        Settlement first = persistSettlement(owner, null, SettlementStatus.OPEN, CREATED_AT);
        Settlement second = persistSettlement(owner, null, SettlementStatus.OPEN, CREATED_AT);
        Settlement outside = persistSettlement(owner, null, SettlementStatus.OPEN, CREATED_AT);
        persistItem(first, owner);
        persistItem(first, left);
        persistItem(first, deleted);
        persistItem(second, owner);
        persistItem(outside, owner);
        entityManager.flush();
        jdbcTemplate.update("UPDATE group_member SET status = 'LEFT' WHERE id = ?", left.getId());
        jdbcTemplate.update("UPDATE member SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", deleted.getMember().getId());
        jdbcTemplate.update("UPDATE group_member SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", deleted.getId());
        entityManager.clear();

        // when
        Map<Long, SettlementItemRepository.TargetCount> counts = itemRepository
                .countTargetsBySettlementIds(List.of(first.getId(), second.getId())).stream()
                .collect(Collectors.toMap(SettlementItemRepository.TargetCount::getSettlementId, Function.identity()));

        // then
        assertAll(
                () -> assertEquals(2, counts.size()),
                () -> assertEquals(3, counts.get(first.getId()).getTargetCount()),
                () -> assertEquals(1, counts.get(second.getId()).getTargetCount()),
                () -> assertFalse(counts.containsKey(outside.getId()))
        );
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private Settlement persistSettlement(GroupMember creator, Long scheduleId,
            SettlementStatus status, LocalDateTime createdAt) {
        Settlement settlement = Settlement.builder().group(creator.getGroup()).createdByGroupMember(creator)
                .title("정산").scheduleId(scheduleId).totalAmount(1000L).splitMode(SettlementSplitMode.EQUAL).build();
        entityManager.persist(settlement);
        entityManager.flush();
        jdbcTemplate.update("UPDATE settlement SET status = ?, created_at = ? WHERE id = ?",
                status.name(), createdAt, settlement.getId());
        return settlement;
    }

    private void persistItem(Settlement settlement, GroupMember member) {
        entityManager.persist(SettlementItem.builder().settlement(settlement).groupMember(member).amount(100L).build());
    }

    private Group persistGroup() {
        Category category = entityManager.createQuery("select c from Category c where c.code = :code", Category.class)
                .setParameter("code", "RUNNING").getSingleResult();
        Group group = Group.builder().category(category).name("목록 " + UUID.randomUUID())
                .description("설명").regionCode("11680").placeName("장소").address("주소")
                .maxMembers(20).joinPolicy(GroupJoinPolicy.APPROVAL).build();
        entityManager.persist(group);
        return group;
    }

    private GroupMember persistMembership(Group group) {
        Member member = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).build();
        entityManager.persist(member);
        GroupMember membership = GroupMember.builder().group(group).member(member).role(GroupMemberRole.OWNER)
                .status(GroupMemberStatus.ACTIVE).joinedAt(CREATED_AT.minusDays(1)).build();
        entityManager.persist(membership);
        return membership;
    }

    private Long insertSchedule(Long groupId) {
        entityManager.flush();
        String title = "목록 일정 " + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO schedule (title, group_id, description, starts_at, ends_at, location_name, location_address)
                VALUES (?, ?, '설명', '2026-10-20 09:00:00', '2026-10-20 10:00:00', '장소', '주소')
                """, title, groupId);
        return jdbcTemplate.queryForObject("SELECT id FROM schedule WHERE title = ?", Long.class, title);
    }
}
