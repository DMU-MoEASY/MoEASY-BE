package com.moeasy.moeasybe.domain.group.dto.response;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;

public final class GroupFeedResDTO {
    private GroupFeedResDTO() {}

    @Builder
    public record Feed(List<Item> items, long totalGroupCount, boolean hasNext,
            @Schema(types = {"string", "null"}) String nextCursor) {}

    @Schema(oneOf = {GroupCard.class, AdCard.class}, discriminatorProperty = "type",
            discriminatorMapping = {
                    @DiscriminatorMapping(value = "GROUP", schema = GroupCard.class),
                    @DiscriminatorMapping(value = "AD", schema = AdCard.class)
            })
    public sealed interface Item permits GroupCard, AdCard { String type(); }

    @Builder
    public record GroupCard(
            @Schema(allowableValues = "GROUP", requiredMode = Schema.RequiredMode.REQUIRED) String type,
            long groupId, String name, String description,
            String categoryCode, String categoryName, String regionCode, String regionName,
            @Schema(types = {"string", "null"}) String coverImageUrl,
            long currentMemberCount, int maxMembers, String tier,
            List<String> recommendationReasons) implements Item {}

    @Builder
    public record AdCard(
            @Schema(allowableValues = "AD", requiredMode = Schema.RequiredMode.REQUIRED) String type,
            long adId, String advertiserName, String title,
            String imageUrl, String landingUrl) implements Item {}
}
