package com.moeasy.moeasybe.domain.member.service.query;

import com.moeasy.moeasybe.domain.member.converter.MemberConverter;
import com.moeasy.moeasybe.domain.member.dto.response.MemberResDTO;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberQueryService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MemberResDTO.NicknameAvailability getNicknameAvailability(String nickname) {
        boolean available = !memberRepository.existsByNickname(nickname);
        return MemberConverter.toNicknameAvailability(available);
    }
}
