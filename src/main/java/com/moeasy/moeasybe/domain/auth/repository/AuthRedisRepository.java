package com.moeasy.moeasybe.domain.auth.repository;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuthRedisRepository {

    private final StringRedisTemplate stringRedisTemplate;

    public void save(String key, String value, Duration expiration) {
        stringRedisTemplate.opsForValue().set(key, value, expiration);
    }
}
