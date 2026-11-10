package com.moeasy.moeasybe.domain.group.converter;

import com.moeasy.moeasybe.domain.group.dto.request.GroupJoinRequestReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupJoinRequestResDTO;
import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupJoinRequest;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinRequestStatus;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.member.entity.Member;
import java.time.LocalDateTime;
import org.springframework.data.domain.Slice;

public final class GroupJoinRequestConverter {
    private GroupJoinRequestConverter() {
    }

    public static GroupJoinRequest toEntity(Group group, Member member, GroupJoinRequestReqDTO.Create request) {
        return GroupJoinRequest.builder().group(group).member(member).introduction(request.introduction())
                .status(GroupJoinRequestStatus.PENDING).build();
    }

    public static GroupMember toMember(Group group, Member member, LocalDateTime at) {
        return GroupMember.builder().group(group).member(member).role(GroupMemberRole.MEMBER)
                .status(GroupMemberStatus.ACTIVE).joinedAt(at).build();
    }

    public static GroupJoinRequestResDTO.Created toCreated(GroupJoinRequest request) {
        return GroupJoinRequestResDTO.Created.builder().requestId(request.getId()).status(request.getStatus())
                .createdAt(request.getCreatedAt()).build();
    }

    public static GroupJoinRequestResDTO.Detail toDetail(GroupJoinRequest request) {
        return GroupJoinRequestResDTO.Detail.builder().requestId(request.getId()).status(request.getStatus())
                .introduction(request.getIntroduction()).rejectionReason(request.getRejectionReason())
                .createdAt(request.getCreatedAt()).processedAt(request.getProcessedAt()).build();
    }

    public static GroupJoinRequestResDTO.Item toItem(GroupJoinRequest request) {
        return GroupJoinRequestResDTO.Item.builder().requestId(request.getId()).memberId(request.getMember().getId())
                .nickname(request.getMember().getNickname()).introduction(request.getIntroduction())
                .createdAt(request.getCreatedAt()).build();
    }

    public static GroupJoinRequestResDTO.PendingList toPendingList(Slice<GroupJoinRequest> slice) {
        return GroupJoinRequestResDTO.PendingList.builder()
                .items(slice.getContent().stream().map(GroupJoinRequestConverter::toItem).toList())
                .page(slice.getNumber()).size(slice.getSize()).hasNext(slice.hasNext()).build();
    }
}
