package com.moeasy.moeasybe.domain.group.service.command;

import com.moeasy.moeasybe.domain.group.converter.GroupJoinRequestConverter;
import com.moeasy.moeasybe.domain.group.dto.request.GroupJoinRequestReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupJoinRequestResDTO;
import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupJoinRequest;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinRequestStatus;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.domain.group.repository.GroupJoinRequestRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupMemberRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.group.validate.GroupAccessValidator;
import com.moeasy.moeasybe.domain.group.validate.GroupRequestValidator;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupJoinRequestCommandService {
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupJoinRequestRepository joinRequestRepository;
    private final MemberRepository memberRepository;
    private final GroupAccessValidator accessValidator;
    private final Clock clock;

    @Transactional
    public GroupJoinRequestResDTO.Created create(Long groupId, GroupJoinRequestReqDTO.Create request, Long memberId) {
        GroupRequestValidator.requireActor(request, memberId);
        Group group = lockGroup(groupId);
        requireActive(group);
        if (group.getJoinPolicy() != GroupJoinPolicy.APPROVAL) {
            throw new GroupException(GroupErrorCode.GROUP_APPROVAL_REQUIRED);
        }
        requireEligible(groupId, memberId);
        if (joinRequestRepository.existsByGroupIdAndMemberIdAndStatus(groupId, memberId, GroupJoinRequestStatus.PENDING)) {
            throw new GroupException(GroupErrorCode.JOIN_REQUEST_DUPLICATE);
        }
        requireCapacity(group);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.UNAUTHORIZED));
        GroupJoinRequest entity = GroupJoinRequestConverter.toEntity(group, member, request);
        try {
            joinRequestRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            if (isPendingConstraintViolation(exception)) {
                throw new GroupException(GroupErrorCode.JOIN_REQUEST_DUPLICATE);
            }
            throw exception;
        }
        return GroupJoinRequestConverter.toCreated(entity);
    }

    @Transactional
    public void cancel(Long groupId, Long memberId) {
        GroupRequestValidator.requireMemberId(memberId);
        lockGroup(groupId);
        GroupJoinRequest request = joinRequestRepository
                .findByGroupIdAndMemberIdAndStatus(groupId, memberId, GroupJoinRequestStatus.PENDING)
                .orElseThrow(() -> new GroupException(GroupErrorCode.JOIN_REQUEST_PENDING_NOT_FOUND));
        request.cancel(now());
    }

    @Transactional
    public void approve(Long groupId, Long requestId, Long memberId) {
        GroupRequestValidator.requireMemberId(memberId);
        Group group = lockGroup(groupId);
        requireActive(group);
        GroupMember processor = accessValidator.requireJoinManager(groupId, memberId);
        GroupJoinRequest request = findPending(groupId, requestId);
        GroupMember existing = requireEligible(groupId, request.getMember().getId());
        requireCapacity(group);
        LocalDateTime approvedAt = now();
        if (existing == null) {
            groupMemberRepository.save(GroupJoinRequestConverter.toMember(group, request.getMember(), approvedAt));
        } else {
            existing.rejoin(approvedAt);
        }
        request.approve(processor, approvedAt);
    }

    @Transactional
    public void reject(Long groupId, Long requestId, GroupJoinRequestReqDTO.Reject input, Long memberId) {
        GroupRequestValidator.requireActor(input, memberId);
        Group group = lockGroup(groupId);
        requireActive(group);
        GroupMember processor = accessValidator.requireJoinManager(groupId, memberId);
        findPending(groupId, requestId).reject(processor, input.rejectionReason(), now());
    }

    private Group lockGroup(Long groupId) {
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        GroupRequestValidator.requireOpen(group);
        accessValidator.requireNoDeletionHistory(groupId);
        return group;
    }

    private void requireActive(Group group) {
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new GroupException(GroupErrorCode.GROUP_ARCHIVED);
        }
    }

    private GroupJoinRequest findPending(Long groupId, Long requestId) {
        GroupJoinRequest request = joinRequestRepository.findByIdAndGroupId(requestId, groupId)
                .orElseThrow(() -> new GroupException(GroupErrorCode.JOIN_REQUEST_NOT_FOUND));
        if (request.getStatus() != GroupJoinRequestStatus.PENDING) {
            throw new GroupException(GroupErrorCode.JOIN_REQUEST_ALREADY_PROCESSED);
        }
        return request;
    }

    private GroupMember requireEligible(Long groupId, Long memberId) {
        GroupMember membership = groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId).orElse(null);
        if (membership != null) {
            if (membership.getStatus() == GroupMemberStatus.KICKED) {
                throw new GroupException(GroupErrorCode.GROUP_MEMBER_KICKED);
            }
            if (membership.getStatus() == GroupMemberStatus.ACTIVE && membership.getDeletedAt() == null) {
                throw new GroupException(GroupErrorCode.GROUP_ALREADY_JOINED);
            }
        }
        return membership;
    }

    private void requireCapacity(Group group) {
        if (groupMemberRepository.countByGroupIdAndStatusAndDeletedAtIsNull(group.getId(), GroupMemberStatus.ACTIVE)
                >= group.getMaxMembers()) {
            throw new GroupException(GroupErrorCode.GROUP_CAPACITY_EXCEEDED);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private boolean isPendingConstraintViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                return name != null && (name.equals("uq_group_join_request_pending")
                        || name.endsWith(".uq_group_join_request_pending"));
            }
        }
        return false;
    }
}
