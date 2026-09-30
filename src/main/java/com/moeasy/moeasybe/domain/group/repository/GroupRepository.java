package com.moeasy.moeasybe.domain.group.repository;

import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, Long> {
    Optional<Group> findByIdAndStatusNotAndDeletedAtIsNull(Long id, GroupStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Group g where g.id = :id")
    Optional<Group> findByIdForUpdate(@Param("id") Long id);
}
