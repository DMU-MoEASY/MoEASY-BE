package com.moeasy.moeasybe.domain.settlement.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentSource;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentType;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SettlementPaymentMappingRepositoryTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 29, 14, 0, 0, 123456000);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @ValueSource(longs = {1L, Long.MAX_VALUE})
    @DisplayName("수동 납부는 금액 경계·실제 입금 시각·청구 대상과 다른 등록자를 보존한다")
    void persist_manualPayment_roundTripsMapping(long amount) {
        // given
        Fixture fixture = persistFixture();
        SettlementPayment payment = buildPayment(fixture, amount);

        // when
        entityManager.persist(payment);
        entityManager.flush();
        entityManager.clear();
        SettlementPayment reloaded = entityManager.find(SettlementPayment.class, payment.getId());
        SettlementItem item = reloaded.getSettlementItem();

        // then
        assertAll(
                () -> assertEquals(fixture.item().getId(), item.getId()),
                () -> assertEquals(fixture.operator().getId(), reloaded.getRecordedByGroupMember().getId()),
                () -> assertNotEquals(item.getGroupMember().getId(), reloaded.getRecordedByGroupMember().getId()),
                () -> assertEquals(amount, reloaded.getAmount()),
                () -> assertEquals(OCCURRED_AT, reloaded.getOccurredAt()),
                () -> assertEquals(SettlementPaymentType.PAYMENT, reloaded.getPaymentType()),
                () -> assertEquals(SettlementPaymentSource.MANUAL, reloaded.getSource()),
                () -> assertEquals(SettlementPaymentStatus.COMPLETED, reloaded.getStatus()),
                () -> assertEquals(0L, item.getVersion()),
                () -> assertNull(reloaded.getAccountTransactionId()),
                () -> assertNull(reloaded.getCancelledByGroupMember()),
                () -> assertNull(reloaded.getCancelledAt()),
                () -> assertNull(reloaded.getCancelReason()),
                () -> assertNotNull(reloaded.getCreatedAt()),
                () -> assertNotNull(reloaded.getUpdatedAt())
        );
    }

    @Test
    @DisplayName("취소 감사 정보는 원본 납부와 별도로 저장되고 등록 금액·입금 시각은 유지된다")
    void find_cancelledPayment_preservesOriginalAndCancellationAudit() {
        // given
        Fixture fixture = persistFixture();
        SettlementPayment payment = persistPayment(fixture);
        LocalDateTime cancelledAt = LocalDateTime.of(2026, 10, 29, 15, 0, 0, 654321000);
        setCancellationFixture(payment, fixture.operator(), cancelledAt);
        entityManager.clear();

        // when
        SettlementPayment reloaded = entityManager.find(SettlementPayment.class, payment.getId());

        // then
        assertAll(
                () -> assertEquals(SettlementPaymentStatus.CANCELLED, reloaded.getStatus()),
                () -> assertEquals(fixture.operator().getId(), reloaded.getCancelledByGroupMember().getId()),
                () -> assertEquals(cancelledAt, reloaded.getCancelledAt()),
                () -> assertEquals("중복 입력 정정", reloaded.getCancelReason()),
                () -> assertEquals(4000L, reloaded.getAmount()),
                () -> assertEquals(OCCURRED_AT, reloaded.getOccurredAt()),
                () -> assertEquals(fixture.operator().getId(), reloaded.getRecordedByGroupMember().getId())
        );
    }

    @Test
    @DisplayName("기존 기록의 알 수 없는 등록자·입금 시각·취소 감사 정보는 null로 읽을 수 있다")
    void find_legacyCancelledPayment_allowsUnknownAudit() {
        // given
        Fixture fixture = persistFixture();
        jdbcTemplate.update("""
                INSERT INTO settlement_payment
                    (settlement_item_id, payment_type, status, source, amount)
                VALUES (?, 'PAYMENT', 'CANCELLED', 'MANUAL', 4000)
                """, fixture.item().getId());

        // when
        SettlementPayment reloaded = entityManager.createQuery(
                "select p from SettlementPayment p where p.settlementItem.id = :itemId", SettlementPayment.class)
                .setParameter("itemId", fixture.item().getId()).getSingleResult();

        // then
        assertAll(
                () -> assertEquals(SettlementPaymentStatus.CANCELLED, reloaded.getStatus()),
                () -> assertNull(reloaded.getRecordedByGroupMember()),
                () -> assertNull(reloaded.getOccurredAt()),
                () -> assertNull(reloaded.getCancelledByGroupMember()),
                () -> assertNull(reloaded.getCancelledAt()),
                () -> assertNull(reloaded.getCancelReason())
        );
    }

    @ParameterizedTest
    @CsvSource({"PAYMENT, PENDING, MANUAL", "PAYMENT, FAILED, BANK", "REFUND, COMPLETED, BANK"})
    @DisplayName("기존 스키마의 종류·처리 상태와 은행 출처도 Enum으로 읽을 수 있다")
    void find_existingPaymentValues_mapsEnums(String type, String status, String source) {
        // given
        Fixture fixture = persistFixture();
        jdbcTemplate.update("""
                INSERT INTO settlement_payment
                    (settlement_item_id, payment_type, status, source, amount)
                VALUES (?, ?, ?, ?, 4000)
                """, fixture.item().getId(), type, status, source);

        // when
        SettlementPayment reloaded = entityManager.createQuery(
                "select p from SettlementPayment p where p.settlementItem.id = :itemId", SettlementPayment.class)
                .setParameter("itemId", fixture.item().getId()).getSingleResult();

        // then
        assertAll(
                () -> assertEquals(SettlementPaymentType.valueOf(type), reloaded.getPaymentType()),
                () -> assertEquals(SettlementPaymentStatus.valueOf(status), reloaded.getStatus()),
                () -> assertEquals(SettlementPaymentSource.valueOf(source), reloaded.getSource())
        );
    }

    @Test
    @DisplayName("최종 납부 스키마에는 불필요한 멱등 키 컬럼과 유니크 인덱스가 없다")
    void migrate_paymentSchema_removesUnusedKeyAndIndex() {
        // given
        String table = "settlement_payment";

        // when
        Integer columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'idempotency_key'
                """, Integer.class, table);
        Integer indexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ?
                    AND index_name = 'uq_settlement_payment_idempotency_key'
                """, Integer.class, table);

        // then
        assertAll(() -> assertEquals(0, columnCount), () -> assertEquals(0, indexCount));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "9223372036854775808"})
    @DisplayName("납부액은 양수 Long 범위를 벗어나면 DB가 거부한다")
    void update_outOfRangeAmount_rejectedByCheckConstraint(String amount) {
        // given
        SettlementPayment payment = persistPayment(persistFixture());

        // when
        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("UPDATE settlement_payment SET amount = ? WHERE id = ?",
                        amount, payment.getId()));

        // then
        assertTrue(exception.getMostSpecificCause().getMessage().contains("ck_settlement_payment_amount"));
    }

    @ParameterizedTest
    @CsvSource({
            "source, UNKNOWN, ck_settlement_payment_source",
            "payment_type, UNKNOWN, ck_settlement_payment_type",
            "status, UNKNOWN, ck_settlement_payment_status"
    })
    @DisplayName("정의하지 않은 납부 종류·상태·출처는 DB가 거부한다")
    void update_unknownEnum_rejectedByCheckConstraint(String column, String value, String constraint) {
        // given
        SettlementPayment payment = persistPayment(persistFixture());

        // when
        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("UPDATE settlement_payment SET " + column + " = ? WHERE id = ?",
                        value, payment.getId()));

        // then
        assertTrue(exception.getMostSpecificCause().getMessage().contains(constraint));
    }

    @ParameterizedTest
    @CsvSource({
            "recorded_by_group_member_id, fk_settlement_payment_recorder",
            "cancelled_by_group_member_id, fk_settlement_payment_canceller",
            "account_transaction_id, fk_settlement_payment_transaction"
    })
    @DisplayName("존재하지 않는 처리자나 은행 거래 연결은 DB가 거부한다")
    void update_missingReference_rejectedByForeignKey(String column, String constraint) {
        // given
        Fixture fixture = persistFixture();
        SettlementPayment payment = persistPayment(fixture);
        if (column.equals("cancelled_by_group_member_id")) {
            // 감사 필드 제약을 만족시킨 상태에서 취소자 외래키만 위반한다.
            setCancellationFixture(payment, fixture.operator(), OCCURRED_AT.plusHours(1));
        }

        // when
        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("UPDATE settlement_payment SET " + column + " = ? WHERE id = ?",
                        Long.MAX_VALUE, payment.getId()));

        // then
        assertTrue(exception.getMostSpecificCause().getMessage().contains(constraint));
    }

    @Test
    @DisplayName("취소 사유만 있는 불완전한 감사 정보는 DB가 거부한다")
    void update_incompleteCancellation_rejectedByCheckConstraint() {
        // given
        SettlementPayment payment = persistPayment(persistFixture());

        // when
        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("UPDATE settlement_payment SET cancel_reason = ? WHERE id = ?",
                        "취소", payment.getId()));

        // then
        assertTrue(exception.getMostSpecificCause().getMessage().contains("ck_settlement_payment_cancel_audit"));
    }

    @Test
    @DisplayName("청구 버전은 음수로 저장할 수 없다")
    void update_negativeVersion_rejectedByCheckConstraint() {
        // given
        Fixture fixture = persistFixture();

        // when
        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("UPDATE settlement_item SET version = -1 WHERE id = ?",
                        fixture.item().getId()));

        // then
        assertTrue(exception.getMostSpecificCause().getMessage().contains("ck_settlement_item_version"));
    }

    private void setCancellationFixture(SettlementPayment payment, GroupMember operator, LocalDateTime cancelledAt) {
        // 취소 기능 도입 전 매핑을 검증하는 픽스처다. 날짜는 JPA 바인딩으로 저장한다.
        entityManager.createQuery("""
                update SettlementPayment p
                set p.status = :status, p.cancelledByGroupMember = :operator,
                    p.cancelledAt = :cancelledAt, p.cancelReason = :reason
                where p.id = :id
                """)
                .setParameter("status", SettlementPaymentStatus.CANCELLED)
                .setParameter("operator", operator)
                .setParameter("cancelledAt", cancelledAt)
                .setParameter("reason", "중복 입력 정정")
                .setParameter("id", payment.getId())
                .executeUpdate();
    }

    private SettlementPayment buildPayment(Fixture fixture, long amount) {
        return SettlementPayment.builder().settlementItem(fixture.item()).amount(amount)
                .occurredAt(OCCURRED_AT).recordedByGroupMember(fixture.operator())
                .build();
    }

    private SettlementPayment persistPayment(Fixture fixture) {
        SettlementPayment payment = buildPayment(fixture, 4000L);
        entityManager.persist(payment);
        entityManager.flush();
        return payment;
    }

    private Fixture persistFixture() {
        Category category = entityManager.createQuery("select c from Category c where c.code = :code", Category.class)
                .setParameter("code", "RUNNING").getSingleResult();
        Group group = Group.builder().category(category).name("납부 테스트 모임").description("설명")
                .regionCode("11680").placeName("장소").address("서울").maxMembers(10)
                .joinPolicy(GroupJoinPolicy.APPROVAL).build();
        entityManager.persist(group);
        GroupMember operator = persistMember(group, GroupMemberRole.OWNER);
        GroupMember target = persistMember(group, GroupMemberRole.MEMBER);
        Settlement settlement = Settlement.builder().group(group).createdByGroupMember(operator)
                .title("회비").totalAmount(Long.MAX_VALUE).splitMode(SettlementSplitMode.EQUAL).build();
        entityManager.persist(settlement);
        SettlementItem item = SettlementItem.builder().settlement(settlement).groupMember(target)
                .amount(Long.MAX_VALUE).build();
        entityManager.persist(item);
        entityManager.flush();
        return new Fixture(item, operator);
    }

    private GroupMember persistMember(Group group, GroupMemberRole role) {
        Member member = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).build();
        entityManager.persist(member);
        GroupMember groupMember = GroupMember.builder().group(group).member(member).role(role)
                .status(GroupMemberStatus.ACTIVE).joinedAt(OCCURRED_AT).build();
        entityManager.persist(groupMember);
        return groupMember;
    }

    private record Fixture(SettlementItem item, GroupMember operator) {
    }
}
