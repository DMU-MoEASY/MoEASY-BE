package com.moeasy.moeasybe.domain.group.repository;

/** 가입 승인 정원과 피드 현재 인원의 공통 집계 기준. 계정 상태와 무관하게 활성 미삭제 멤버십을 센다. */
public final class GroupMembershipSql {
    private GroupMembershipSql() {}
    public static final String ACTIVE = "gm.status='ACTIVE' AND gm.deleted_at IS NULL";
}
