package com.moeasy.moeasybe.domain.region.converter;

import com.moeasy.moeasybe.domain.region.dto.response.RegionResDTO;
import com.moeasy.moeasybe.domain.region.entity.Region;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public final class RegionConverter {

    private RegionConverter() {
    }

    public static List<RegionResDTO.Province> toResponseList(List<Region> regions) {
        return regions.stream()
                .collect(Collectors.groupingBy(Region::getProvinceName, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> RegionResDTO.Province.builder()
                        .provinceName(entry.getKey())
                        .regions(entry.getValue().stream().map(RegionConverter::toDetail).toList())
                        .build())
                .toList();
    }

    private static RegionResDTO.Detail toDetail(Region region) {
        return RegionResDTO.Detail.builder()
                .code(region.getCode())
                .name(region.getName())
                .build();
    }
}
