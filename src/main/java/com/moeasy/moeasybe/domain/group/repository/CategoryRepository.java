package com.moeasy.moeasybe.domain.group.repository;

import com.moeasy.moeasybe.domain.group.entity.Category;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByCodeInAndDeletedAtIsNull(Collection<String> codes);

    Optional<Category> findByIdAndDeletedAtIsNull(Long id);
}
