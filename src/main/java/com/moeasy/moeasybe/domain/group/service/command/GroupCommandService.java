package com.moeasy.moeasybe.domain.group.service.command;

import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupResDTO;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.group.validate.GroupAccessValidator;
import com.moeasy.moeasybe.domain.group.validate.GroupCoverImageValidator;
import com.moeasy.moeasybe.domain.group.validate.GroupRequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class GroupCommandService {
    private final GroupCoverImageValidator coverImageValidator;
    private final GroupRepository groupRepository;
    private final GroupAccessValidator accessValidator;
    private final GroupWriteService groupWriteService;

    public GroupResDTO.Created createGroup(GroupReqDTO.Create request, Long memberId) {
        GroupRequestValidator.requireActor(request, memberId);
        GroupRequestValidator.validateCreate(request);
        if (request.coverImageKey() != null) {
            coverImageValidator.verifyOwnedAndExists(memberId, request.coverImageKey());
        }
        return groupWriteService.createGroup(request, memberId);
    }

    public GroupResDTO.Detail updateGroup(Long groupId, GroupReqDTO.Update request, Long memberId) {
        GroupRequestValidator.requireActor(request, memberId);
        GroupRequestValidator.validatePatch(request);
        if (request.has("coverImageKey")) {
            JsonNode node = request.value("coverImageKey");
            if (!GroupRequestValidator.isNull(node)) {
                requireCurrentOwner(groupId, memberId);
                String key = GroupRequestValidator.text(node, 500);
                coverImageValidator.verifyOwnedAndExists(memberId, key);
            }
        }
        return groupWriteService.updateGroup(groupId, request, memberId);
    }

    public void closeGroup(Long groupId, GroupReqDTO.Close request, Long memberId) {
        GroupRequestValidator.requireActor(request, memberId);
        groupWriteService.closeGroup(groupId, request, memberId);
    }

    private void requireCurrentOwner(Long groupId, Long memberId) {
        groupRepository.findByIdAndStatusNotAndDeletedAtIsNull(groupId, GroupStatus.CLOSED)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        accessValidator.requireOwner(groupId, memberId);
    }
}
