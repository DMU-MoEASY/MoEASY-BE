package com.moeasy.moeasybe.domain.settlement.dto.request;

import com.moeasy.moeasybe.domain.settlement.dto.deserializer.SettlementDueAtDeserializer;
import com.moeasy.moeasybe.domain.settlement.dto.deserializer.SettlementLongDeserializer;
import com.moeasy.moeasybe.domain.settlement.dto.deserializer.SettlementSplitModeDeserializer;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

public final class SettlementReqDTO {

    private SettlementReqDTO() {
    }

    @Schema(name = "SettlementSearchRequest", description = "정산 목록 조회 조건")
    public record Search(
            @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
            @Schema(description = "0부터 시작하는 페이지 번호", defaultValue = "0", example = "0")
            Integer page,

            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
            @Schema(description = "페이지 크기 (1~100)", defaultValue = "20", example = "20")
            Integer size,

            @Schema(description = "정산 상태 필터 (생략하면 전체 상태)", example = "OPEN")
            SettlementStatus status,

            @Positive(message = "일정 ID는 양수여야 합니다.")
            @Schema(description = "연결된 일정 ID 필터 (생략하면 일정 연결 여부와 무관하게 조회)", example = "7")
            Long scheduleId
    ) {
        public Search {
            page = page == null ? 0 : page;
            size = size == null ? 20 : size;
        }
    }

    @Schema(name = "SettlementCreateRequest", description = "정산 생성 요청")
    public record Create(
            @NotBlank(message = "정산 제목은 필수입니다.")
            @Size(max = 100, message = "정산 제목은 100자 이하여야 합니다.")
            @Schema(description = "정산 제목 (앞뒤 공백 제거)", example = "10월 모임 회비")
            String title,

            @Size(max = 500, message = "정산 설명은 500자 이하여야 합니다.")
            @Schema(description = "정산 설명 (선택 입력)", example = "모임 운영비입니다.")
            String description,

            @JsonDeserialize(using = SettlementDueAtDeserializer.class)
            @Schema(description = "납부 기한 (선택 입력, 한국 시간 기준, 생성 시각 이후)",
                    type = "string", example = "2026-11-01T23:59:59")
            LocalDateTime dueAt,

            @Positive(message = "일정 ID는 양수여야 합니다.")
            @JsonDeserialize(using = SettlementLongDeserializer.class)
            @Schema(description = "같은 모임의 일정 ID (선택 입력)", example = "7")
            Long scheduleId,

            @NotNull(message = "분할 방식은 필수입니다.")
            @JsonDeserialize(using = SettlementSplitModeDeserializer.class)
            @Schema(description = "분할 방식: EQUAL(균등), CUSTOM(회원별 금액 지정)", example = "EQUAL")
            SettlementSplitMode splitMode,

            @NotNull(message = "정산 총액은 필수입니다.")
            @Positive(message = "정산 총액은 양수여야 합니다.")
            @JsonDeserialize(using = SettlementLongDeserializer.class)
            @Schema(description = "원 단위 정수 총액 (두 분할 방식 모두 필수)", example = "10000")
            Long totalAmount,

            @Size(min = 1, message = "정산 대상은 한 명 이상이어야 합니다.")
            @JsonDeserialize(contentUsing = SettlementLongDeserializer.class)
            @Schema(description = "EQUAL에서 필수인 대상 모임 회원 ID 목록. CUSTOM에서는 생략 또는 null",
                    example = "[11, 12, 13]")
            List<@NotNull(message = "모임 회원 ID는 필수입니다.")
                    @Positive(message = "모임 회원 ID는 양수여야 합니다.") Long> groupMemberIds,

            @Valid
            @Size(min = 1, message = "회원별 청구액은 한 명 이상 지정해야 합니다.")
            @Schema(description = "CUSTOM에서 필수인 회원별 청구액 목록. EQUAL에서는 생략 또는 null")
            List<@NotNull(message = "회원별 청구액 항목은 비어 있을 수 없습니다.") MemberAmount> memberAmounts
    ) {
        public Create {
            title = title == null ? null : title.trim();
        }
    }

    @Schema(name = "SettlementPreviewRequest", description = "정산 분할 미리보기 요청")
    public record Preview(
            @NotNull(message = "분할 방식은 필수입니다.")
            @JsonDeserialize(using = SettlementSplitModeDeserializer.class)
            @Schema(description = "분할 방식: EQUAL(균등), CUSTOM(회원별 금액 지정)", example = "EQUAL")
            SettlementSplitMode splitMode,

            @NotNull(message = "정산 총액은 필수입니다.")
            @Positive(message = "정산 총액은 양수여야 합니다.")
            @JsonDeserialize(using = SettlementLongDeserializer.class)
            @Schema(description = "원 단위 정수 총액 (두 분할 방식 모두 필수)", example = "500")
            Long totalAmount,

            @Size(min = 1, message = "정산 대상은 한 명 이상이어야 합니다.")
            @JsonDeserialize(contentUsing = SettlementLongDeserializer.class)
            @Schema(description = "EQUAL에서 필수인 대상 모임 회원 ID 목록. CUSTOM에서는 생략 또는 null",
                    example = "[11, 12, 13]")
            List<@NotNull(message = "모임 회원 ID는 필수입니다.")
                    @Positive(message = "모임 회원 ID는 양수여야 합니다.") Long> groupMemberIds,

            @Valid
            @Size(min = 1, message = "회원별 청구액은 한 명 이상 지정해야 합니다.")
            @Schema(description = "CUSTOM에서 필수인 회원별 청구액 목록. EQUAL에서는 생략 또는 null")
            List<@NotNull(message = "회원별 청구액 항목은 비어 있을 수 없습니다.") MemberAmount> memberAmounts
    ) {
    }

    @Schema(name = "SettlementMemberAmount", description = "회원별 지정 청구액")
    public record MemberAmount(
            @NotNull(message = "모임 회원 ID는 필수입니다.")
            @Positive(message = "모임 회원 ID는 양수여야 합니다.")
            @JsonDeserialize(using = SettlementLongDeserializer.class)
            @Schema(description = "대상 모임 회원 ID (group_member.id)", example = "11")
            Long groupMemberId,

            @NotNull(message = "회원별 청구액은 필수입니다.")
            @Positive(message = "회원별 청구액은 양수여야 합니다.")
            @JsonDeserialize(using = SettlementLongDeserializer.class)
            @Schema(description = "원 단위 정수 청구액", example = "12000")
            Long amount
    ) {
    }
}
