package com.moeasy.moeasybe.domain.group.repository;

import com.moeasy.moeasybe.domain.group.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
