package com.moeasy.moeasybe.domain.auth.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AuthRedisRepositoryIntegrationTest {

    @Autowired
    private AuthRedisRepository authRedisRepository;

    @Test
    void refreshToken_회전은_한번만_성공하고_새_식별자로_교체된다() {
        String oldTokenId = UUID.randomUUID().toString();
        String nextTokenId = UUID.randomUUID().toString();
        String finalTokenId = UUID.randomUUID().toString();
        Long memberId = 731L;

        try {
            authRedisRepository.saveRefreshToken(oldTokenId, memberId, Duration.ofMinutes(1));

            assertTrue(authRedisRepository.rotateRefreshToken(
                    oldTokenId,
                    nextTokenId,
                    memberId,
                    Duration.ofMinutes(1)
            ));
            assertFalse(authRedisRepository.rotateRefreshToken(
                    oldTokenId,
                    finalTokenId,
                    memberId,
                    Duration.ofMinutes(1)
            ));
            assertTrue(authRedisRepository.rotateRefreshToken(
                    nextTokenId,
                    finalTokenId,
                    memberId,
                    Duration.ofMinutes(1)
            ));
        } finally {
            authRedisRepository.deleteRefreshToken(oldTokenId);
            authRedisRepository.deleteRefreshToken(nextTokenId);
            authRedisRepository.deleteRefreshToken(finalTokenId);
        }
    }
}
