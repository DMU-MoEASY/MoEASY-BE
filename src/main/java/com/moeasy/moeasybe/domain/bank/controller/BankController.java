package com.moeasy.moeasybe.domain.bank.controller;

import com.moeasy.moeasybe.domain.bank.controller.docs.BankControllerDocs;
import com.moeasy.moeasybe.domain.bank.dto.request.BankReqDTO;
import com.moeasy.moeasybe.domain.bank.dto.response.BankResDTO;
import com.moeasy.moeasybe.domain.bank.exception.code.BankSuccessCode;
import com.moeasy.moeasybe.domain.bank.service.command.BankCommandService;
import com.moeasy.moeasybe.domain.bank.service.command.BankSyncService;
import com.moeasy.moeasybe.domain.bank.service.query.BankQueryService;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/groups/{groupId}/bank-account")
public class BankController implements BankControllerDocs {
    private final BankCommandService commands;
    private final BankQueryService queries;
    private final BankSyncService syncService;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<BankResDTO.Account>> connect(
            @PathVariable Long groupId,
            @AuthenticationPrincipal Long memberId,
            @RequestBody BankReqDTO.Connect request) {
        return ResponseEntity.status(BankSuccessCode.CONNECTED.getStatus())
                .body(
                        ApiResponse.onSuccess(
                                BankSuccessCode.CONNECTED,
                                commands.connect(groupId, memberId, request)));
    }

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<BankResDTO.Account>> getAccount(
            @PathVariable Long groupId, @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.status(BankSuccessCode.FETCHED.getStatus())
                .body(
                        ApiResponse.onSuccess(
                                BankSuccessCode.FETCHED, queries.getAccount(groupId, memberId)));
    }

    @Override
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> disconnect(
            @PathVariable Long groupId, @AuthenticationPrincipal Long memberId) {
        commands.disconnect(groupId, memberId);
        return ResponseEntity.status(BankSuccessCode.DISCONNECTED.getStatus())
                .body(ApiResponse.onSuccess(BankSuccessCode.DISCONNECTED, null));
    }

    @Override
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<BankResDTO.Synced>> sync(
            @PathVariable Long groupId,
            @AuthenticationPrincipal Long memberId,
            @RequestBody BankReqDTO.Sync request) {
        return ResponseEntity.status(BankSuccessCode.SYNCED.getStatus())
                .body(
                        ApiResponse.onSuccess(
                                BankSuccessCode.SYNCED,
                                syncService.sync(groupId, memberId, request)));
    }

    @Override
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<BankResDTO.Transactions>> getTransactions(
            @PathVariable Long groupId,
            @AuthenticationPrincipal Long memberId,
            @ModelAttribute BankReqDTO.Search request) {
        return ResponseEntity.status(BankSuccessCode.TRANSACTIONS_FETCHED.getStatus())
                .body(
                        ApiResponse.onSuccess(
                                BankSuccessCode.TRANSACTIONS_FETCHED,
                                queries.getTransactions(groupId, memberId, request)));
    }
}
