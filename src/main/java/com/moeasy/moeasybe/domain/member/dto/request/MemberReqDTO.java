package com.moeasy.moeasybe.domain.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class MemberReqDTO {

    private MemberReqDTO() {
    }

    public record NicknameAvailability(
            @NotBlank(message = "닉네임은 필수입니다.")
            @Size(min = 2, max = 12, message = "닉네임은 2자 이상 12자 이하여야 합니다.")
            @Schema(description = "확인할 닉네임 (2~12자, 공백만 입력 불가)", example = "모이지",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String nickname
    ) {
    }

    @Schema(name = "MemberOnboardingRequest", description = "온보딩 최종 제출 정보")
    public record Onboarding(
            @NotBlank(message = "닉네임은 필수입니다.")
            @Size(min = 2, max = 12, message = "닉네임은 2자 이상 12자 이하여야 합니다.")
            @Schema(description = "닉네임", example = "모이지")
            String nickname,

            @Size(max = 60, message = "한 줄 소개는 60자 이하여야 합니다.")
            @Schema(description = "한 줄 소개 (선택 입력)", example = "함께 달릴 친구를 찾고 있어요")
            String statusMessage,

            @NotBlank(message = "주 활동 지역은 필수입니다.")
            @Pattern(regexp = "[0-9]{5}", message = "지역 코드는 5자리 숫자여야 합니다.")
            @Schema(description = "지역 목록 조회 API에서 받은 지역 코드", example = "11680")
            String regionCode,

            @NotNull(message = "관심사는 필수입니다.")
            @Size(min = 3, max = 5, message = "관심사는 3개 이상 5개 이하로 선택해야 합니다.")
            @Schema(description = "중복 없는 관심사 코드 3~5개", example = "[\"RUNNING\",\"STUDY\",\"READING\"]")
            List<@NotBlank(message = "관심사 코드는 비어 있을 수 없습니다.")
                    @Pattern(regexp = "[A-Z_]+", message = "관심사 코드는 영문 대문자와 밑줄로 작성해야 합니다.")
                    String> categoryCodes
    ) {
    }
}
