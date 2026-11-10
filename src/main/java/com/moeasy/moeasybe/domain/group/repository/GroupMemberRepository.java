package com.moeasy.moeasybe.domain.group.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import java.time.LocalDateTime;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO membership_period(group_member_id,starts_at)
            SELECT gm.id,:at FROM group_member gm JOIN member m ON m.id=gm.member_id
            WHERE gm.id=:id AND gm.status='ACTIVE' AND gm.deleted_at IS NULL
            AND m.status='ACTIVE' AND m.deleted_at IS NULL
            """, nativeQuery = true)
    void openPeriod(Long id, LocalDateTime at);

    @Modifying
    @Query(value = """
            UPDATE membership_period SET ends_at=:at WHERE open_member=:id
            """, nativeQuery = true)
    void closePeriod(Long id, LocalDateTime at);

    @EntityGraph(attributePaths = "member")
    List<GroupMember> findAllByGroupIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
            Long groupId, GroupMemberStatus status);

    Optional<GroupMember> findByGroupIdAndMemberId(Long groupId, Long memberId);

    @Query(value = "SELECT COUNT(*) FROM group_member gm WHERE gm.group_id=:groupId AND "
            + GroupMembershipSql.ACTIVE, nativeQuery = true)
    long countActiveMembers(Long groupId);

    long countByGroupIdAndStatusAndDeletedAtIsNull(Long groupId, GroupMemberStatus status);

    boolean existsByGroupIdAndMemberIdAndRoleAndStatus(Long groupId, Long memberId,
            GroupMemberRole role, GroupMemberStatus status);

    Optional<GroupMember> findByGroupIdAndMemberIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
            Long groupId, Long memberId, GroupMemberStatus status);

    @EntityGraph(attributePaths = "member")
    List<GroupMember> findAllByGroupIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNullOrderByIdAsc(
            Long groupId, GroupMemberStatus status);

    @EntityGraph(attributePaths = "member")
    List<GroupMember> findAllByGroupIdAndIdInAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
            Long groupId, Collection<Long> ids, GroupMemberStatus status);
}
