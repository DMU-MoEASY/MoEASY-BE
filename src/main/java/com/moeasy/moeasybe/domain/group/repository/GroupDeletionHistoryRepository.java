package com.moeasy.moeasybe.domain.group.repository;

import com.moeasy.moeasybe.domain.group.entity.GroupDeletionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupDeletionHistoryRepository extends JpaRepository<GroupDeletionHistory, Long> {
    boolean existsByGroupId(Long groupId);
}
