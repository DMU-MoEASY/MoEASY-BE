package com.moeasy.moeasybe.domain.group.service;

import com.moeasy.moeasybe.domain.group.converter.GroupConverter;
import com.moeasy.moeasybe.domain.group.repository.GroupCoverUploadRepository;
import com.moeasy.moeasybe.domain.group.validate.GroupCoverImageValidator;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import com.moeasy.moeasybe.storage.S3StorageService;
import java.net.URI;
import java.time.Clock;
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
    public UploadIssue issueUpload(Long memberId, String contentType) {
        coverImageValidator.validateIssueRequest(memberId, contentType);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.UNAUTHORIZED));
        String key = "group-covers/" + memberId + "/" + UUID.randomUUID();
        URI uploadUrl = storageService.createPresignedPutUrl(key, contentType);
        LocalDateTime issuedAt = LocalDateTime.now(clock);
        uploadRepository.save(GroupConverter.toCoverUpload(key, member, contentType, issuedAt));
        return new UploadIssue(key, uploadUrl.toString());
    }

    public String createViewUrl(String key) {
        return key == null ? null : storageService.createPresignedGetUrl(key).toString();
    }

    public record UploadIssue(String coverImageKey, String uploadUrl) {
    }
}
