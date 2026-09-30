package com.moeasy.moeasybe.domain.region.repository;

import com.moeasy.moeasybe.domain.region.entity.Region;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {

    List<Region> findAllByDeletedAtIsNullOrderByProvinceNameAscNameAsc();
}
