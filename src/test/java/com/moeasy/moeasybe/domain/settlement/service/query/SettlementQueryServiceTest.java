package com.moeasy.moeasybe.domain.settlement.service.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.group.repository.GroupMemberRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.entity.Settlement;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementItemStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentType;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementItemRepository;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementPaymentRepository;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementRepository;
import com.moeasy.moeasybe.domain.settlement.service.support.SettlementTargetFinder;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceTest {

    private static final Long GROUP_ID = 10L;
    private static final Long MEMBER_ID = 42L;

    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private SettlementRepository settlementRepository;
    @Mock private SettlementItemRepository settlementItemRepository;
    @Mock private SettlementPaymentRepository settlementPaymentRepository;

    private SettlementQueryService service;

    @BeforeEach
    void setUp() {
        service = new SettlementQueryService(groupRepository, groupMemberRepository,
                settlementRepository, settlementItemRepository, settlementPaymentRepository,
                new SettlementSplitCalculator(), new SettlementTargetFinder(groupMemberRepository));
    }

    @Test
    @DisplayName("목록 현황은 현재 페이지 ID로 한 번씩 일괄 집계하고 청구가 없는 정산은 0으로 반환한다")
    void getSettlements_pageIds_batchesPaymentSummaryQueries() {
        // given
        Settlement first = Settlement.builder().title("첫 정산").totalAmount(10000L).build();
        Settlement empty = Settlement.builder().title("빈 정산").totalAmount(10000L).build();
        ReflectionTestUtils.setField(first, "id", 31L);
        ReflectionTestUtils.setField(empty, "id", 32L);
        var pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(Group.builder().build()));
        when(groupMemberRepository.findByGroupIdAndMemberIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                GROUP_ID, MEMBER_ID, GroupMemberStatus.ACTIVE)).thenReturn(Optional.of(GroupMember.builder().build()));
        when(settlementRepository.findPageByGroup(GROUP_ID, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(first, empty), pageable, 5));
        var charge = mock(SettlementItemRepository.Summary.class);
        when(charge.getSettlementId()).thenReturn(31L);
        when(charge.getTargetCount()).thenReturn(2L);
        when(charge.getChargedAmount()).thenReturn(10000L);
        when(charge.getPaidCount()).thenReturn(1L);
        when(charge.getPendingCount()).thenReturn(1L);
        when(settlementItemRepository.summarizeBySettlementIds(
                List.of(31L, 32L), SettlementItemStatus.PAID, SettlementItemStatus.PENDING)).thenReturn(List.of(charge));
        var payment = mock(SettlementPaymentRepository.SettlementPaidAmount.class);
        when(payment.getSettlementId()).thenReturn(31L);
        when(payment.getPaidAmount()).thenReturn(4000L);
        when(settlementPaymentRepository.sumAmountsBySettlementIdsAndTypeAndStatus(
                List.of(31L, 32L), SettlementPaymentType.PAYMENT, SettlementPaymentStatus.COMPLETED))
                .thenReturn(List.of(payment));

        // when
        var result = service.getSettlements(GROUP_ID, new SettlementReqDTO.Search(0, 2, null, null), MEMBER_ID);

        // then
        var summary = result.items().getFirst();
        var emptySummary = result.items().get(1);
        assertAll(
                () -> assertEquals(4000L, summary.paidAmount()),
                () -> assertEquals(6000L, summary.remainingAmount()),
                () -> assertEquals(1, summary.paidCount()),
                () -> assertEquals(1, summary.pendingCount()),
                () -> assertEquals(0, emptySummary.targetCount()),
                () -> assertEquals(0L, emptySummary.paidAmount()),
                () -> assertEquals(0L, emptySummary.remainingAmount()),
                () -> assertEquals(0, emptySummary.paidCount()),
                () -> assertEquals(0, emptySummary.pendingCount())
        );
        verify(settlementItemRepository).summarizeBySettlementIds(
                List.of(31L, 32L), SettlementItemStatus.PAID, SettlementItemStatus.PENDING);
        verify(settlementPaymentRepository).sumAmountsBySettlementIdsAndTypeAndStatus(
                List.of(31L, 32L), SettlementPaymentType.PAYMENT, SettlementPaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("납부 이력의 offset이 int 범위를 넘으면 DB 조회 전에 거부한다")
    void getPaymentHistory_offsetExceedsIntegerRange_rejectsBeforeQuery() {
        // given
        var request = new SettlementReqDTO.PaymentSearch(Integer.MAX_VALUE, 100);

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.getPaymentHistory(GROUP_ID, 31L, 101L, request, MEMBER_ID));

        // then
        assertEquals(GeneralErrorCode.BAD_REQUEST, exception.getCode());
        verifyNoInteractions(groupRepository, groupMemberRepository,
                settlementRepository, settlementItemRepository, settlementPaymentRepository);
    }

    @ParameterizedTest
    @CsvSource({"21474837,100", "2147483647,100"})
    @DisplayName("조회 offset이 int 범위를 넘으면 DB 조회 전에 잘못된 요청으로 거부한다")
    void getSettlements_offsetExceedsIntegerRange_rejectsBeforeQuery(int page, int size) {
        // given
        var request = new SettlementReqDTO.Search(page, size, null, null);

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.getSettlements(GROUP_ID, request, MEMBER_ID));

        // then
        assertEquals(GeneralErrorCode.BAD_REQUEST, exception.getCode());
        verifyNoInteractions(groupRepository, groupMemberRepository,
                settlementRepository, settlementItemRepository);
    }

    @ParameterizedTest
    @CsvSource({"2147483647,1", "21474836,100"})
    @DisplayName("int 범위 안의 큰 offset은 페이지 번호를 임의로 제한하지 않고 조회한다")
    void getSettlements_offsetWithinIntegerRange_queriesRequestedPage(int page, int size) {
        // given
        var request = new SettlementReqDTO.Search(page, size, null, null);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(Group.builder().build()));
        when(groupMemberRepository.findByGroupIdAndMemberIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                GROUP_ID, MEMBER_ID, GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.of(GroupMember.builder().build()));
        when(settlementRepository.findPageByGroup(GROUP_ID, null, null, pageable))
                .thenReturn(Page.empty(pageable));

        // when
        var result = service.getSettlements(GROUP_ID, request, MEMBER_ID);

        // then
        assertAll(
                () -> assertEquals(page, result.page()),
                () -> assertEquals(size, result.size()),
                () -> assertTrue(result.items().isEmpty())
        );
        verify(settlementRepository).findPageByGroup(GROUP_ID, null, null, pageable);
        verifyNoInteractions(settlementItemRepository);
    }
}
