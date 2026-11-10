package com.moeasy.moeasybe.domain.group.service.support;

import com.moeasy.moeasybe.domain.group.dto.request.GroupFeedReqDTO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public record GroupFeedFilter(String query, List<String> categoryCodes, String regionCode) {
    public static GroupFeedFilter from(GroupFeedReqDTO.Search input) {
        if (input == null || (input.sort() != null && !input.sort().equals("RECOMMENDED"))) {
            throw new IllegalArgumentException("잘못된 추천 조회 조건입니다.");
        }
        return new GroupFeedFilter(input.query() == null ? "" : input.query().strip().toLowerCase(Locale.ROOT),
                input.categoryCodes() == null ? List.of() : input.categoryCodes().stream().distinct().sorted().toList(),
                input.regionCode() == null ? "" : input.regionCode());
    }

    public String fingerprint() {
        try {
            String canonical = "RECOMMENDED\n" + query + "\n" + String.join(",", categoryCodes) + "\n" + regionCode;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
