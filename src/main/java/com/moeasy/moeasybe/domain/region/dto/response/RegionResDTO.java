package com.moeasy.moeasybe.domain.region.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;

public final class RegionResDTO {

    private RegionResDTO() {
    }

    @Builder
    @Schema(description = "시도별 선택 가능한 지역 목록")
    public record Province(
            @Schema(description = "시도명", example = "서울특별시") String provinceName,
            @Schema(description = "해당 시도의 선택 가능한 지역") List<Detail> regions
    ) {
    }

    @Builder
    @Schema(description = "선택 가능한 지역")
    public record Detail(
            @Schema(description = "법정동코드 앞 5자리. 온보딩 요청의 regionCode에 전달", example = "11680") String code,
            @Schema(description = "지역명", example = "강남구") String name
    ) {
    }
}
