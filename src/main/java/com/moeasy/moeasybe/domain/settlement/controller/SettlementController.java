package com.moeasy.moeasybe.domain.settlement.controller;

import com.moeasy.moeasybe.domain.settlement.controller.docs.SettlementControllerDocs;
import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.dto.response.SettlementResDTO;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementSuccessCode;
import com.moeasy.moeasybe.domain.settlement.service.command.SettlementCommandService;
import com.moeasy.moeasybe.domain.settlement.service.query.SettlementQueryService;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1")
public class SettlementController implements SettlementControllerDocs {

    private final SettlementCommandService settlementCommandService;
    private final SettlementQueryService settlementQueryService;

    @Override
    @GetMapping("/groups/{groupId}/settlements/{settlementId}")
    public ResponseEntity<ApiResponse<SettlementResDTO.Detail>> getSettlement(
            @PathVariable Long groupId,
            @PathVariable Long settlementId,
            @AuthenticationPrincipal Long memberId
    ) {
        SettlementResDTO.Detail result = settlementQueryService.getSettlement(groupId, settlementId, memberId);
        return ResponseEntity.status(SettlementSuccessCode.SETTLEMENT_DETAIL_FETCHED.getStatus())
                .body(ApiResponse.onSuccess(SettlementSuccessCode.SETTLEMENT_DETAIL_FETCHED, result));
    }

    @Override
    @GetMapping("/groups/{groupId}/settlements")
    public ResponseEntity<ApiResponse<SettlementResDTO.Page>> getSettlements(
            @PathVariable Long groupId,
            @ModelAttribute SettlementReqDTO.Search request,
            @AuthenticationPrincipal Long memberId
    ) {
        SettlementResDTO.Page result = settlementQueryService.getSettlements(groupId, request, memberId);
        return ResponseEntity.status(SettlementSuccessCode.SETTLEMENT_LIST_FETCHED.getStatus())
                .body(ApiResponse.onSuccess(SettlementSuccessCode.SETTLEMENT_LIST_FETCHED, result));
    }

    @Override
    @PostMapping("/groups/{groupId}/settlements")
    public ResponseEntity<ApiResponse<SettlementResDTO.Created>> createSettlement(
            @PathVariable Long groupId,
            @RequestBody SettlementReqDTO.Create request,
            @AuthenticationPrincipal Long memberId
    ) {
        SettlementResDTO.Created result = settlementCommandService.createSettlement(groupId, request, memberId);

        return ResponseEntity.status(SettlementSuccessCode.SETTLEMENT_CREATED.getStatus())
                .body(ApiResponse.onSuccess(SettlementSuccessCode.SETTLEMENT_CREATED, result));
    }

    @Override
    @PostMapping("/groups/{groupId}/settlements/preview")
    public ResponseEntity<ApiResponse<SettlementResDTO.Preview>> previewSplit(
            @PathVariable Long groupId,
            @RequestBody SettlementReqDTO.Preview request,
            @AuthenticationPrincipal Long memberId
    ) {
        SettlementResDTO.Preview result = settlementQueryService.previewSplit(groupId, request, memberId);

        return ResponseEntity.status(SettlementSuccessCode.SPLIT_PREVIEWED.getStatus())
                .body(ApiResponse.onSuccess(SettlementSuccessCode.SPLIT_PREVIEWED, result));
    }
}
