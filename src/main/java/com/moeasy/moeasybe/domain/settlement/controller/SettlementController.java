package com.moeasy.moeasybe.domain.settlement.controller;

import com.moeasy.moeasybe.domain.settlement.controller.docs.SettlementControllerDocs;
import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.dto.response.SettlementResDTO;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementSuccessCode;
import com.moeasy.moeasybe.domain.settlement.service.command.SettlementCommandService;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class SettlementController implements SettlementControllerDocs {

    private final SettlementCommandService settlementCommandService;

    @Override
    @PostMapping("/groups/{groupId}/settlements")
    public ResponseEntity<ApiResponse<SettlementResDTO.Created>> createSettlement(
            @PathVariable Long groupId,
            @Valid @RequestBody SettlementReqDTO.Create request,
            @AuthenticationPrincipal Long memberId
    ) {
        SettlementResDTO.Created result = settlementCommandService.createSettlement(groupId, request, memberId);

        return ResponseEntity.status(SettlementSuccessCode.SETTLEMENT_CREATED.getStatus())
                .body(ApiResponse.onSuccess(SettlementSuccessCode.SETTLEMENT_CREATED, result));
    }
}
