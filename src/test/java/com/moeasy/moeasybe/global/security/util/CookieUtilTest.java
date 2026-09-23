package com.moeasy.moeasybe.global.security.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moeasy.moeasybe.global.config.CookieProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class CookieUtilTest {

    @Test
    void 쿠키_생성시_공통_보안_속성과_호출별_만료시간_경로를_적용한다() {
        CookieUtil cookieUtil = new CookieUtil(new CookieProperties(true, "None"));

        ResponseCookie cookie = cookieUtil.createCookie(
                "accessToken",
                "token-value",
                Duration.ofMinutes(15),
                "/api/v1"
        );

        assertEquals("accessToken", cookie.getName());
        assertEquals("token-value", cookie.getValue());
        assertEquals(Duration.ofMinutes(15), cookie.getMaxAge());
        assertEquals("/api/v1", cookie.getPath());
        assertEquals(true, cookie.isHttpOnly());
        assertEquals(true, cookie.isSecure());
        assertEquals("None", cookie.getSameSite());
    }

    @Test
    void 쿠키_만료시_동일_경로와_보안_속성을_유지하면서_즉시_만료한다() {
        CookieUtil cookieUtil = new CookieUtil(new CookieProperties(false, "Lax"));

        ResponseCookie cookie = cookieUtil.expireCookie("refreshToken", "/api/v1/auth");

        assertEquals("refreshToken", cookie.getName());
        assertEquals(Duration.ZERO, cookie.getMaxAge());
        assertEquals("/api/v1/auth", cookie.getPath());
        assertEquals(true, cookie.isHttpOnly());
        assertEquals(false, cookie.isSecure());
        assertEquals("Lax", cookie.getSameSite());
    }
}
