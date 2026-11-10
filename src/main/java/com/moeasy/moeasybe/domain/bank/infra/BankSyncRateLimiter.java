package com.moeasy.moeasybe.domain.bank.infra;

import com.moeasy.moeasybe.domain.bank.config.BankProperties;
import com.moeasy.moeasybe.domain.bank.exception.BankException;
import com.moeasy.moeasybe.domain.bank.exception.code.BankErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BankSyncRateLimiter {
    private final StringRedisTemplate redis;
    private final BankProperties settings;
    private static final DefaultRedisScript<Long> SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local t=redis.call('TIME')
                    local now=t[1]*1000+math.floor(t[2]/1000)
                    for _,k in ipairs(KEYS) do
                      redis.call('ZREMRANGEBYSCORE',k,'-inf',now-1800000)
                      if redis.call('ZCARD',k)>=30 then return 0 end
                    end
                    for _,k in ipairs(KEYS) do
                      redis.call('ZADD',k,now,ARGV[1])
                      redis.call('PEXPIRE',k,1800000)
                    end
                    return 1
                    """,
                    Long.class);

    public void acquire(String keyFingerprint) {
        try {
            Long allowed =
                    redis.execute(
                            SCRIPT,
                            List.of(
                                    settings.getRateLimitPrefix() + ":global",
                                    settings.getRateLimitPrefix() + ":key:" + keyFingerprint),
                            UUID.randomUUID().toString());
            if (!Long.valueOf(1).equals(allowed))
                throw new BankException(BankErrorCode.RATE_LIMITED);
        } catch (BankException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BankException(BankErrorCode.UNAVAILABLE);
        }
    }
}
