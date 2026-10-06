package com.moeasy.moeasybe.domain.member.service.command;

import com.moeasy.moeasybe.domain.group.entity.Category;
import com.moeasy.moeasybe.domain.group.repository.CategoryRepository;
import com.moeasy.moeasybe.domain.member.converter.MemberConverter;
import com.moeasy.moeasybe.domain.member.dto.request.MemberReqDTO;
import com.moeasy.moeasybe.domain.member.dto.response.MemberResDTO;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.MemberStatus;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.member.exception.MemberException;
import com.moeasy.moeasybe.domain.member.exception.code.MemberErrorCode;
import com.moeasy.moeasybe.domain.member.repository.MemberCategoryRepository;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import com.moeasy.moeasybe.domain.region.entity.Region;
import com.moeasy.moeasybe.domain.region.repository.RegionRepository;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberCommandService {

    private final MemberRepository memberRepository;
    private final MemberConverter memberConverter;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final MemberCategoryRepository memberCategoryRepository;

    @Transactional
    public Member findOrCreateSocialMember(SocialType socialType, String socialId) {
        return memberRepository.findBySocialTypeAndSocialId(socialType, socialId)
                .orElseGet(() -> {
                    Member member = memberConverter.toSocialMember(socialType, socialId);
                    return memberRepository.save(member);
                });
    }

    @Transactional
    public MemberResDTO.Onboarding completeOnboarding(Long memberId, MemberReqDTO.Onboarding request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        if (member.getDeletedAt() != null || member.getStatus() != MemberStatus.ACTIVE) {
            throw new MemberException(MemberErrorCode.MEMBER_UNAVAILABLE);
        }

        if (member.isOnboardingCompleted()) {
            throw new MemberException(MemberErrorCode.ONBOARDING_ALREADY_COMPLETED);
        }

        if (memberRepository.existsByNicknameAndIdNot(request.nickname(), memberId)) {
            throw new MemberException(MemberErrorCode.DUPLICATE_NICKNAME);
        }

        Region region = regionRepository.findByCodeAndDeletedAtIsNull(request.regionCode())
                .orElseThrow(() -> new MemberException(MemberErrorCode.INVALID_REGION));

        if (new HashSet<>(request.categoryCodes()).size() != request.categoryCodes().size()) {
            throw new MemberException(MemberErrorCode.INVALID_CATEGORIES);
        }

        List<Category> categories = categoryRepository.findAllByCodeInAndDeletedAtIsNull(request.categoryCodes());
        if (categories.size() != request.categoryCodes().size()) {
            throw new MemberException(MemberErrorCode.INVALID_CATEGORIES);
        }

        member.completeOnboarding(request.nickname(), request.statusMessage(), region);
        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (isNicknameConstraintViolation(ex)) {
                throw new MemberException(MemberErrorCode.DUPLICATE_NICKNAME);
            }
            throw ex;
        }
        memberCategoryRepository.deleteAllByMemberId(memberId);
        memberCategoryRepository.saveAllAndFlush(MemberConverter.toMemberCategories(member, categories));
        return MemberConverter.toOnboarding(member);
    }

    private boolean isNicknameConstraintViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();
                return constraintName != null && (constraintName.equals("uq_member_nickname")
                        || constraintName.endsWith(".uq_member_nickname"));
            }
        }
        return false;
    }
}
