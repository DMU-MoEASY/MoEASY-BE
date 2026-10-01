package com.moeasy.moeasybe.domain.region.service.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.moeasy.moeasybe.domain.region.dto.response.RegionResDTO;
import com.moeasy.moeasybe.domain.region.entity.Region;
import com.moeasy.moeasybe.domain.region.repository.RegionRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegionQueryServiceTest {

    @Mock
    private RegionRepository regionRepository;

    @InjectMocks
    private RegionQueryService regionQueryService;

    @Test
    @DisplayName("지역 목록은 조회 순서를 유지하며 시도별로 묶어서 반환한다")
    void getRegions_multipleProvinces_groupsRegionsInOrder() {
        // given
        when(regionRepository.findAllByDeletedAtIsNullOrderByProvinceNameAscNameAsc())
                .thenReturn(List.of(
                        Region.builder().code("41280").name("고양시").provinceName("경기도").build(),
                        Region.builder().code("11680").name("강남구").provinceName("서울특별시").build(),
                        Region.builder().code("11740").name("강동구").provinceName("서울특별시").build()
                ));

        // when
        List<RegionResDTO.Province> result = regionQueryService.getRegions();

        // then
        assertAll(
                () -> assertEquals(List.of("경기도", "서울특별시"),
                        result.stream().map(RegionResDTO.Province::provinceName).toList()),
                () -> assertEquals(List.of("41280"),
                        result.getFirst().regions().stream().map(RegionResDTO.Detail::code).toList()),
                () -> assertEquals(List.of("11680", "11740"),
                        result.get(1).regions().stream().map(RegionResDTO.Detail::code).toList()),
                () -> assertEquals(List.of("강남구", "강동구"),
                        result.get(1).regions().stream().map(RegionResDTO.Detail::name).toList())
        );
    }

    @Test
    @DisplayName("선택 가능한 지역이 없으면 빈 목록을 반환한다")
    void getRegions_noSelectableRegions_returnsEmptyList() {
        // given
        when(regionRepository.findAllByDeletedAtIsNullOrderByProvinceNameAscNameAsc())
                .thenReturn(List.of());

        // when
        List<RegionResDTO.Province> result = regionQueryService.getRegions();

        // then
        assertTrue(result.isEmpty());
    }
}
