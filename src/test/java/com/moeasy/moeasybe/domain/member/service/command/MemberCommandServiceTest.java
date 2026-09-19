package com.moeasy.moeasybe.domain.member.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.entity.SocialType;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberCommandServiceTest {

    @Mock
    private MemberRepository memberRepository;

    private MemberCommandService memberCommandService;

    @BeforeEach
    void setUp() {
        memberCommandService = new MemberCommandService(memberRepository);
    }

    @Test
    void 기존_소셜_회원을_반환한다() {
        Member member = Member.builder()
                .socialType(SocialType.KAKAO)
                .socialId("123456789")
                .build();
        when(memberRepository.findBySocialTypeAndSocialId(SocialType.KAKAO, "123456789"))
                .thenReturn(Optional.of(member));

        Member result =
                memberCommandService.findOrCreateSocialMember(SocialType.KAKAO, "123456789");

        assertSame(member, result);
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void 신규_소셜_회원을_생성한다() {
        when(memberRepository.findBySocialTypeAndSocialId(SocialType.KAKAO, "123456789"))
                .thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Member result =
                memberCommandService.findOrCreateSocialMember(SocialType.KAKAO, "123456789");

        assertEquals(SocialType.KAKAO, result.getSocialType());
        assertEquals("123456789", result.getSocialId());
        assertFalse(result.isOnboardingCompleted());
    }
}
