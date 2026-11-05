package com.moeasy.moeasybe.domain.settlement.service.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.dto.response.SettlementResDTO;
import com.moeasy.moeasybe.domain.settlement.entity.Settlement;
import com.moeasy.moeasybe.domain.settlement.entity.SettlementItem;
import com.moeasy.moeasybe.domain.settlement.entity.SettlementPayment;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementItemStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentSource;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentType;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementPaymentRepository;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SettlementPaymentQueryServiceIntegrationTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 29, 14, 5);
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 29, 14, 0);
    private static final LocalDateTime CANCELLED_AT = LocalDateTime.of(2026, 10, 29, 15, 0);

    @Autowired private SettlementQueryService service;
    @Autowired private SettlementPaymentRepository paymentRepository;
    @Autowired private EntityManager entityManager;

    private Group group;
    private GroupMember owner;
    private GroupMember manager;
    private GroupMember requester;
    private Settlement settlement;
    private SettlementItem item;

    @BeforeEach
    void setUp() {
        group = persistGroup();
        owner = persistMember(group, GroupMemberRole.OWNER);
        manager = persistMember(group, GroupMemberRole.MANAGER);
        requester = persistMember(group, GroupMemberRole.MEMBER);
        settlement = persistSettlement(group, owner);
        item = persistItem(settlement, owner);
        entityManager.flush();
    }

    @Test
    @DisplayName("일반 회원도 폐쇄 모임의 취소·은행·실패 기록과 과거 등록자·취소자 정보를 조회한다")
    void getPaymentHistory_closedGroup_returnsHistoricalRecordsWithoutMutation() {
        // given
        SettlementPayment cancelled = persistPayment(item, OCCURRED_AT, owner, CREATED_AT.minusHours(1));
        cancelled.cancel(manager, CANCELLED_AT, "오등록 정정");
        SettlementPayment legacy = persistPayment(item, null, null, CREATED_AT);
        changePaymentKind(legacy, SettlementPaymentSource.BANK,
                SettlementPaymentType.PAYMENT, SettlementPaymentStatus.COMPLETED);
        SettlementPayment failedRefund = persistPayment(item, OCCURRED_AT.minusDays(1), manager, CREATED_AT);
        changePaymentKind(failedRefund, SettlementPaymentSource.BANK,
                SettlementPaymentType.REFUND, SettlementPaymentStatus.FAILED);
        entityManager.createQuery("update Group g set g.status = 'CLOSED', g.deletedAt = :at where g.id = :id")
                .setParameter("at", CANCELLED_AT).setParameter("id", group.getId()).executeUpdate();
        entityManager.createQuery("update Member m set m.deletedAt = :at where m.id = :id")
                .setParameter("at", CANCELLED_AT).setParameter("id", owner.getMember().getId()).executeUpdate();
        entityManager.createQuery("update GroupMember gm set gm.status = 'LEFT', gm.deletedAt = :at where gm.id in :ids")
                .setParameter("at", CANCELLED_AT).setParameter("ids", List.of(owner.getId(), manager.getId()))
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // when
        var result = service.getPaymentHistory(group.getId(), settlement.getId(), item.getId(),
                new SettlementReqDTO.PaymentSearch(null, null), requester.getMember().getId());

        // then
        var cancelledResult = result.items().get(2);
        var legacyResult = result.items().get(1);
        var failedResult = result.items().get(0);
        entityManager.flush();
        entityManager.clear();
        SettlementItem storedItem = entityManager.find(SettlementItem.class, item.getId());
        assertAll(
                () -> assertEquals(List.of(failedRefund.getId(), legacy.getId(), cancelled.getId()),
                        result.items().stream().map(SettlementResDTO.PaymentHistory::paymentId).toList()),
                () -> assertEquals(3, result.totalElements()),
                () -> assertEquals(SettlementPaymentStatus.FAILED, failedResult.status()),
                () -> assertEquals(SettlementPaymentType.REFUND, failedResult.paymentType()),
                () -> assertEquals(SettlementPaymentSource.BANK, legacyResult.source()),
                () -> assertNull(legacyResult.occurredAt()),
                () -> assertNull(legacyResult.recordedBy()),
                () -> assertNull(legacyResult.cancelledBy()),
                () -> assertNull(legacyResult.cancelledAt()),
                () -> assertNull(legacyResult.cancelReason()),
                () -> assertEquals(SettlementPaymentStatus.CANCELLED, cancelledResult.status()),
                () -> assertEquals(4000L, cancelledResult.amount()),
                () -> assertEquals(item.getId(), cancelledResult.settlementItemId()),
                () -> assertEquals(OCCURRED_AT, cancelledResult.occurredAt()),
                () -> assertEquals(CREATED_AT.minusHours(1), cancelledResult.createdAt()),
                () -> assertEquals(owner.getId(), cancelledResult.recordedBy().groupMemberId()),
                () -> assertEquals(owner.getMember().getNickname(), cancelledResult.recordedBy().nickname()),
                () -> assertEquals(manager.getId(), cancelledResult.cancelledBy().groupMemberId()),
                () -> assertEquals(manager.getMember().getNickname(), cancelledResult.cancelledBy().nickname()),
                () -> assertEquals(CANCELLED_AT, cancelledResult.cancelledAt()),
                () -> assertEquals("오등록 정정", cancelledResult.cancelReason()),
                () -> assertEquals(SettlementItemStatus.PENDING, storedItem.getStatus()),
                () -> assertEquals(0L, storedItem.getVersion())
        );
    }

    @Test
    @DisplayName("납부 이력은 해당 청구만 고정 순서로 페이징하고 범위 밖 페이지에도 전체 건수를 유지한다")
    void getPaymentHistory_pagination_returnsScopedPageAndEmptyPage() {
        // given
        persistPayment(item, OCCURRED_AT, owner, CREATED_AT);
        SettlementPayment middle = persistPayment(item, OCCURRED_AT, owner, CREATED_AT);
        persistPayment(item, OCCURRED_AT, owner, CREATED_AT.plusHours(1));
        persistPayment(persistItem(settlement, manager), OCCURRED_AT, manager, CREATED_AT.plusDays(1));
        entityManager.flush();
        entityManager.clear();

        // when
        var page = service.getPaymentHistory(group.getId(), settlement.getId(), item.getId(),
                new SettlementReqDTO.PaymentSearch(1, 1), requester.getMember().getId());
        var empty = service.getPaymentHistory(group.getId(), settlement.getId(), item.getId(),
                new SettlementReqDTO.PaymentSearch(3, 1), requester.getMember().getId());

        // then
        assertAll(
                () -> assertEquals(List.of(middle.getId()),
                        page.items().stream().map(SettlementResDTO.PaymentHistory::paymentId).toList()),
                () -> assertEquals(1, page.page()),
                () -> assertEquals(1, page.size()),
                () -> assertEquals(3, page.totalElements()),
                () -> assertEquals(3, page.totalPages()),
                () -> assertTrue(page.hasNext()),
                () -> assertTrue(empty.items().isEmpty()),
                () -> assertEquals(3, empty.totalElements()),
                () -> assertEquals(3, empty.totalPages()),
                () -> assertFalse(empty.hasNext())
        );
    }

    @Test
    @DisplayName("이력은 등록자·취소자의 회원 정보를 함께 로딩하고 등록자가 없는 기록도 유지한다")
    void findAllBySettlementItemId_actors_fetchesMembersTogether() {
        // given
        SettlementPayment payment = persistPayment(item, OCCURRED_AT, owner, CREATED_AT);
        payment.cancel(manager, CANCELLED_AT, "오등록 정정");
        persistPayment(item, null, null, CREATED_AT);
        entityManager.flush();
        entityManager.clear();

        // when
        var page = paymentRepository.findAllBySettlementItemId(item.getId(),
                PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "id")));

        // then
        var persistence = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        SettlementPayment found = page.getContent().getFirst();
        assertAll(
                () -> assertEquals(2, page.getTotalElements()),
                () -> assertTrue(persistence.isLoaded(found, "recordedByGroupMember")),
                () -> assertTrue(persistence.isLoaded(found, "cancelledByGroupMember")),
                () -> assertTrue(persistence.isLoaded(found.getRecordedByGroupMember(), "member")),
                () -> assertTrue(persistence.isLoaded(found.getCancelledByGroupMember(), "member"))
        );
    }

    @ParameterizedTest
    @CsvSource({
            "MISSING_GROUP, GROUP404_1",
            "OTHER_GROUP_SETTLEMENT, SETTLEMENT404_1",
            "DELETED_SETTLEMENT, SETTLEMENT404_1",
            "MISSING_ITEM, SETTLEMENT404_2",
            "OTHER_SETTLEMENT_ITEM, SETTLEMENT404_2",
            "OUTSIDER, AUTH403_1",
            "LEFT_REQUESTER, AUTH403_1",
            "DELETED_ACCOUNT, AUTH403_1"
    })
    @DisplayName("요청자 권한과 모임·정산·청구 소속을 검증하여 다른 청구의 이력을 노출하지 않는다")
    void getPaymentHistory_invalidScopeOrRequester_rejectsRequest(String condition, String expectedCode) {
        // given
        Long groupId = group.getId();
        Long settlementId = settlement.getId();
        Long itemId = item.getId();
        Long memberId = requester.getMember().getId();
        switch (condition) {
            case "MISSING_GROUP" -> groupId = Long.MAX_VALUE;
            case "OTHER_GROUP_SETTLEMENT" -> {
                Group otherGroup = persistGroup();
                settlementId = persistSettlement(otherGroup, persistMember(otherGroup, GroupMemberRole.OWNER)).getId();
            }
            case "DELETED_SETTLEMENT" -> entityManager.createQuery(
                    "update Settlement s set s.deletedAt = :at where s.id = :id")
                    .setParameter("at", CANCELLED_AT).setParameter("id", settlementId).executeUpdate();
            case "MISSING_ITEM" -> itemId = Long.MAX_VALUE;
            case "OTHER_SETTLEMENT_ITEM" -> itemId = persistItem(persistSettlement(group, owner), owner).getId();
            case "OUTSIDER" -> memberId = Long.MAX_VALUE;
            case "LEFT_REQUESTER" -> entityManager.createQuery(
                    "update GroupMember gm set gm.status = 'LEFT' where gm.id = :id")
                    .setParameter("id", requester.getId()).executeUpdate();
            case "DELETED_ACCOUNT" -> entityManager.createQuery(
                    "update Member m set m.deletedAt = :at where m.id = :id")
                    .setParameter("at", CANCELLED_AT).setParameter("id", memberId).executeUpdate();
            default -> throw new IllegalArgumentException(condition);
        }
        entityManager.flush();
        entityManager.clear();
        Long requestedGroupId = groupId;
        Long requestedSettlementId = settlementId;
        Long requestedItemId = itemId;
        Long requestedMemberId = memberId;

        // when
        GeneralException exception = assertThrows(GeneralException.class, () -> service.getPaymentHistory(
                requestedGroupId, requestedSettlementId, requestedItemId,
                new SettlementReqDTO.PaymentSearch(null, null), requestedMemberId));

        // then
        assertEquals(expectedCode, exception.getCode().getCode());
    }

    @Test
    @DisplayName("목록·상세 현황은 여러 납부의 중복 합산을 피하고 취소·실패·대기·환불을 제외한다")
    void getSettlementReads_mixedPayments_returnsConsistentHistoricalSummary() {
        // given
        SettlementItem paidItem = persistItem(settlement, manager);
        persistItem(settlement, requester);
        item.applyPayment(0L, 0L, 4000L);
        SettlementPayment cancelled = persistPayment(item, OCCURRED_AT, owner, CREATED_AT);
        item.applyPayment(1L, 4000L, 6000L);
        SettlementPayment bank = persistPayment(item, 6000L, OCCURRED_AT, owner, CREATED_AT);
        changePaymentKind(bank, SettlementPaymentSource.BANK,
                SettlementPaymentType.PAYMENT, SettlementPaymentStatus.COMPLETED);
        cancelled.cancel(manager, CANCELLED_AT, "오등록 정정");
        item.cancelPayment(2L, 10000L, 4000L);
        paidItem.applyPayment(0L, 0L, 2000L);
        persistPayment(paidItem, 2000L, OCCURRED_AT, owner, CREATED_AT);
        paidItem.applyPayment(1L, 2000L, 8000L);
        SettlementPayment paidBank = persistPayment(paidItem, 8000L, OCCURRED_AT, owner, CREATED_AT);
        changePaymentKind(paidBank, SettlementPaymentSource.BANK,
                SettlementPaymentType.PAYMENT, SettlementPaymentStatus.COMPLETED);
        for (SettlementPaymentStatus status : List.of(SettlementPaymentStatus.PENDING, SettlementPaymentStatus.FAILED)) {
            changePaymentKind(persistPayment(item, OCCURRED_AT, owner, CREATED_AT),
                    SettlementPaymentSource.BANK, SettlementPaymentType.PAYMENT, status);
        }
        changePaymentKind(persistPayment(item, OCCURRED_AT, owner, CREATED_AT),
                SettlementPaymentSource.MANUAL, SettlementPaymentType.REFUND, SettlementPaymentStatus.COMPLETED);
        Group outside = persistGroup();
        GroupMember outsider = persistMember(outside, GroupMemberRole.OWNER);
        persistPayment(persistItem(persistSettlement(outside, outsider), outsider), OCCURRED_AT, outsider, CREATED_AT);
        entityManager.createQuery("update Settlement s set s.totalAmount = 30000 where s.id = :id")
                .setParameter("id", settlement.getId()).executeUpdate();
        entityManager.createQuery("update Group g set g.status = 'CLOSED', g.deletedAt = :at where g.id = :id")
                .setParameter("at", CANCELLED_AT).setParameter("id", group.getId()).executeUpdate();
        entityManager.createQuery("update GroupMember gm set gm.status = 'LEFT', gm.deletedAt = :at where gm.id = :id")
                .setParameter("at", CANCELLED_AT).setParameter("id", manager.getId()).executeUpdate();
        entityManager.createQuery("update Member m set m.deletedAt = :at where m.id = :id")
                .setParameter("at", CANCELLED_AT).setParameter("id", owner.getMember().getId()).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // when
        var page = service.getSettlements(group.getId(), new SettlementReqDTO.Search(null, null, null, null),
                requester.getMember().getId());
        var detail = service.getSettlement(group.getId(), settlement.getId(), requester.getMember().getId());

        // then
        var summary = page.items().getFirst();
        var partial = detail.items().getFirst();
        var paid = detail.items().get(1);
        var unpaid = detail.items().get(2);
        entityManager.flush();
        entityManager.clear();
        assertAll(
                () -> assertEquals(1, page.items().size()),
                () -> assertEquals(30000L, summary.totalAmount()),
                () -> assertEquals(16000L, summary.paidAmount()),
                () -> assertEquals(14000L, summary.remainingAmount()),
                () -> assertEquals(1, summary.paidCount()),
                () -> assertEquals(2, summary.pendingCount()),
                () -> assertEquals(3, summary.targetCount()),
                () -> assertEquals(summary.paidAmount(), detail.paidAmount()),
                () -> assertEquals(summary.remainingAmount(), detail.remainingAmount()),
                () -> assertEquals(summary.paidCount(), detail.paidCount()),
                () -> assertEquals(summary.pendingCount(), detail.pendingCount()),
                () -> assertEquals(summary.targetCount(), detail.targetCount()),
                () -> assertEquals(6000L, partial.paidAmount()),
                () -> assertEquals(4000L, partial.remainingAmount()),
                () -> assertEquals(3L, partial.version()),
                () -> assertEquals(SettlementItemStatus.PENDING, partial.status()),
                () -> assertEquals(10000L, paid.paidAmount()),
                () -> assertEquals(0L, paid.remainingAmount()),
                () -> assertEquals(2L, paid.version()),
                () -> assertEquals(SettlementItemStatus.PAID, paid.status()),
                () -> assertEquals(0L, unpaid.paidAmount()),
                () -> assertEquals(10000L, unpaid.remainingAmount()),
                () -> assertEquals(0L, unpaid.version()),
                () -> assertEquals(3L, entityManager.find(SettlementItem.class, item.getId()).getVersion()),
                () -> assertEquals(2L, entityManager.find(SettlementItem.class, paidItem.getId()).getVersion()),
                () -> assertEquals(SettlementPaymentStatus.CANCELLED,
                        entityManager.find(SettlementPayment.class, cancelled.getId()).getStatus())
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, Long.MAX_VALUE})
    @DisplayName("현황 집계는 납부 이력이 없는 0과 Long 최대 완납액을 정확히 반환한다")
    void getSettlementReads_amountBoundary_preservesLongAndZero(long amount) {
        // given
        entityManager.createQuery("update Settlement s set s.totalAmount = :amount where s.id = :id")
                .setParameter("amount", Long.MAX_VALUE).setParameter("id", settlement.getId()).executeUpdate();
        entityManager.createQuery("update SettlementItem i set i.amount = :amount where i.id = :id")
                .setParameter("amount", Long.MAX_VALUE).setParameter("id", item.getId()).executeUpdate();
        entityManager.refresh(item);
        if (amount > 0) {
            item.applyPayment(0L, 0L, amount);
            persistPayment(item, amount, OCCURRED_AT, owner, CREATED_AT);
        }
        entityManager.flush();
        entityManager.clear();

        // when
        var summary = service.getSettlements(group.getId(), new SettlementReqDTO.Search(null, null, null, null),
                requester.getMember().getId()).items().getFirst();
        var detail = service.getSettlement(group.getId(), settlement.getId(), requester.getMember().getId());

        // then
        var target = detail.items().getFirst();
        assertAll(
                () -> assertEquals(amount, summary.paidAmount()),
                () -> assertEquals(Long.MAX_VALUE - amount, summary.remainingAmount()),
                () -> assertEquals(amount == 0 ? 0 : 1, summary.paidCount()),
                () -> assertEquals(amount == 0 ? 1 : 0, summary.pendingCount()),
                () -> assertEquals(summary.paidAmount(), detail.paidAmount()),
                () -> assertEquals(summary.remainingAmount(), detail.remainingAmount()),
                () -> assertEquals(amount, target.paidAmount()),
                () -> assertEquals(Long.MAX_VALUE - amount, target.remainingAmount()),
                () -> assertEquals(amount == 0 ? 0L : 1L, target.version())
        );
    }

    private Group persistGroup() {
        Category category = entityManager.createQuery("select c from Category c where c.code = 'RUNNING'", Category.class)
                .getSingleResult();
        Group created = Group.builder().category(category).name("납부 이력 " + UUID.randomUUID())
                .description("설명").regionCode("11680").placeName("장소").address("주소")
                .maxMembers(20).joinPolicy(GroupJoinPolicy.APPROVAL).build();
        entityManager.persist(created);
        return created;
    }

    private GroupMember persistMember(Group memberGroup, GroupMemberRole role) {
        String key = UUID.randomUUID().toString();
        Member member = Member.builder().socialType(SocialType.KAKAO).socialId(key).nickname("이력" + key).build();
        entityManager.persist(member);
        GroupMember membership = GroupMember.builder().group(memberGroup).member(member).role(role)
                .status(GroupMemberStatus.ACTIVE).joinedAt(OCCURRED_AT.minusDays(1)).build();
        entityManager.persist(membership);
        return membership;
    }

    private Settlement persistSettlement(Group memberGroup, GroupMember creator) {
        Settlement created = Settlement.builder().group(memberGroup).createdByGroupMember(creator)
                .title("정산").totalAmount(10000L).splitMode(SettlementSplitMode.EQUAL).build();
        entityManager.persist(created);
        return created;
    }

    private SettlementItem persistItem(Settlement parent, GroupMember target) {
        SettlementItem created = SettlementItem.builder().settlement(parent).groupMember(target).amount(10000L).build();
        entityManager.persist(created);
        return created;
    }

    private SettlementPayment persistPayment(SettlementItem targetItem, LocalDateTime occurredAt,
            GroupMember recorder, LocalDateTime createdAt) {
        return persistPayment(targetItem, 4000L, occurredAt, recorder, createdAt);
    }

    private SettlementPayment persistPayment(SettlementItem targetItem, long amount, LocalDateTime occurredAt,
            GroupMember recorder, LocalDateTime createdAt) {
        SettlementPayment payment = SettlementPayment.builder().settlementItem(targetItem)
                .amount(amount).occurredAt(occurredAt).recordedByGroupMember(recorder).build();
        entityManager.persist(payment);
        entityManager.flush();
        entityManager.createQuery("update SettlementPayment p set p.createdAt = :at where p.id = :id")
                .setParameter("at", createdAt).setParameter("id", payment.getId()).executeUpdate();
        entityManager.refresh(payment);
        return payment;
    }

    private void changePaymentKind(SettlementPayment payment, SettlementPaymentSource source,
            SettlementPaymentType type, SettlementPaymentStatus status) {
        entityManager.createQuery("""
                update SettlementPayment p set p.source = :source, p.paymentType = :type, p.status = :status
                where p.id = :id
                """)
                .setParameter("source", source).setParameter("type", type).setParameter("status", status)
                .setParameter("id", payment.getId()).executeUpdate();
    }
}
