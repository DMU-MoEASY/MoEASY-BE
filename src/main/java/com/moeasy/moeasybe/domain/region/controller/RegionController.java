package com.moeasy.moeasybe.domain.region.controller;

import com.moeasy.moeasybe.domain.region.dto.response.RegionResDTO;
import com.moeasy.moeasybe.domain.region.exception.code.RegionSuccessCode;
import com.moeasy.moeasybe.domain.region.service.query.RegionQueryService;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController implements RegionControllerDocs {

    private final RegionQueryService regionQueryService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<List<RegionResDTO.Province>>> getRegions() {
        List<RegionResDTO.Province> result = regionQueryService.getRegions();
        return ResponseEntity.status(RegionSuccessCode.REGION_LIST_FETCH_SUCCESS.getStatus())
                .body(ApiResponse.onSuccess(RegionSuccessCode.REGION_LIST_FETCH_SUCCESS, result));
    }
}
