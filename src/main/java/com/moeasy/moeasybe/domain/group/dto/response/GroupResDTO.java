package com.moeasy.moeasybe.domain.group.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;

public final class GroupResDTO {
    private GroupResDTO() {
    }

    @Builder
    public record Members(List<MemberSummary> members) {
    }

    @Builder
    public record MemberSummary(Long groupMemberId, Long memberId, String nickname,
            GroupMemberRole role) {
    }

    @Builder
    public record Created(Long groupId) {
    }

    @Builder
    public record Detail(Long groupId, Long categoryId, String name, String description,
            String regionCode, String placeName, String address, BigDecimal latitude,
            BigDecimal longitude, Integer maxMembers, GroupJoinPolicy joinPolicy,
            GroupStatus status, String coverImageUrl, @Schema(allowableValues = {"bronze", "silver", "gold"},
                    accessMode = Schema.AccessMode.READ_ONLY) String tier) {
    }

    @Builder
    public record CoverImageUploadIssue(String coverImageKey, String uploadUrl, Instant uploadUrlExpiresAt,
            Map<String, String> requiredHeaders) {
    }
}
