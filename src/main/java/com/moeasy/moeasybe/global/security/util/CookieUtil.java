package com.moeasy.moeasybe.global.security.util;

import com.moeasy.moeasybe.global.config.CookieProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CookieUtil {

    private final CookieProperties cookieProperties;

    public ResponseCookie createCookie(String name, String value, Duration maxAge, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie expireCookie(String name, String path) {
        return createCookie(name, "", Duration.ZERO, path);
    }
}
