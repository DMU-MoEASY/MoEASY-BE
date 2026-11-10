package com.moeasy.moeasybe.domain.group.service.support;

import static org.junit.jupiter.api.Assertions.*;

import com.moeasy.moeasybe.global.security.jwt.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupFeedCursorCodecTest {

    @Test
    @DisplayName("손상된 커서의 일반 오류 메시지를 유지하고 원인 예외를 보존한다")
    void decode_malformedBase64_preservesCause() {
        // given
        var properties = new JwtProperties("test-only-group-feed-secret-at-least-32-bytes", "test",
                new JwtProperties.TokenExpiration(60), new JwtProperties.TokenExpiration(120));
        var codec = new GroupFeedCursorCodec(properties);

        // when
        var exception = assertThrows(IllegalArgumentException.class, () -> codec.decode("payload.%%", 1L, "filter"));

        // then
        assertAll(() -> assertEquals("잘못된 추천 커서입니다.", exception.getMessage()),
                () -> assertInstanceOf(IllegalArgumentException.class, exception.getCause()));
    }
}
