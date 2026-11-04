package com.moeasy.moeasybe.domain.schedule.service.support;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

public record ScheduleCursor(Long groupId, LocalDateTime from, LocalDateTime to,
        LocalDateTime createdAt, Long id) {
    public String encode() {
        String raw = "1|" + groupId + "|" + from + "|" + to + "|" + createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
    public static ScheduleCursor decode(String token) {
        try {
            if (token.isBlank() || token.length() > 512) throw new IllegalArgumentException("잘못된 커서입니다.");
            String[] parts = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 6 || !parts[0].equals("1")) throw new IllegalArgumentException("잘못된 커서입니다.");
            ScheduleCursor c = new ScheduleCursor(Long.valueOf(parts[1]), LocalDateTime.parse(parts[2]),
                    LocalDateTime.parse(parts[3]), LocalDateTime.parse(parts[4]), Long.valueOf(parts[5]));
            if (c.groupId <= 0 || c.id <= 0 || !c.from.isBefore(c.to)) throw new IllegalArgumentException("잘못된 커서입니다.");
            return c;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("잘못된 커서입니다.");
        }
    }
}
