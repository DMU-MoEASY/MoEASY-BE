package com.moeasy.moeasybe.domain.group.converter;

import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupResDTO;
import com.moeasy.moeasybe.domain.group.entity.Category;
import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupDeletionHistory;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.member.entity.Member;
import java.time.LocalDateTime;

public final class GroupConverter {
    private GroupConverter() {
    }

    public static Group toEntity(GroupReqDTO.Create request, Category category) {
        return Group.builder()
                .category(category)
                .name(request.name())
                .coverImageKey(request.coverImageKey())
                .description(request.description())
                .regionCode(request.regionCode())
                .placeName(request.placeName())
                .address(request.address())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .maxMembers(request.maxMembers())
                .joinPolicy(request.joinPolicy())
                .build();
    }

    public static GroupMember toOwner(Group group, Member member, LocalDateTime joinedAt) {
        return GroupMember.builder()
                .group(group)
                .member(member)
                .role(GroupMemberRole.OWNER)
                .status(GroupMemberStatus.ACTIVE)
                .joinedAt(joinedAt)
                .build();
    }

    public static GroupDeletionHistory toDeletionHistory(Group group, Member member,
            GroupReqDTO.Close request, LocalDateTime closedAt) {
        return GroupDeletionHistory.builder()
                .group(group)
                .deletedByMember(member)
                .reason(request.reason())
                .confirmationText(request.confirmationText())
                .archiveContent(request.archiveContent())
                .closedAt(closedAt)
                .build();
    }

    public static GroupResDTO.Created toCreated(Group group) {
        return GroupResDTO.Created.builder().groupId(group.getId()).build();
    }

    public static GroupResDTO.Detail toDetail(Group group, String coverImageUrl) {
        return GroupResDTO.Detail.builder()
                .groupId(group.getId())
                .categoryId(group.getCategory().getId())
                .name(group.getName())
                .description(group.getDescription())
                .regionCode(group.getRegionCode())
                .placeName(group.getPlaceName())
                .address(group.getAddress())
                .latitude(group.getLatitude())
                .longitude(group.getLongitude())
                .maxMembers(group.getMaxMembers())
                .joinPolicy(group.getJoinPolicy())
                .status(group.getStatus())
                .coverImageUrl(coverImageUrl)
                .build();
    }
}
