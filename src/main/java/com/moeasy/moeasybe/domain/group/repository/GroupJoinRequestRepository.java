package com.moeasy.moeasybe.domain.group.repository;

import com.moeasy.moeasybe.domain.group.entity.GroupJoinRequest;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinRequestStatus;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupJoinRequestRepository extends JpaRepository<GroupJoinRequest, Long> {
    boolean existsByGroupIdAndMemberIdAndStatus(Long groupId, Long memberId, GroupJoinRequestStatus status);

    Optional<GroupJoinRequest> findByIdAndGroupId(Long id, Long groupId);

    Optional<GroupJoinRequest> findByGroupIdAndMemberIdAndStatus(
            Long groupId, Long memberId, GroupJoinRequestStatus status);

    Optional<GroupJoinRequest> findFirstByGroupIdAndMemberIdOrderByCreatedAtDescIdDesc(Long groupId, Long memberId);

    @EntityGraph(attributePaths = "member")
    Slice<GroupJoinRequest> findByGroupIdAndStatusOrderByCreatedAtAscIdAsc(
            Long groupId, GroupJoinRequestStatus status, Pageable pageable);
}
