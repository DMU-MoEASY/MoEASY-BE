package com.moeasy.moeasybe.domain.group.service.query;

import com.moeasy.moeasybe.domain.group.converter.GroupConverter;
import com.moeasy.moeasybe.domain.group.dto.response.GroupResDTO;
import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.group.service.GroupCoverImageService;
import com.moeasy.moeasybe.domain.group.validate.GroupRequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupQueryService {
    private final GroupRepository groupRepository;
    private final GroupCoverImageService coverImageService;

    @Transactional(readOnly = true)
    public GroupResDTO.Detail getGroup(Long groupId, Long memberId) {
        GroupRequestValidator.requireMemberId(memberId);
        Group group = groupRepository.findByIdAndStatusNotAndDeletedAtIsNull(groupId, GroupStatus.CLOSED)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        return GroupConverter.toDetail(group, coverImageService.createViewUrl(group.getCoverImageKey()));
    }
}
