package com.moeasy.moeasybe.domain.region.controller;

import com.moeasy.moeasybe.domain.region.dto.response.RegionResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Region", description = "지역 기준 데이터 API")
public interface RegionControllerDocs {

    @Operation(
            summary = "지역 목록 조회",
            description = "소프트 삭제되지 않은 지역을 시도별로 묶어서 반환합니다. "
                    + "시도명과 지역명 오름차순으로 정렬되며, 선택 가능한 지역이 없으면 result는 빈 배열입니다. "
                    + "지역의 code를 온보딩 요청의 regionCode로 전달합니다. "
                    + "로그인 후 moeasy_access_token 쿠키와 함께 호출해야 합니다. "
                    + "GET 요청이므로 CSRF 헤더는 필요하지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    useReturnTypeSchema = true,
                    description = "지역 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "REGION200_1",
                                      "message": "지역 목록 조회에 성공했습니다.",
                                      "result": [
                                        {
                                          "provinceName": "서울특별시",
                                          "regions": [
                                            { "code": "11680", "name": "강남구" },
                                            { "code": "11740", "name": "강동구" }
                                          ]
                                        }
                                      ]
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token 쿠키가 없거나 유효하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH401_1",
                                      "message": "인증이 필요합니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ResponseEntity<ApiResponse<List<RegionResDTO.Province>>> getRegions();
}
