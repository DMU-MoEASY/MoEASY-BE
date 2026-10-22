package com.moeasy.moeasybe.domain.settlement.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moeasy.moeasybe.domain.group.entity.Category;
import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementItemStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementStatus;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SettlementMappingRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @EnumSource(SettlementSplitMode.class)
    @DisplayName("정산과 청구는 최대 Long 금액·분할 방식·연관 관계·기본 상태를 보존한다")
    void persist_validSettlement_roundTripsMapping(SettlementSplitMode splitMode) {
        // given
        GroupMember creator = persistGroupMember();
        LocalDateTime dueAt = LocalDateTime.of(2026, 12, 31, 23, 59, 59);
        Settlement settlement = Settlement.builder()
                .group(creator.getGroup()).createdByGroupMember(creator)
                .title("모임 회비").description("운영비").dueAt(dueAt)
                .totalAmount(Long.MAX_VALUE).splitMode(splitMode).build();
        entityManager.persist(settlement);
        SettlementItem item = SettlementItem.builder()
                .settlement(settlement).groupMember(creator).amount(Long.MAX_VALUE).build();

        // when
        entityManager.persist(item);
        entityManager.flush();
        entityManager.clear();
        Settlement reloaded = entityManager.find(Settlement.class, settlement.getId());
        SettlementItem reloadedItem = entityManager.find(SettlementItem.class, item.getId());

        // then
        assertAll(
                () -> assertEquals(creator.getGroup().getId(), reloaded.getGroup().getId()),
                () -> assertEquals(creator.getId(), reloaded.getCreatedByGroupMember().getId()),
                () -> assertEquals("모임 회비", reloaded.getTitle()),
                () -> assertEquals("운영비", reloaded.getDescription()),
                () -> assertEquals(dueAt, reloaded.getDueAt()),
                () -> assertEquals(Long.MAX_VALUE, reloaded.getTotalAmount()),
                () -> assertEquals(splitMode, reloaded.getSplitMode()),
                () -> assertEquals(SettlementStatus.OPEN, reloaded.getStatus()),
                () -> assertNull(reloaded.getScheduleId()),
                () -> assertNull(reloaded.getFeeRuleId()),
                () -> assertNull(reloaded.getDeletedAt()),
                () -> assertNotNull(reloaded.getCreatedAt()),
                () -> assertNotNull(reloaded.getUpdatedAt()),
                () -> assertEquals(settlement.getId(), reloadedItem.getSettlement().getId()),
                () -> assertEquals(creator.getId(), reloadedItem.getGroupMember().getId()),
                () -> assertEquals(Long.MAX_VALUE, reloadedItem.getAmount()),
                () -> assertEquals(SettlementItemStatus.PENDING, reloadedItem.getStatus()),
                () -> assertNotNull(reloadedItem.getCreatedAt()),
                () -> assertNotNull(reloadedItem.getUpdatedAt()),
                () -> assertEquals(0, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM settlement_payment WHERE settlement_item_id = ?",
                        Integer.class, item.getId()))
        );
    }

    @Test
    @DisplayName("같은 정산에서 같은 모임 회원을 두 번 청구하면 DB가 거부한다")
    void insert_duplicateTarget_rejectedByUniqueConstraint() {
        // given
        SettlementItem item = persistSettlementItem();

        // when
        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("""
                        INSERT INTO settlement_item (settlement_id, group_member_id, amount)
                        VALUES (?, ?, ?)
                        """, item.getSettlement().getId(), item.getGroupMember().getId(), 1000L));

        // then
        assertTrue(exception.getMostSpecificCause().getMessage().contains("uq_settlement_item_member"));
    }

    @ParameterizedTest
    @CsvSource({
            "settlement, total_amount, 0, ck_settlement_total_amount",
            "settlement, total_amount, 9223372036854775808, ck_settlement_total_amount",
            "settlement_item, amount, 0, ck_settlement_item_amount",
            "settlement_item, amount, 9223372036854775808, ck_settlement_item_amount"
    })
    @DisplayName("총액과 청구액은 양수 Long 범위를 벗어나면 DB가 거부한다")
    void update_outOfRangeAmount_rejectedByCheckConstraint(
            String table, String column, String amount, String constraint) {
        // given
        SettlementItem item = persistSettlementItem();
        Long id = table.equals("settlement") ? item.getSettlement().getId() : item.getId();

        // when
        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(
                        "UPDATE " + table + " SET " + column + " = ? WHERE id = ?", amount, id));

        // then
        assertAll(
                () -> assertEquals(3819, ((SQLException) exception.getMostSpecificCause()).getErrorCode()),
                () -> assertTrue(exception.getMostSpecificCause().getMessage().contains(constraint))
        );
    }

    @Test
    @DisplayName("정의하지 않은 분할 방식은 DB가 거부한다")
    void update_unknownSplitMode_rejectedByCheckConstraint() {
        // given
        SettlementItem item = persistSettlementItem();

        // when
        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("UPDATE settlement SET split_mode = ? WHERE id = ?",
                        "UNKNOWN", item.getSettlement().getId()));

        // then
        assertAll(
                () -> assertEquals(3819, ((SQLException) exception.getMostSpecificCause()).getErrorCode()),
                () -> assertTrue(exception.getMostSpecificCause().getMessage().contains("ck_settlement_split_mode"))
        );
    }

    @ParameterizedTest
    @CsvSource({
            "settlement, DRAFT, ck_settlement_status",
            "settlement_item, PARTIALLY_PAID, ck_settlement_item_status"
    })
    @DisplayName("지원하지 않는 초안·부분 납부 전용 상태는 DB가 거부한다")
    void update_removedStatus_rejectedByCheckConstraint(String table, String status, String constraint) {
        // given
        SettlementItem item = persistSettlementItem();
        Long id = table.equals("settlement") ? item.getSettlement().getId() : item.getId();

        // when
        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("UPDATE " + table + " SET status = ? WHERE id = ?", status, id));

        // then
        assertAll(
                () -> assertEquals(3819, ((SQLException) exception.getMostSpecificCause()).getErrorCode()),
                () -> assertTrue(exception.getMostSpecificCause().getMessage().contains(constraint))
        );
    }

    private SettlementItem persistSettlementItem() {
        GroupMember creator = persistGroupMember();
        Settlement settlement = Settlement.builder()
                .group(creator.getGroup()).createdByGroupMember(creator)
                .title("정산").totalAmount(1000L).splitMode(SettlementSplitMode.EQUAL).build();
        entityManager.persist(settlement);
        SettlementItem item = SettlementItem.builder()
                .settlement(settlement).groupMember(creator).amount(1000L).build();
        entityManager.persist(item);
        entityManager.flush();
        return item;
    }

    private GroupMember persistGroupMember() {
        Member member = Member.builder()
                .socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).build();
        entityManager.persist(member);
        Category category = entityManager.createQuery(
                "select c from Category c where c.code = :code", Category.class)
                .setParameter("code", "RUNNING").getSingleResult();
        Group group = Group.builder().category(category).name("정산 테스트 모임").description("설명")
                .regionCode("11680").placeName("장소").address("서울")
                .maxMembers(10).joinPolicy(GroupJoinPolicy.APPROVAL).build();
        entityManager.persist(group);
        GroupMember creator = GroupMember.builder().group(group).member(member)
                .role(GroupMemberRole.OWNER).status(GroupMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.of(2026, 10, 1, 0, 0)).build();
        entityManager.persist(creator);
        return creator;
    }
}
