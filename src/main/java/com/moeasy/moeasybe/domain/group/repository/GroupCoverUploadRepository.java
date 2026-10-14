package com.moeasy.moeasybe.domain.group.repository;

import com.moeasy.moeasybe.domain.group.entity.GroupCoverUpload;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupCoverUploadRepository extends JpaRepository<GroupCoverUpload, Long> {
    Optional<GroupCoverUpload> findByObjectKeyAndMemberId(String objectKey, Long memberId);
}
