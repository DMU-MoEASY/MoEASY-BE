package com.moeasy.moeasybe.domain.group.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public final class GroupFeedReqDTO {
    private GroupFeedReqDTO() {}

    public record Search(
            @Schema(defaultValue = "RECOMMENDED", allowableValues = "RECOMMENDED")
            @Pattern(regexp = "RECOMMENDED", message = "정렬은 RECOMMENDED만 지원합니다.") String sort,
            @Size(max = 100, message = "검색어는 100자 이하여야 합니다.") String query,
            @Schema(description = "BE 카테고리 코드 목록. 예: RUNNING,HIKING")
            @Size(max = 20, message = "카테고리는 20개 이하여야 합니다.")
            List<@Pattern(regexp = "[A-Z_]{1,32}", message = "카테고리 코드 형식이 잘못되었습니다.") String> categoryCodes,
            @Pattern(regexp = "[0-9]{5}", message = "지역은 5자리 코드여야 합니다.") String regionCode,
            @Size(max = 2048, message = "커서가 너무 깁니다.") String cursor
    ) {}
}
