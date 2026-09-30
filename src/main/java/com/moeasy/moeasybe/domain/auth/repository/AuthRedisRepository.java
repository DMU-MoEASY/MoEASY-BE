package com.moeasy.moeasybe.domain.auth.repository;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuthRedisRepository {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";
    private static final DefaultRedisScript<Long> ROTATE_REFRESH_TOKEN_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end "
                    + "redis.call('DEL', KEYS[1]) "
                    + "redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2]) "
                    + "return 1",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    public void save(String key, String value, Duration expiration) {
        stringRedisTemplate.opsForValue().set(key, value, expiration);
    }

    public String getAndDelete(String key) {
        return stringRedisTemplate.opsForValue().getAndDelete(key);
    }

    public void saveRefreshToken(String tokenId, Long memberId, Duration expiration) {
        stringRedisTemplate.opsForValue().set(
                refreshTokenKey(tokenId),
                memberId.toString(),
                expiration
        );
    }

    public boolean rotateRefreshToken(
            String previousTokenId,
            String nextTokenId,
            Long memberId,
            Duration expiration
    ) {
        Long result = stringRedisTemplate.execute(
                ROTATE_REFRESH_TOKEN_SCRIPT,
                List.of(refreshTokenKey(previousTokenId), refreshTokenKey(nextTokenId)),
                memberId.toString(),
                Long.toString(expiration.toMillis())
        );
        return Long.valueOf(1L).equals(result);
    }

    public void deleteRefreshToken(String tokenId) {
        stringRedisTemplate.delete(refreshTokenKey(tokenId));
    }

    private String refreshTokenKey(String tokenId) {
        return REFRESH_TOKEN_KEY_PREFIX + tokenId;
    }
}
