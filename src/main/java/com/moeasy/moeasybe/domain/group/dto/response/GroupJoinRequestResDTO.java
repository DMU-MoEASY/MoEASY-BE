package com.moeasy.moeasybe.domain.group.dto.response;

import com.moeasy.moeasybe.domain.group.enums.GroupJoinRequestStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

public final class GroupJoinRequestResDTO {
    private GroupJoinRequestResDTO() {
    }

    @Builder
    public record Created(Long requestId, GroupJoinRequestStatus status, LocalDateTime createdAt) {
    }

    @Builder
    public record Detail(Long requestId, GroupJoinRequestStatus status, String introduction,
            String rejectionReason, LocalDateTime createdAt, LocalDateTime processedAt) {
    }

    @Builder
    public record Item(Long requestId, Long memberId, String nickname, String introduction,
            LocalDateTime createdAt) {
    }

    @Builder
    public record PendingList(List<Item> items, int page, int size, boolean hasNext) {
    }
}
