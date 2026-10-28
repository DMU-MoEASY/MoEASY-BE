package com.moeasy.moeasybe.domain.settlement.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SettlementDetailRepositoryTest {

    @Autowired private SettlementRepository settlementRepository;
    @Autowired private SettlementItemRepository itemRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @EnumSource(SettlementStatus.class)
    @DisplayName("모든 정산 상태를 조회하고 삭제·탈퇴 대상도 닉네임과 함께 ID 순서로 로딩한다")
    void findDetail_historicalMembers_fetchesCreatorAndSortedItems(SettlementStatus status) {
        // given
        Group group = persistGroup();
        GroupMember creator = persistMember(group);
        GroupMember target = persistMember(group);
        Settlement settlement = persistSettlement(creator);
        entityManager.persist(SettlementItem.builder().settlement(settlement).groupMember(target).amount(200L).build());
        entityManager.persist(SettlementItem.builder().settlement(settlement).groupMember(creator).amount(100L).build());
        Settlement outside = persistSettlement(creator);
        entityManager.persist(SettlementItem.builder().settlement(outside).groupMember(target).amount(300L).build());
        entityManager.flush();
        jdbcTemplate.update("UPDATE settlement SET status = ? WHERE id = ?", status.name(), settlement.getId());
        jdbcTemplate.update("UPDATE member SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", creator.getMember().getId());
        jdbcTemplate.update("UPDATE group_member SET status = 'LEFT', deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                target.getId());
        entityManager.clear();

        // when
        Settlement found = settlementRepository.findByIdAndGroupIdAndDeletedAtIsNull(settlement.getId(), group.getId())
                .orElseThrow();
        List<SettlementItem> items = itemRepository.findAllBySettlementIdOrderByGroupMemberIdAsc(settlement.getId());

        // then
        var persistence = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        assertAll(
                () -> assertEquals(status, found.getStatus()),
                () -> assertTrue(persistence.isLoaded(found, "createdByGroupMember")),
                () -> assertTrue(persistence.isLoaded(found.getCreatedByGroupMember(), "member")),
                () -> assertEquals(creator.getMember().getNickname(), found.getCreatedByGroupMember().getMember().getNickname()),
                () -> assertEquals(List.of(creator.getId(), target.getId()),
                        items.stream().map(item -> item.getGroupMember().getId()).toList()),
                () -> assertEquals(List.of(100L, 200L), items.stream().map(SettlementItem::getAmount).toList()),
                () -> assertTrue(items.stream().allMatch(item -> persistence.isLoaded(item.getGroupMember(), "member")))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "DELETED", "OTHER_GROUP"})
    @DisplayName("정산 없음·삭제·다른 모임의 정산은 같은 조건으로 조회에서 제외한다")
    void findDetail_unavailableSettlement_returnsEmpty(String condition) {
        // given
        GroupMember creator = persistMember(persistGroup());
        Settlement settlement = persistSettlement(creator);
        Long settlementId = condition.equals("MISSING") ? Long.MAX_VALUE : settlement.getId();
        Long groupId = condition.equals("OTHER_GROUP") ? persistGroup().getId() : creator.getGroup().getId();
        entityManager.flush();
        if (condition.equals("DELETED")) {
            jdbcTemplate.update("UPDATE settlement SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", settlement.getId());
        }
        entityManager.clear();

        // when
        var result = settlementRepository.findByIdAndGroupIdAndDeletedAtIsNull(settlementId, groupId);

        // then
        assertTrue(result.isEmpty());
    }

    private Settlement persistSettlement(GroupMember creator) {
        Settlement settlement = Settlement.builder().group(creator.getGroup()).createdByGroupMember(creator)
                .title("정산").totalAmount(300L).splitMode(SettlementSplitMode.CUSTOM).build();
        entityManager.persist(settlement);
        return settlement;
    }

    private Group persistGroup() {
        Category category = entityManager.createQuery("select c from Category c where c.code = :code", Category.class)
                .setParameter("code", "RUNNING").getSingleResult();
        Group group = Group.builder().category(category).name("상세 " + UUID.randomUUID())
                .description("설명").regionCode("11680").placeName("장소").address("주소")
                .maxMembers(20).joinPolicy(GroupJoinPolicy.APPROVAL).build();
        entityManager.persist(group);
        return group;
    }

    private GroupMember persistMember(Group group) {
        String key = UUID.randomUUID().toString();
        Member member = Member.builder().socialType(SocialType.KAKAO).socialId(key).nickname("상세" + key).build();
        entityManager.persist(member);
        GroupMember membership = GroupMember.builder().group(group).member(member).role(GroupMemberRole.OWNER)
                .status(GroupMemberStatus.ACTIVE).joinedAt(LocalDateTime.of(2026, 10, 20, 9, 0)).build();
        entityManager.persist(membership);
        return membership;
    }
}
