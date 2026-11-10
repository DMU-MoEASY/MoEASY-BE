package com.moeasy.moeasybe.domain.bank.controller.docs;

import com.moeasy.moeasybe.domain.bank.dto.request.BankReqDTO;
import com.moeasy.moeasybe.domain.bank.dto.response.BankResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

@Tag(name = "Bank", description = "모임 계좌 연결·거래내역 관리")
public interface BankControllerDocs {
    @Operation(
            summary = "모임 계좌 연결",
            description =
                    "활성 OWNER/MANAGER 전용. 키와 계좌번호는 암호화하여 보관합니다. "
                            + "실제 키 검증과 거래 조회는 /sync에서 수행합니다. 이 API 자체는 은행을 호출하지 않습니다.",
            parameters =
                    @Parameter(
                            name = "X-XSRF-TOKEN",
                            in = ParameterIn.HEADER,
                            required = true,
                            description = "CSRF 발급 응답의 token"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "BANK201_1: 연결됨"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "입력 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "운영진 아님 또는 CSRF 누락"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "모임 없음·폐쇄·삭제"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 연결됨 또는 다른 모임의 계좌"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "503",
                description = "암호화 설정 오류")
    })
    ResponseEntity<ApiResponse<BankResDTO.Account>> connect(
            @Positive(message = "모임 ID는 양수여야 합니다.") Long groupId,
            @Parameter(hidden = true) Long memberId,
            @Valid BankReqDTO.Connect request);

    @Operation(
            summary = "모임 계좌 정보 조회",
            description = "모든 활성 모임 회원에게 공개합니다. 계좌번호는 끝 4자리만 표시하고 비밀정보는 반환하지 않습니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "BANK200_1: 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "활성 모임 회원 아님"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "모임 또는 연결 계좌 없음")
    })
    ResponseEntity<ApiResponse<BankResDTO.Account>> getAccount(
            @Positive(message = "모임 ID는 양수여야 합니다.") Long groupId,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "모임 계좌 연결 해제",
            description = "활성 OWNER/MANAGER 전용. 인증정보는 제거하고 거래내역은 보존합니다. 진행 중 동기화가 있으면 409입니다.",
            parameters =
                    @Parameter(
                            name = "X-XSRF-TOKEN",
                            in = ParameterIn.HEADER,
                            required = true,
                            description = "CSRF 발급 응답의 token"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "BANK200_2: 해제 성공, result=null"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "운영진 아님 또는 CSRF 누락"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "모임 또는 연결 계좌 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "동기화 진행 중")
    })
    ResponseEntity<ApiResponse<Void>> disconnect(
            @Positive(message = "모임 ID는 양수여야 합니다.") Long groupId,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "은행 거래내역 수동 동기화",
            description =
                    "활성 OWNER/MANAGER 전용. 오늘까지 최대 31일을 조회합니다. 개인 계좌는 residentNumber(생년월일 6자리),"
                        + " accountType=personal, 사업자는 10자리/business를 전달합니다. SH는"
                        + " loginId/loginPassword를 전달합니다. 계좌 비밀번호 등은 저장하지 않습니다. 자동 납부 처리는 하지 않습니다.",
            parameters =
                    @Parameter(
                            name = "X-XSRF-TOKEN",
                            in = ParameterIn.HEADER,
                            required = true,
                            description = "CSRF 발급 응답의 token"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "BANK200_3: 동기화 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "날짜 또는 은행별 필수 입력 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "운영진 아님 또는 CSRF 누락"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "모임 또는 연결 계좌 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "동기화 진행 중"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422",
                description = "은행 조회 거절 또는 API 키 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "조회 제한, 계좌 정보의 nextSyncAt 확인"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "502",
                description = "은행 장애·비정상 응답·타임아웃"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "503",
                description = "암호화·Redis 사용 불가")
    })
    ResponseEntity<ApiResponse<BankResDTO.Synced>> sync(
            @Positive(message = "모임 ID는 양수여야 합니다.") Long groupId,
            @Parameter(hidden = true) Long memberId,
            @Valid BankReqDTO.Sync request);

    @Operation(
            summary = "저장된 계좌 거래내역 조회",
            description = "모든 활성 모임 회원에게 공개합니다. 은행 호출 없이 DB에서 최신 거래 시각·ID 순으로 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "BANK200_4: 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "페이지 입력 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "활성 모임 회원 아님"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "모임 또는 연결 계좌 없음")
    })
    ResponseEntity<ApiResponse<BankResDTO.Transactions>> getTransactions(
            @Positive(message = "모임 ID는 양수여야 합니다.") Long groupId,
            @Parameter(hidden = true) Long memberId,
            @Valid @ParameterObject BankReqDTO.Search request);
}
