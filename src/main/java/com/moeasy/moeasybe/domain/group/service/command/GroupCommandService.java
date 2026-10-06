package com.moeasy.moeasybe.domain.group.service.command;

import com.moeasy.moeasybe.domain.group.converter.GroupConverter;
import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupResDTO;
import com.moeasy.moeasybe.domain.group.entity.Category;
import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.domain.group.repository.CategoryRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupDeletionHistoryRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupMemberRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.group.service.GroupCoverImageService;
import com.moeasy.moeasybe.domain.group.validate.GroupAccessValidator;
import com.moeasy.moeasybe.domain.group.validate.GroupCoverImageValidator;
import com.moeasy.moeasybe.domain.group.validate.GroupRequestValidator;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class GroupCommandService {
    private final GroupRepository groupRepository;
    private final CategoryRepository categoryRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupDeletionHistoryRepository deletionHistoryRepository;
    private final MemberRepository memberRepository;
    private final GroupCoverImageService coverImageService;
    private final GroupCoverImageValidator coverImageValidator;
    private final GroupAccessValidator accessValidator;
    private final Clock clock;

    @Transactional
    public GroupResDTO.Created createGroup(GroupReqDTO.Create request, Long memberId) {
        GroupRequestValidator.requireActor(request, memberId);
        GroupRequestValidator.validateCreate(request);
        if (request.coverImageKey() != null) {
            coverImageValidator.verifyOwnedAndExists(memberId, request.coverImageKey());
        }
        Category category = findCategory(request.categoryId());
        Member member = findMember(memberId);
        Group group = groupRepository.save(GroupConverter.toEntity(request, category));
        groupMemberRepository.save(GroupConverter.toOwner(group, member, LocalDateTime.now(clock)));
        return GroupConverter.toCreated(group);
    }

    @Transactional
    public GroupResDTO.Detail updateGroup(Long groupId, GroupReqDTO.Update request, Long memberId) {
        GroupRequestValidator.requireActor(request, memberId);
        GroupRequestValidator.validatePatch(request);
        Group group = findOpenGroupForUpdate(groupId);
        accessValidator.requireOwner(groupId, memberId);

        if (request.has("categoryId")) {
            group.changeCategory(findCategory(GroupRequestValidator.longValue(request.value("categoryId"))));
        }
        if (request.has("name")) group.changeName(GroupRequestValidator.text(request.value("name"), 100));
        if (request.has("description")) group.changeDescription(GroupRequestValidator.text(request.value("description"), -1));
        if (request.has("regionCode")) group.changeRegionCode(GroupRequestValidator.text(request.value("regionCode"), 32));
        if (request.has("placeName")) group.changePlaceName(GroupRequestValidator.text(request.value("placeName"), 100));
        if (request.has("address")) group.changeAddress(GroupRequestValidator.text(request.value("address"), 255));
        if (request.has("coverImageKey")) {
            JsonNode node = request.value("coverImageKey");
            String key = GroupRequestValidator.isNull(node) ? null : GroupRequestValidator.text(node, 500);
            if (key != null) coverImageValidator.verifyOwnedAndExists(memberId, key);
            group.changeCoverImageKey(key);
        }
        if (request.has("latitude")) {
            JsonNode lat = request.value("latitude");
            JsonNode lon = request.value("longitude");
            group.changeCoordinates(GroupRequestValidator.isNull(lat) ? null : GroupRequestValidator.decimal(lat, "-90", "90"),
                    GroupRequestValidator.isNull(lon) ? null : GroupRequestValidator.decimal(lon, "-180", "180"));
        }
        return GroupConverter.toDetail(group, coverImageService.createViewUrl(group.getCoverImageKey()));
    }

    @Transactional
    public void closeGroup(Long groupId, GroupReqDTO.Close request, Long memberId) {
        GroupRequestValidator.requireActor(request, memberId);
        Group group = findOpenGroupForUpdate(groupId);
        accessValidator.requireNoDeletionHistory(groupId);
        accessValidator.requireOwner(groupId, memberId);
        GroupRequestValidator.requireConfirmationText(group, request);
        LocalDateTime closedAt = LocalDateTime.now(clock);
        group.close(closedAt);
        deletionHistoryRepository.save(GroupConverter.toDeletionHistory(
                group, findMember(memberId), request, closedAt));
    }

    private Group findOpenGroupForUpdate(Long groupId) {
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        GroupRequestValidator.requireOpen(group);
        return group;
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new GroupException(GroupErrorCode.CATEGORY_NOT_FOUND));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.UNAUTHORIZED));
    }

}
