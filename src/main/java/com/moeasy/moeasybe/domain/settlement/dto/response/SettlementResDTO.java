package com.moeasy.moeasybe.domain.settlement.dto.response;

import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;

public final class SettlementResDTO {

    private SettlementResDTO() {
    }

    @Builder
    @Schema(name = "SettlementCreatedResponse", description = "정산 생성 결과")
    public record Created(
            @Schema(description = "생성한 정산 ID", example = "1")
            Long settlementId
    ) {
    }

    @Builder
    @Schema(name = "SettlementPreviewResponse", description = "정산 분할 미리보기 결과")
    public record Preview(
            @Schema(description = "적용한 분할 방식", example = "EQUAL")
            SettlementSplitMode splitMode,

            @Schema(description = "원 단위 정산 총액", example = "500")
            Long totalAmount,

            @Schema(description = "정산 대상 인원수", example = "3")
            int targetCount,

            @Schema(description = "모임 회원 ID 오름차순의 회원별 분할 결과")
            List<PreviewItem> items
    ) {
    }

    @Builder
    @Schema(name = "SettlementPreviewItem", description = "회원별 분할 미리보기")
    public record PreviewItem(
            @Schema(description = "대상 모임 회원 ID (group_member.id)", example = "11")
            Long groupMemberId,

            @Schema(description = "현재 회원 닉네임", example = "모이지")
            String nickname,

            @Schema(description = "나머지 추가분을 포함한 최종 청구액", example = "167")
            Long amount,

            @Schema(description = "균등 분할에서 추가 배분한 금액 (0 또는 1원). CUSTOM은 0원", example = "1")
            Long remainderAmount
    ) {
    }
}
