package com.moeasy.moeasybe.domain.group.repository;

import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    Optional<GroupMember> findByGroupIdAndMemberId(Long groupId, Long memberId);

    long countByGroupIdAndStatusAndDeletedAtIsNull(Long groupId, GroupMemberStatus status);

    boolean existsByGroupIdAndMemberIdAndRoleAndStatus(Long groupId, Long memberId,
            GroupMemberRole role, GroupMemberStatus status);
}
