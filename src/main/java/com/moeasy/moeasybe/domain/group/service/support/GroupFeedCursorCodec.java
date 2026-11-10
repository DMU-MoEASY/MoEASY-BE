package com.moeasy.moeasybe.domain.group.service.support;

import com.moeasy.moeasybe.global.security.jwt.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class GroupFeedCursorCodec {
    private final byte[] key;
    public GroupFeedCursorCodec(JwtProperties properties) {
        this.key = properties.secret().getBytes(StandardCharsets.UTF_8);
    }

    public String encode(GroupFeedCursor c) {
        String raw = "feed-v1|" + c.memberId() + "|" + c.filter() + "|" + c.at() + "|"
                + position(c.normal()) + "|" + position(c.discovery()) + "|" + c.adSequence();
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }

    public GroupFeedCursor decode(String token, long memberId, String filter) {
        try {
            if (token.isBlank() || token.length() > 2048) throw new IllegalArgumentException();
            String[] signed = token.split("\\.", -1);
            if (signed.length != 2 || !MessageDigest.isEqual(sign(signed[0]), Base64.getUrlDecoder().decode(signed[1]))) {
                throw new IllegalArgumentException();
            }
            String[] p = new String(Base64.getUrlDecoder().decode(signed[0]), StandardCharsets.UTF_8).split("\\|", -1);
            if (p.length != 11 || !p[0].equals("feed-v1")) throw new IllegalArgumentException();
            GroupFeedCursor c = new GroupFeedCursor(Long.parseLong(p[1]), p[2], LocalDateTime.parse(p[3]),
                    parsePosition(p, 4), parsePosition(p, 7), Long.parseLong(p[10]));
            if (c.memberId() != memberId || !c.filter().equals(filter) || c.adSequence() < 0) {
                throw new IllegalArgumentException();
            }
            return c;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("잘못된 추천 커서입니다.", exception);
        }
    }

    private String position(GroupFeedCursor.Position p) {
        return p == null ? "||" : p.score() + "|" + p.createdAt() + "|" + p.id();
    }

    private GroupFeedCursor.Position parsePosition(String[] p, int offset) {
        if (p[offset].isEmpty() && p[offset + 1].isEmpty() && p[offset + 2].isEmpty()) return null;
        var position = new GroupFeedCursor.Position(Integer.parseInt(p[offset]),
                LocalDateTime.parse(p[offset + 1]), Long.parseLong(p[offset + 2]));
        if (position.score() < 0 || position.score() > 100 || position.id() <= 0) throw new IllegalArgumentException();
        return position;
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(("group-feed:" + payload).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
