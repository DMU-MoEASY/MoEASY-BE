package com.moeasy.moeasybe.domain.settlement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
}
