package com.moeasy.moeasybe.domain.group.converter;

import com.moeasy.moeasybe.domain.advertisement.repository.AdvertisementRepository.Banner;
import com.moeasy.moeasybe.domain.group.dto.response.GroupFeedResDTO;
import com.moeasy.moeasybe.domain.group.repository.GroupFeedRepository.Candidate;
import java.util.List;

public final class GroupFeedConverter {
    private GroupFeedConverter() {}

    public static GroupFeedResDTO.GroupCard toGroupCard(Candidate c, String imageUrl, List<String> reasons) {
        return GroupFeedResDTO.GroupCard.builder().type("GROUP").groupId(c.id()).name(c.name())
                .description(c.description()).categoryCode(c.categoryCode()).categoryName(c.categoryName())
                .regionCode(c.regionCode()).regionName(c.regionName()).coverImageUrl(imageUrl)
                .currentMemberCount(c.currentMembers()).maxMembers(c.maxMembers()).tier(c.tier())
                .recommendationReasons(reasons).build();
    }

    public static GroupFeedResDTO.AdCard toAdCard(Banner ad) {
        return GroupFeedResDTO.AdCard.builder().type("AD").adId(ad.id()).advertiserName(ad.advertiserName())
                .title(ad.title()).imageUrl(ad.imageUrl()).landingUrl(ad.landingUrl()).build();
    }

    public static GroupFeedResDTO.Feed toFeed(List<GroupFeedResDTO.Item> items, long total, String cursor) {
        return GroupFeedResDTO.Feed.builder().items(List.copyOf(items)).totalGroupCount(total)
                .hasNext(cursor != null).nextCursor(cursor).build();
    }
}
