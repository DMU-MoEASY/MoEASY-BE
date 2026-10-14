package com.moeasy.moeasybe.domain.group.dto.response;

import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import java.math.BigDecimal;
import lombok.Builder;

public final class GroupResDTO {
    private GroupResDTO() {
    }

    @Builder
    public record Created(Long groupId) {
    }

    @Builder
    public record Detail(Long groupId, Long categoryId, String name, String description,
            String regionCode, String placeName, String address, BigDecimal latitude,
            BigDecimal longitude, Integer maxMembers, GroupJoinPolicy joinPolicy,
            GroupStatus status, String coverImageUrl) {
    }
}
