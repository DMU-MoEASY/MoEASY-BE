package com.moeasy.moeasybe.domain.group.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class GroupJoinRequestReqDTO {
    private GroupJoinRequestReqDTO() {
    }

    public record Create(
            @NotBlank(message = "소개글은 필수입니다.")
            @Size(max = 500, message = "소개글은 500자 이하여야 합니다.") String introduction
    ) {
    }

    public record Page(
            @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") Integer page,
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") Integer size
    ) {
        public Page {
            page = page == null ? 0 : page;
            size = size == null ? 20 : size;
        }
    }

    public record Reject(
            @Size(max = 500, message = "거절 사유는 500자 이하여야 합니다.") String rejectionReason
    ) {
    }
}
