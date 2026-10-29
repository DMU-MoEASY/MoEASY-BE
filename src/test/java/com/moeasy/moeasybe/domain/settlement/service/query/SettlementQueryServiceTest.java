package com.moeasy.moeasybe.domain.settlement.service.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.group.repository.GroupMemberRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementItemRepository;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementRepository;
import com.moeasy.moeasybe.domain.settlement.service.support.SettlementTargetFinder;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceTest {

    private static final Long GROUP_ID = 10L;
    private static final Long MEMBER_ID = 42L;

    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private SettlementRepository settlementRepository;
    @Mock private SettlementItemRepository settlementItemRepository;

    private SettlementQueryService service;

    @BeforeEach
    void setUp() {
        service = new SettlementQueryService(groupRepository, groupMemberRepository,
                settlementRepository, settlementItemRepository, new SettlementSplitCalculator(),
                new SettlementTargetFinder(groupMemberRepository));
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
