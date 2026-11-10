package com.moeasy.moeasybe.domain.group.service.support;

/** 완성 가능한 10장 조합을 먼저 선택한다. 짧은 끝 페이지는 일반 모임만 소비한다. */
public record GroupFeedPage(int normalCount, boolean discovery, boolean advertisement, boolean hasNext) {
    public static GroupFeedPage select(int normalAvailable, boolean discoveryAvailable, boolean adAvailable) {
        int required = 10 - (discoveryAvailable ? 1 : 0) - (adAvailable ? 1 : 0);
        boolean full = normalAvailable >= required;
        int count = full ? required : normalAvailable;
        return new GroupFeedPage(count, full && discoveryAvailable, full && adAvailable, normalAvailable > count);
    }
}
