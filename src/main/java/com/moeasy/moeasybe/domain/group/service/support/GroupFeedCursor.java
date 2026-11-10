package com.moeasy.moeasybe.domain.group.service.support;

import java.time.LocalDateTime;

public record GroupFeedCursor(long memberId, String filter, LocalDateTime at,
        Position normal, Position discovery, long adSequence) {
    public record Position(int score, LocalDateTime createdAt, long id) {}
}
