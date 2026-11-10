package com.moeasy.moeasybe.domain.group.controller.docs;

import com.moeasy.moeasybe.domain.group.dto.request.GroupFeedReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupFeedResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

@Tag(name = "Group", description = "모임 API")
public interface GroupFeedControllerDocs {
    @Operation(summary = "점수 기반 모임 추천 피드", description = """
            로그인 회원의 관심사·대표 지역·저장된 티어·최근 활동으로 정렬합니다. 크기는 10장으로 고정됩니다.
            일반 8 + 낮은 관련성 1 + 광고 1(10번째), 광고가 없으면 9+1, 낮은 관련성이 없으면 9+광고,
            둘 다 없으면 일반 10장입니다. 짧은 마지막 페이지는 일반 모임만 반환합니다.
            첫 요청: ?sort=RECOMMENDED / 다음 요청: 같은 필터에 &cursor={nextCursor}.
            광고 제외 totalGroupCount, 마지막 hasNext=false·nextCursor=null. 내부 점수는 반환하지 않습니다.
            categoryCodes=RUNNING,HIKING처럼 BE 코드를 전달합니다. regionCode는 /api/v1/regions의 5자리 코드입니다.
            검색은 모임명·소개·카테고리명·지역명에 적용됩니다. 필터를 바꾸면 커서를 버리고 처음부터 조회합니다.
            카드 JSON 및 첫 페이지·다음 페이지·광고 없는 응답 예시는 docs/api/group.md를 참고하세요.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true,
                    description = "추천 조회 성공",
                    content = @Content(examples = {
                            @ExampleObject(name = "첫 페이지 8+1+A", value = GroupFeedExamples.FIRST),
                            @ExampleObject(name = "다음 페이지 8+1+B", value = GroupFeedExamples.NEXT),
                            @ExampleObject(name = "마지막 페이지", value = GroupFeedExamples.LAST),
                            @ExampleObject(name = "광고 없음 9+1", value = GroupFeedExamples.NO_AD)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필터·정렬·커서 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "비활성 회원")
    })
    ResponseEntity<ApiResponse<GroupFeedResDTO.Feed>> getFeed(
            @Valid @ParameterObject GroupFeedReqDTO.Search request, @Parameter(hidden = true) Long memberId);
}
