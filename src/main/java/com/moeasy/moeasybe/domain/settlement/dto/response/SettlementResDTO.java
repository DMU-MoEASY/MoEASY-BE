package com.moeasy.moeasybe.domain.settlement.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementItemStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

public final class SettlementResDTO {

    private SettlementResDTO() {
    }

    @Builder
    @Schema(name = "SettlementPageResponse", description = "정산 목록과 페이지 정보")
    public record Page(
            @Schema(description = "생성 시각 내림차순, 같은 시각에서는 ID 내림차순의 정산 목록")
            List<Summary> items,

            @Schema(description = "0부터 시작하는 현재 페이지 번호", example = "0")
            int page,

            @Schema(description = "요청한 페이지 크기", example = "20")
            int size,

            @Schema(description = "필터를 적용한 전체 정산 수", example = "1")
            long totalElements,

            @Schema(description = "전체 페이지 수", example = "1")
            int totalPages,

            @Schema(description = "다음 페이지 존재 여부", example = "false")
            boolean hasNext
    ) {
    }

    @Builder
    @Schema(name = "SettlementSummary", description = "정산 목록의 요약 정보")
    public record Summary(
            @Schema(description = "정산 ID", example = "31")
            Long settlementId,

            @Schema(description = "정산 제목", example = "러닝 후 식사비")
            String title,

            @Schema(description = "연결된 일정 ID (일정 연결이 없으면 null)", example = "7")
            Long scheduleId,

            @Schema(description = "저장된 정산 상태", example = "OPEN")
            SettlementStatus status,

            @Schema(description = "생성 시 확정한 분할 방식", example = "EQUAL")
            SettlementSplitMode splitMode,

            @Schema(description = "원 단위 정산 총액", example = "10000")
            Long totalAmount,

            @Schema(description = "저장된 청구 항목 수 (탈퇴·삭제된 대상 포함)", example = "3")
            int targetCount,

            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
            @Schema(description = "납부 기한 (한국 시간, 기한이 없으면 null)", type = "string",
                    example = "2026-11-01T23:59:59")
            LocalDateTime dueAt,

            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
            @Schema(description = "정산 생성 시각 (한국 시간)", type = "string",
                    example = "2026-10-20T14:00:00")
            LocalDateTime createdAt
    ) {
    }

    @Builder
    @Schema(name = "SettlementDetailResponse", description = "정산 상세와 전체 회원별 청구 내역")
    public record Detail(
            @Schema(description = "정산 ID", example = "31")
            Long settlementId,

            @Schema(description = "정산이 속한 모임 ID", example = "1")
            Long groupId,

            @Schema(description = "연결된 일정 ID (일정 연결이 없으면 null)", example = "7")
            Long scheduleId,

            @Schema(description = "정산 제목", example = "러닝 후 식사비")
            String title,

            @Schema(description = "정산 설명 (설명이 없으면 null)", example = "식사비 정산입니다.")
            String description,

            @Schema(description = "저장된 정산 상태", example = "OPEN")
            SettlementStatus status,

            @Schema(description = "생성 시 확정한 분할 방식", example = "EQUAL")
            SettlementSplitMode splitMode,

            @Schema(description = "원 단위 정산 총액", example = "10000")
            Long totalAmount,

            @Schema(description = "저장된 청구 항목 수 (items 길이와 같음)", example = "3")
            int targetCount,

            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
            @Schema(description = "납부 기한 (한국 시간, 기한이 없으면 null)", type = "string",
                    example = "2026-11-01T23:59:59")
            LocalDateTime dueAt,

            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
            @Schema(description = "정산 생성 시각 (한국 시간)", type = "string",
                    example = "2026-10-20T14:00:00")
            LocalDateTime createdAt,

            @Schema(description = "정산 생성자 정보")
            Creator createdBy,

            @Schema(description = "모임 회원 ID 오름차순의 전체 청구 항목 (탈퇴·삭제된 대상 포함)")
            List<Item> items
    ) {
    }

    @Builder
    @Schema(name = "SettlementCreator", description = "정산 생성자")
    public record Creator(
            @Schema(description = "생성자의 모임 회원 ID (group_member.id)", example = "11")
            Long groupMemberId,

            @Schema(description = "조회 시점의 생성자 닉네임", example = "정규")
            String nickname
    ) {
    }

    @Builder
    @Schema(name = "SettlementItem", description = "저장된 회원별 청구 항목")
    public record Item(
            @Schema(description = "청구 항목 ID (settlement_item.id)", example = "101")
            Long settlementItemId,

            @Schema(description = "대상 모임 회원 ID (group_member.id)", example = "11")
            Long groupMemberId,

            @Schema(description = "조회 시점의 대상자 닉네임", example = "정규")
            String nickname,

            @Schema(description = "원 단위 최종 청구액 (나머지 배분이 이미 포함된 저장 금액)", example = "3334")
            Long amount,

            @Schema(description = "저장된 청구 상태", example = "PENDING")
            SettlementItemStatus status
    ) {
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
