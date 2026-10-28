package com.moeasy.moeasybe.domain.group.service;

import com.moeasy.moeasybe.domain.group.converter.GroupConverter;
import com.moeasy.moeasybe.domain.group.dto.response.GroupResDTO;
import com.moeasy.moeasybe.domain.group.repository.GroupCoverUploadRepository;
import com.moeasy.moeasybe.domain.group.validate.GroupCoverImageValidator;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import com.moeasy.moeasybe.storage.S3StorageService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupCoverImageService {
    private final S3StorageService storageService;
    private final GroupCoverUploadRepository uploadRepository;
    private final MemberRepository memberRepository;
    private final GroupCoverImageValidator coverImageValidator;
    private final Clock clock;

    @Transactional
    public GroupResDTO.CoverImageUploadIssue issueUpload(Long memberId, String contentType) {
        coverImageValidator.validateIssueRequest(memberId, contentType);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.UNAUTHORIZED));
        String key = "group-covers/" + memberId + "/" + UUID.randomUUID();
        Instant issuedAt = clock.instant();
        S3StorageService.PresignedUpload upload = storageService.createPresignedPutUrl(key, contentType);
        uploadRepository.save(GroupConverter.toCoverUpload(key, member, contentType,
                LocalDateTime.ofInstant(issuedAt, clock.getZone())));
        return GroupConverter.toCoverImageUploadIssue(key, upload);
    }

    public String createViewUrl(String key) {
        return key == null ? null : storageService.createPresignedGetUrl(key).toString();
    }
}
