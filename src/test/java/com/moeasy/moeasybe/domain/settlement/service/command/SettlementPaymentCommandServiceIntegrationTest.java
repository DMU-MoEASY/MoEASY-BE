package com.moeasy.moeasybe.domain.settlement.service.command;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
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
import com.moeasy.moeasybe.domain.settlement.entity.Settlement;
import com.moeasy.moeasybe.domain.settlement.entity.SettlementItem;
import com.moeasy.moeasybe.domain.settlement.entity.SettlementPayment;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementItemStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentType;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementItemRepository;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementPaymentRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SettlementPaymentCommandServiceIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 10, 0);

    @Autowired private SettlementCommandService service;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @MockitoBean(name = "clock") private Clock clock;
    @MockitoSpyBean private SettlementPaymentRepository paymentRepository;
    @MockitoSpyBean private SettlementItemRepository itemRepository;

    private final List<Long> groupIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();
    private Group group;
    private GroupMember owner;
    private GroupMember target;
    private Settlement settlement;
    private SettlementItem item;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-30T01:00:00Z"));
        transactionTemplate.executeWithoutResult(status -> {
            group = persistGroup();
            owner = persistMembership(group, GroupMemberRole.OWNER);
            target = persistMembership(group, GroupMemberRole.MEMBER);
            settlement = persistSettlement(group, owner);
            item = SettlementItem.builder().settlement(settlement).groupMember(target).amount(10_000L).build();
            entityManager.persist(item);
        });
    }

    @AfterEach
    void cleanUp() {
        // 서비스가 실제로 커밋·롤백하도록 테스트 바깥 트랜잭션은 사용하지 않는다.
        for (Long groupId : groupIds) {
            jdbcTemplate.update("""
                    DELETE p FROM settlement_payment p JOIN settlement_item i ON p.settlement_item_id = i.id
                    JOIN settlement s ON i.settlement_id = s.id WHERE s.group_id = ?
                    """, groupId);
            jdbcTemplate.update("""
                    DELETE i FROM settlement_item i JOIN settlement s ON i.settlement_id = s.id WHERE s.group_id = ?
                    """, groupId);
            jdbcTemplate.update("DELETE FROM settlement WHERE group_id = ?", groupId);
            jdbcTemplate.update("DELETE FROM group_member WHERE group_id = ?", groupId);
            jdbcTemplate.update("DELETE FROM member_group WHERE id = ?", groupId);
        }
        memberIds.forEach(id -> jdbcTemplate.update("DELETE FROM member WHERE id = ?", id));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GROUP", "SETTLEMENT", "ITEM", "MEMBER", "REQUEST", "AMOUNT", "VERSION"})
    @DisplayName("서비스를 직접 호출해도 필수 ID·요청·금액·버전 누락은 저장 전에 거부한다")
    void registerPayment_missingRequiredInput_badRequest(String missing) {
        // given
        Long groupId = missing.equals("GROUP") ? null : group.getId();
        Long settlementId = missing.equals("SETTLEMENT") ? null : settlement.getId();
        Long itemId = missing.equals("ITEM") ? null : item.getId();
        Long memberId = missing.equals("MEMBER") ? null : owner.getMember().getId();
        SettlementReqDTO.RegisterPayment request = missing.equals("REQUEST") ? null :
                new SettlementReqDTO.RegisterPayment(missing.equals("AMOUNT") ? null : 1_000L,
                        missing.equals("VERSION") ? null : 0L, null);

        // when
        GeneralException failure = assertThrows(GeneralException.class,
                () -> service.registerPayment(groupId, settlementId, itemId, request, memberId));

        // then
        assertRejected(failure, GeneralErrorCode.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "MANAGER"})
    @DisplayName("운영진은 기한이 지난 타인의 청구에 부분 납부를 기록하고 실제 등록자를 보존한다")
    void registerPayment_operator_recordsPartialPayment(String role) {
        // given
        jdbcTemplate.update("UPDATE group_member SET role = ? WHERE id = ?", role, owner.getId());

        // when
        SettlementResDTO.PaymentRegistered result = register(4_000, 0, null);

        // then
        transactionTemplate.executeWithoutResult(status -> {
            SettlementPayment payment = entityManager.find(SettlementPayment.class, result.paymentId());
            assertAll(
                    () -> assertNotNull(result.paymentId()),
                    () -> assertEquals(item.getId(), result.settlementItemId()),
                    () -> assertEquals(SettlementItemStatus.PENDING, result.status()),
                    () -> assertEquals(4_000L, result.paidAmount()),
                    () -> assertEquals(6_000L, result.remainingAmount()),
                    () -> assertEquals(1L, result.version()),
                    () -> assertEquals(NOW, payment.getOccurredAt()),
                    () -> assertEquals(owner.getId(), payment.getRecordedByGroupMember().getId()),
                    () -> assertEquals(target.getId(), payment.getSettlementItem().getGroupMember().getId()),
                    () -> assertEquals("PAYMENT", payment.getPaymentType().name()),
                    () -> assertEquals("COMPLETED", payment.getStatus().name()),
                    () -> assertEquals("MANUAL", payment.getSource().name()),
                    () -> assertNull(payment.getAccountTransactionId()),
                    () -> assertNotNull(payment.getCreatedAt())
            );
        });
        assertItem("PENDING", 1);
    }

    @Test
    @DisplayName("최신 버전으로 나머지를 납부하면 완납되며 정산은 OPEN을 유지한다")
    void registerPayment_remainingAmount_marksPaid() {
        // given
        register(4_000, 0, NOW.minusDays(1));

        // when
        SettlementResDTO.PaymentRegistered result = register(6_000, 1, NOW);

        // then
        assertAll(
                () -> assertEquals(10_000L, result.paidAmount()),
                () -> assertEquals(0L, result.remainingAmount()),
                () -> assertEquals(SettlementItemStatus.PAID, result.status()),
                () -> assertEquals(2, paymentCount()),
                () -> assertEquals("OPEN", jdbcTemplate.queryForObject(
                        "SELECT status FROM settlement WHERE id = ?", String.class, settlement.getId()))
        );
        assertItem("PAID", 2);
    }

    @Test
    @DisplayName("Long 최대 청구액도 기존 합계에 마지막 1원을 더해 정확하게 완납한다")
    void registerPayment_maxLongAmount_aggregatesWithoutOverflow() {
        // given
        jdbcTemplate.update("UPDATE settlement SET total_amount = ? WHERE id = ?", Long.MAX_VALUE, settlement.getId());
        jdbcTemplate.update("UPDATE settlement_item SET amount = ? WHERE id = ?", Long.MAX_VALUE, item.getId());
        register(Long.MAX_VALUE - 1, 0, null);

        // when
        SettlementResDTO.PaymentRegistered result = register(1, 1, null);

        // then
        assertAll(
                () -> assertEquals(Long.MAX_VALUE, result.paidAmount()),
                () -> assertEquals(0L, result.remainingAmount()),
                () -> assertEquals(2, paymentCount())
        );
        assertItem("PAID", 2);
    }

    @ParameterizedTest
    @CsvSource({"PAYMENT,COMPLETED,MANUAL,2000", "PAYMENT,COMPLETED,BANK,2000",
            "PAYMENT,CANCELLED,MANUAL,1000", "PAYMENT,PENDING,MANUAL,1000",
            "PAYMENT,FAILED,MANUAL,1000", "REFUND,COMPLETED,MANUAL,1000"})
    @DisplayName("기존 합계에는 출처와 무관하게 완료된 PAYMENT만 포함한다")
    void registerPayment_existingRecords_countsOnlyCompletedPayments(
            String type, String paymentStatus, String source, long expectedPaid) {
        // given
        transactionTemplate.executeWithoutResult(status -> {
            SettlementPayment payment = SettlementPayment.builder().settlementItem(item)
                    .amount(1_000L).occurredAt(NOW.minusDays(1)).recordedByGroupMember(owner).build();
            entityManager.persist(payment);
            entityManager.flush();
            jdbcTemplate.update("UPDATE settlement_payment SET payment_type = ?, status = ?, source = ? WHERE id = ?",
                    type, paymentStatus, source, payment.getId());
        });

        // when
        SettlementResDTO.PaymentRegistered result = register(1_000, 0, null);

        // then
        assertAll(
                () -> assertEquals(expectedPaid, result.paidAmount()),
                () -> assertEquals(10_000 - expectedPaid, result.remainingAmount()),
                () -> assertEquals(2, paymentCount())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "LEFT", "KICKED", "DELETED_MEMBERSHIP", "DELETED_ACCOUNT", "OUTSIDER"})
    @DisplayName("일반 회원·비활성 회원·삭제 계정·외부인은 기록을 등록할 수 없다")
    void registerPayment_notOperator_forbidden(String condition) {
        // given
        Long requesterId = owner.getMember().getId();
        switch (condition) {
            case "MEMBER" -> requesterId = target.getMember().getId();
            case "OUTSIDER" -> requesterId = Long.MAX_VALUE;
            case "LEFT", "KICKED" -> jdbcTemplate.update(
                    "UPDATE group_member SET status = ? WHERE id = ?", condition, owner.getId());
            case "DELETED_MEMBERSHIP" -> jdbcTemplate.update(
                    "UPDATE group_member SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", owner.getId());
            case "DELETED_ACCOUNT" -> jdbcTemplate.update(
                    "UPDATE member SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", requesterId);
            default -> throw new IllegalArgumentException(condition);
        }
        Long memberId = requesterId;

        // when
        GeneralException failure = assertThrows(GeneralException.class, () -> service.registerPayment(
                group.getId(), settlement.getId(), item.getId(), request(1_000, 0, null), memberId));

        // then
        assertRejected(failure, GeneralErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("폐쇄 모임의 탈퇴·삭제된 대상 회원 청구도 운영진이 정리할 수 있다")
    void registerPayment_closedGroupAndFormerTarget_allowed() {
        // given
        transactionTemplate.executeWithoutResult(status -> entityManager.find(Group.class, group.getId()).close(NOW));
        jdbcTemplate.update("UPDATE group_member SET status = 'LEFT' WHERE id = ?", target.getId());
        jdbcTemplate.update("UPDATE member SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", target.getMember().getId());

        // when
        SettlementResDTO.PaymentRegistered result = register(1_000, 0, null);

        // then
        assertEquals(1_000L, result.paidAmount());
        assertItem("PENDING", 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GROUP", "SETTLEMENT", "OTHER_GROUP", "DELETED_SETTLEMENT", "ITEM", "OTHER_SETTLEMENT"})
    @DisplayName("경로의 모임·정산·청구가 없거나 소속이 다르면 저장하지 않는다")
    void registerPayment_invalidPath_notFound(String condition) {
        // given
        Long groupId = group.getId();
        Long settlementId = settlement.getId();
        Long itemId = item.getId();
        BaseErrorCode expected = SettlementErrorCode.SETTLEMENT_NOT_FOUND;
        switch (condition) {
            case "GROUP" -> { groupId = Long.MAX_VALUE; expected = GroupErrorCode.GROUP_NOT_FOUND; }
            case "SETTLEMENT" -> settlementId = Long.MAX_VALUE;
            case "OTHER_GROUP" -> settlementId = transactionTemplate.execute(status -> {
                Group other = persistGroup();
                GroupMember otherOwner = persistMembership(other, GroupMemberRole.OWNER);
                return persistSettlement(other, otherOwner).getId();
            });
            case "DELETED_SETTLEMENT" -> jdbcTemplate.update(
                    "UPDATE settlement SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", settlementId);
            case "ITEM" -> { itemId = Long.MAX_VALUE; expected = SettlementErrorCode.ITEM_NOT_FOUND; }
            case "OTHER_SETTLEMENT" -> {
                settlementId = transactionTemplate.execute(status -> persistSettlement(group, owner).getId());
                expected = SettlementErrorCode.ITEM_NOT_FOUND;
            }
            default -> throw new IllegalArgumentException(condition);
        }
        Long requestGroup = groupId;
        Long requestSettlement = settlementId;
        Long requestItem = itemId;

        // when
        GeneralException failure = assertThrows(GeneralException.class, () -> service.registerPayment(
                requestGroup, requestSettlement, requestItem, request(1_000, 0, null), owner.getMember().getId()));

        // then
        assertRejected(failure, expected);
    }

    @ParameterizedTest
    @CsvSource({"CLOSED,PENDING", "CANCELLED,PENDING", "OPEN,REFUNDED", "OPEN,CANCELLED"})
    @DisplayName("변경 불가능한 정산·청구 상태는 납부를 저장하지 않는다")
    void registerPayment_disallowedState_rejectsWithoutMutation(String settlementStatus, String itemStatus) {
        // given
        jdbcTemplate.update("UPDATE settlement SET status = ? WHERE id = ?", settlementStatus, settlement.getId());
        jdbcTemplate.update("UPDATE settlement_item SET status = ? WHERE id = ?", itemStatus, item.getId());

        // when
        GeneralException failure = assertThrows(GeneralException.class, () -> register(1_000, 0, null));

        // then
        assertEquals(SettlementErrorCode.PAYMENT_NOT_ALLOWED, failure.getCode());
        assertEquals(0, paymentCount());
        assertItem(itemStatus, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STALE_VERSION", "EXCESS", "FUTURE"})
    @DisplayName("버전 충돌·초과 납부·미래 입금 시각은 기존 납부와 버전을 보존한다")
    void registerPayment_invalidChange_preservesExistingPayment(String condition) {
        // given
        register(4_000, 0, null);
        long version = condition.equals("STALE_VERSION") ? 0 : 1;
        long amount = condition.equals("EXCESS") ? 6_001 : 1_000;
        LocalDateTime occurredAt = condition.equals("FUTURE") ? NOW.plusSeconds(1) : NOW;
        BaseErrorCode expected = switch (condition) {
            case "STALE_VERSION" -> SettlementErrorCode.ITEM_VERSION_CONFLICT;
            case "EXCESS" -> SettlementErrorCode.PAYMENT_AMOUNT_EXCEEDS_REMAINING;
            default -> SettlementErrorCode.INVALID_OCCURRED_AT;
        };

        // when
        GeneralException failure = assertThrows(GeneralException.class, () -> register(amount, version, occurredAt));

        // then
        assertEquals(expected, failure.getCode());
        assertEquals(1, paymentCount());
        assertItem("PENDING", 1);
    }

    @Test
    @DisplayName("납부 행과 청구 변경을 flush한 뒤 실패해도 모두 롤백된다")
    void registerPayment_saveFailure_rollsBackPaymentStatusAndVersion() {
        // given
        doAnswer(invocation -> {
            SettlementPayment payment = invocation.getArgument(0);
            entityManager.persist(payment);
            entityManager.flush();
            assertEquals(1, paymentCount());
            assertItem("PAID", 1);
            throw new IllegalStateException("납부 저장 실패");
        }).when(paymentRepository).save(any(SettlementPayment.class));

        // when
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> register(10_000, 0, null));

        // then
        assertEquals("납부 저장 실패", failure.getMessage());
        assertEquals(0, paymentCount());
        assertItem("PENDING", 0);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    @DisplayName("동시 요청은 행 잠금으로 대기하고 같은 버전은 충돌·다음 버전은 최신 합계로 완납된다")
    void registerPayment_concurrentRequests_serializesAndReadsCommittedPayments(long secondVersion) throws Exception {
        // given
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEnteringLock = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        // Spring Data 인터페이스의 실제 쿼리 프록시에 위임해 행 잠금 SQL을 그대로 실행한다.
        var itemDelegate = mockingDetails(itemRepository).getMockCreationSettings().getDefaultAnswer();
        var paymentDelegate = mockingDetails(paymentRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            int call = calls.incrementAndGet();
            if (call == 2) {
                secondEnteringLock.countDown();
            }
            Object result = itemDelegate.answer(invocation);
            if (call == 1) {
                firstLocked.countDown();
                assertTrue(releaseFirst.await(10, TimeUnit.SECONDS));
            }
            return result;
        }).when(itemRepository).findByIdAndSettlementIdForUpdate(item.getId(), settlement.getId());
        doAnswer(invocation -> {
            assertEquals("READ-COMMITTED", jdbcTemplate.queryForObject("SELECT @@transaction_isolation", String.class));
            return paymentDelegate.answer(invocation);
        }).when(paymentRepository).sumAmountByItemAndTypeAndStatus(
                item.getId(), SettlementPaymentType.PAYMENT, SettlementPaymentStatus.COMPLETED);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> register(4_000, 0, null));
            assertTrue(firstLocked.await(5, TimeUnit.SECONDS));

            // when
            var second = executor.submit(() -> register(6_000, secondVersion, null));
            assertTrue(secondEnteringLock.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);

            // then
            if (secondVersion == 0) {
                ExecutionException failure = assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
                assertEquals(SettlementErrorCode.ITEM_VERSION_CONFLICT, ((GeneralException) failure.getCause()).getCode());
                assertEquals(1, paymentCount());
                assertItem("PENDING", 1);
            } else {
                SettlementResDTO.PaymentRegistered result = second.get(5, TimeUnit.SECONDS);
                assertAll(
                        () -> assertEquals(10_000L, result.paidAmount()),
                        () -> assertEquals(0L, result.remainingAmount()),
                        () -> assertEquals(2, paymentCount())
                );
                assertItem("PAID", 2);
            }
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANUAL", "BANK"})
    @DisplayName("운영진이 수동·은행 납부를 취소하면 원본과 감사 정보를 보존하고 청구 현황을 갱신한다")
    void cancelPayment_completedPayment_preservesOriginalAndUpdatesItem(String source) {
        // given
        SettlementResDTO.PaymentRegistered first = register(4_000, 0, NOW.minusDays(1));
        register(6_000, 1, NOW);
        jdbcTemplate.update("UPDATE settlement_payment SET source = ? WHERE id = ?", source, first.paymentId());
        GroupMember manager = transactionTemplate.execute(status -> persistMembership(group, GroupMemberRole.MANAGER));
        LocalDateTime createdAt = transactionTemplate.execute(status ->
                entityManager.find(SettlementPayment.class, first.paymentId()).getCreatedAt());
        if (source.equals("BANK")) {
            transactionTemplate.executeWithoutResult(status -> entityManager.find(Group.class, group.getId()).close(NOW));
            jdbcTemplate.update("UPDATE group_member SET status = 'LEFT' WHERE id = ?", target.getId());
            jdbcTemplate.update("UPDATE member SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                    target.getMember().getId());
        }

        // when
        SettlementResDTO.PaymentCancelled result = service.cancelPayment(
                group.getId(), settlement.getId(), item.getId(), first.paymentId(),
                new SettlementReqDTO.CancelPayment(2L, "  다른 회원의 입금 정정  "), manager.getMember().getId());

        // then
        assertAll(
                () -> assertEquals(first.paymentId(), result.paymentId()),
                () -> assertEquals(item.getId(), result.settlementItemId()),
                () -> assertEquals(SettlementItemStatus.PENDING, result.status()),
                () -> assertEquals(6_000L, result.paidAmount()),
                () -> assertEquals(4_000L, result.remainingAmount()),
                () -> assertEquals(3L, result.version()),
                () -> assertEquals(2, paymentCount())
        );
        transactionTemplate.executeWithoutResult(status -> {
            SettlementPayment payment = entityManager.find(SettlementPayment.class, first.paymentId());
            assertAll(
                    () -> assertEquals(SettlementPaymentStatus.CANCELLED, payment.getStatus()),
                    () -> assertEquals(source, payment.getSource().name()),
                    () -> assertEquals(4_000L, payment.getAmount()),
                    () -> assertEquals(NOW.minusDays(1), payment.getOccurredAt()),
                    () -> assertEquals(createdAt, payment.getCreatedAt()),
                    () -> assertEquals(owner.getId(), payment.getRecordedByGroupMember().getId()),
                    () -> assertEquals(manager.getId(), payment.getCancelledByGroupMember().getId()),
                    () -> assertEquals(NOW, payment.getCancelledAt()),
                    () -> assertEquals("다른 회원의 입금 정정", payment.getCancelReason()),
                    () -> assertEquals(6_000L, paymentRepository.sumAmountByItemAndTypeAndStatus(
                            item.getId(), SettlementPaymentType.PAYMENT, SettlementPaymentStatus.COMPLETED))
            );
        });
        assertItem("PENDING", 3);
    }

    @Test
    @DisplayName("일반 회원은 자신의 청구에 기록된 납부도 취소할 수 없다")
    void cancelPayment_regularMember_forbidden() {
        // given
        Long paymentId = register(4_000, 0, null).paymentId();

        // when
        GeneralException failure = assertThrows(GeneralException.class, () -> service.cancelPayment(
                group.getId(), settlement.getId(), item.getId(), paymentId,
                new SettlementReqDTO.CancelPayment(1L, "오등록"), target.getMember().getId()));

        // then
        assertEquals(GeneralErrorCode.FORBIDDEN, failure.getCode());
        assertPaymentStatus(paymentId, "COMPLETED");
        assertItem("PENDING", 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "OTHER_ITEM"})
    @DisplayName("없는 납부 또는 다른 청구의 납부 ID는 현재 청구에서 취소할 수 없다")
    void cancelPayment_invalidPaymentPath_notFound(String condition) {
        // given
        Long originalId = register(4_000, 0, null).paymentId();
        Long paymentId = condition.equals("MISSING") ? Long.MAX_VALUE : transactionTemplate.execute(status -> {
            Settlement other = persistSettlement(group, owner);
            SettlementItem otherItem = SettlementItem.builder().settlement(other).groupMember(target).amount(10_000L).build();
            entityManager.persist(otherItem);
            SettlementPayment otherPayment = SettlementPayment.builder().settlementItem(otherItem)
                    .amount(4_000L).occurredAt(NOW).recordedByGroupMember(owner).build();
            entityManager.persist(otherPayment);
            return otherPayment.getId();
        });

        // when
        GeneralException failure = assertThrows(GeneralException.class, () -> cancel(paymentId, 1));

        // then
        assertEquals(SettlementErrorCode.PAYMENT_NOT_FOUND, failure.getCode());
        assertPaymentStatus(originalId, "COMPLETED");
        if (condition.equals("OTHER_ITEM")) {
            assertPaymentStatus(paymentId, "COMPLETED");
        }
        assertItem("PENDING", 1);
    }

    @Test
    @DisplayName("취소를 다시 요청해도 최초 취소 기록과 청구 버전을 변경하지 않는다")
    void cancelPayment_alreadyCancelled_conflict() {
        // given
        Long paymentId = register(4_000, 0, null).paymentId();
        cancel(paymentId, 1);

        // when
        GeneralException failure = assertThrows(GeneralException.class, () -> cancel(paymentId, 2));

        // then
        assertEquals(SettlementErrorCode.PAYMENT_CANCELLATION_NOT_ALLOWED, failure.getCode());
        assertPaymentStatus(paymentId, "CANCELLED");
        assertItem("PENDING", 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STALE_VERSION", "CLOSED"})
    @DisplayName("버전 충돌·마감 정산이면 납부 상태 변경도 함께 롤백한다")
    void cancelPayment_invalidItem_rollsBackPaymentMutation(String condition) {
        // given
        Long paymentId = register(4_000, 0, null).paymentId();
        long version = condition.equals("STALE_VERSION") ? 0 : 1;
        if (condition.equals("CLOSED")) {
            jdbcTemplate.update("UPDATE settlement SET status = 'CLOSED' WHERE id = ?", settlement.getId());
        }

        // when
        GeneralException failure = assertThrows(GeneralException.class, () -> cancel(paymentId, version));

        // then
        assertEquals(condition.equals("STALE_VERSION") ? SettlementErrorCode.ITEM_VERSION_CONFLICT
                : SettlementErrorCode.PAYMENT_NOT_ALLOWED, failure.getCode());
        assertPaymentStatus(paymentId, "COMPLETED");
        assertNull(jdbcTemplate.queryForObject("SELECT cancelled_at FROM settlement_payment WHERE id = ?",
                LocalDateTime.class, paymentId));
        assertItem("PENDING", 1);
    }

    @Test
    @DisplayName("취소 기록과 청구 변경을 DB에 반영한 뒤 실패하면 두 변경 모두 롤백한다")
    void cancelPayment_failureAfterFlush_rollsBackBothEntities() {
        // given
        Long paymentId = register(10_000, 0, null).paymentId();
        TransactionTemplate transaction = new TransactionTemplate(transactionTemplate.getTransactionManager());
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        // when
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> transaction.executeWithoutResult(status -> {
                    cancel(paymentId, 1);
                    entityManager.flush();
                    assertPaymentStatus(paymentId, "CANCELLED");
                    assertItem("PENDING", 2);
                    throw new IllegalStateException("취소 처리 후 실패");
                }));

        // then
        assertEquals("취소 처리 후 실패", failure.getMessage());
        assertPaymentStatus(paymentId, "COMPLETED");
        assertItem("PAID", 1);
    }

    @ParameterizedTest
    @ValueSource(longs = {2, 3})
    @DisplayName("취소·등록 경쟁은 행 잠금으로 대기하고 같은 버전은 충돌·다음 버전은 취소 후 합계를 사용한다")
    void cancelPayment_concurrentRegistration_serializesAndReadsCommittedPayments(long secondVersion) throws Exception {
        // given
        Long paymentId = register(4_000, 0, null).paymentId();
        register(6_000, 1, null);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEnteringLock = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        var itemDelegate = mockingDetails(itemRepository).getMockCreationSettings().getDefaultAnswer();
        var paymentDelegate = mockingDetails(paymentRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            int call = calls.incrementAndGet();
            if (call == 2) {
                secondEnteringLock.countDown();
            }
            Object result = itemDelegate.answer(invocation);
            if (call == 1) {
                firstLocked.countDown();
                assertTrue(releaseFirst.await(10, TimeUnit.SECONDS));
            }
            return result;
        }).when(itemRepository).findByIdAndSettlementIdForUpdate(item.getId(), settlement.getId());
        doAnswer(invocation -> {
            assertEquals("READ-COMMITTED", jdbcTemplate.queryForObject("SELECT @@transaction_isolation", String.class));
            return paymentDelegate.answer(invocation);
        }).when(paymentRepository).sumAmountByItemAndTypeAndStatus(
                item.getId(), SettlementPaymentType.PAYMENT, SettlementPaymentStatus.COMPLETED);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> cancel(paymentId, 2));
            assertTrue(firstLocked.await(5, TimeUnit.SECONDS));

            // when
            var second = executor.submit(() -> register(4_000, secondVersion, null));
            assertTrue(secondEnteringLock.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            SettlementResDTO.PaymentCancelled cancelled = first.get(5, TimeUnit.SECONDS);

            // then
            assertEquals(6_000L, cancelled.paidAmount());
            assertPaymentStatus(paymentId, "CANCELLED");
            if (secondVersion == 2) {
                ExecutionException failure = assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
                assertEquals(SettlementErrorCode.ITEM_VERSION_CONFLICT, ((GeneralException) failure.getCause()).getCode());
                assertItem("PENDING", 3);
                assertEquals(2, paymentCount());
            } else {
                SettlementResDTO.PaymentRegistered result = second.get(5, TimeUnit.SECONDS);
                assertAll(
                        () -> assertEquals(10_000L, result.paidAmount()),
                        () -> assertEquals(0L, result.remainingAmount()),
                        () -> assertEquals(3, paymentCount())
                );
                assertItem("PAID", 4);
            }
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private SettlementResDTO.PaymentCancelled cancel(Long paymentId, long version) {
        return service.cancelPayment(group.getId(), settlement.getId(), item.getId(), paymentId,
                new SettlementReqDTO.CancelPayment(version, "오등록 정정"), owner.getMember().getId());
    }

    private void assertPaymentStatus(Long paymentId, String status) {
        assertEquals(status, jdbcTemplate.queryForObject(
                "SELECT status FROM settlement_payment WHERE id = ?", String.class, paymentId));
    }

    private SettlementResDTO.PaymentRegistered register(long amount, long version, LocalDateTime occurredAt) {
        return service.registerPayment(group.getId(), settlement.getId(), item.getId(),
                request(amount, version, occurredAt), owner.getMember().getId());
    }

    private SettlementReqDTO.RegisterPayment request(long amount, long version, LocalDateTime occurredAt) {
        return new SettlementReqDTO.RegisterPayment(amount, version, occurredAt);
    }

    private Group persistGroup() {
        Category category = entityManager.createQuery("select c from Category c where c.code = 'RUNNING'", Category.class)
                .getSingleResult();
        Group created = Group.builder().category(category).name("납부 테스트 " + UUID.randomUUID())
                .description("설명").regionCode("11680").placeName("장소").address("주소")
                .maxMembers(20).joinPolicy(GroupJoinPolicy.APPROVAL).build();
        entityManager.persist(created);
        groupIds.add(created.getId());
        return created;
    }

    private GroupMember persistMembership(Group memberGroup, GroupMemberRole role) {
        Member member = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).build();
        entityManager.persist(member);
        memberIds.add(member.getId());
        GroupMember membership = GroupMember.builder().group(memberGroup).member(member).role(role)
                .status(GroupMemberStatus.ACTIVE).joinedAt(NOW.minusDays(1)).build();
        entityManager.persist(membership);
        return membership;
    }

    private Settlement persistSettlement(Group memberGroup, GroupMember creator) {
        Settlement created = Settlement.builder().group(memberGroup).createdByGroupMember(creator)
                .title("회비").dueAt(NOW.minusDays(1)).totalAmount(10_000L).splitMode(SettlementSplitMode.EQUAL).build();
        entityManager.persist(created);
        return created;
    }

    private int paymentCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlement_payment WHERE settlement_item_id = ?",
                Integer.class, item.getId());
    }

    private void assertItem(String status, long version) {
        var stored = jdbcTemplate.queryForMap("SELECT status, version FROM settlement_item WHERE id = ?", item.getId());
        assertAll(
                () -> assertEquals(status, stored.get("status")),
                () -> assertEquals(version, ((Number) stored.get("version")).longValue())
        );
    }

    private void assertRejected(GeneralException failure, BaseErrorCode expected) {
        assertEquals(expected, failure.getCode());
        assertEquals(0, paymentCount());
        assertItem("PENDING", 0);
    }
}
