package com.moeasy.moeasybe.domain.region.repository;

import com.moeasy.moeasybe.domain.region.entity.Region;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByCodeAndDeletedAtIsNull(String code);

    List<Region> findAllByDeletedAtIsNullOrderByProvinceNameAscNameAsc();
}
