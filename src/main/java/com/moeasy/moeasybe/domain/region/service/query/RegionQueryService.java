package com.moeasy.moeasybe.domain.region.service.query;

import com.moeasy.moeasybe.domain.region.converter.RegionConverter;
import com.moeasy.moeasybe.domain.region.dto.response.RegionResDTO;
import com.moeasy.moeasybe.domain.region.repository.RegionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegionQueryService {

    private final RegionRepository regionRepository;

    @Transactional(readOnly = true)
    public List<RegionResDTO.Province> getRegions() {
        return RegionConverter.toResponseList(
                regionRepository.findAllByDeletedAtIsNullOrderByProvinceNameAscNameAsc()
        );
    }
}
