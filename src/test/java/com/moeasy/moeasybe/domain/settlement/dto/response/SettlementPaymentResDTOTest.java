package com.moeasy.moeasybe.domain.settlement.dto.response;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moeasy.moeasybe.domain.settlement.enums.SettlementItemStatus;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementSuccessCode;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

class SettlementPaymentResDTOTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("납부 등록 응답은 생성한 기록 ID와 등록 후 누적 청구 현황을 제공한다")
    void paymentRegistered_partialPayment_serializesAgreedResponse() {
        // given
        var result = SettlementResDTO.PaymentRegistered.builder()
                .paymentId(501L).settlementItemId(101L).status(SettlementItemStatus.PENDING)
                .paidAmount(4000L).remainingAmount(6000L).version(1L).build();

        // when
        var json = objectMapper.readTree(objectMapper.writeValueAsString(
                ApiResponse.onSuccess(SettlementSuccessCode.PAYMENT_REGISTERED, result)));

        // then
        assertAll(
                () -> assertEquals(HttpStatus.CREATED, SettlementSuccessCode.PAYMENT_REGISTERED.getStatus()),
                () -> assertEquals(objectMapper.readTree("""
                        {
                          "isSuccess":true,"code":"SETTLEMENT201_2","message":"납부 기록 등록에 성공했습니다.",
                          "result":{
                            "paymentId":501,"settlementItemId":101,"status":"PENDING",
                            "paidAmount":4000,"remainingAmount":6000,"version":1
                          }
                        }
                        """), json)
        );
    }

    @Test
    @DisplayName("납부 취소 응답은 취소한 기록 ID와 나머지 유효 납부액·청구 상태를 제공한다")
    void paymentCancelled_remainingPayment_serializesAgreedResponse() {
        // given
        var result = SettlementResDTO.PaymentCancelled.builder()
                .paymentId(501L).settlementItemId(101L).status(SettlementItemStatus.PENDING)
                .paidAmount(6000L).remainingAmount(4000L).version(3L).build();

        // when
        var json = objectMapper.readTree(objectMapper.writeValueAsString(
                ApiResponse.onSuccess(SettlementSuccessCode.PAYMENT_CANCELLED, result)));

        // then
        assertAll(
                () -> assertEquals(HttpStatus.OK, SettlementSuccessCode.PAYMENT_CANCELLED.getStatus()),
                () -> assertEquals(objectMapper.readTree("""
                        {
                          "isSuccess":true,"code":"SETTLEMENT200_4","message":"납부 기록 취소에 성공했습니다.",
                          "result":{
                            "paymentId":501,"settlementItemId":101,"status":"PENDING",
                            "paidAmount":6000,"remainingAmount":4000,"version":3
                          }
                        }
                        """), json)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "PAYMENT_AMOUNT_EXCEEDS_REMAINING, 400, SETTLEMENT400_6",
            "INVALID_OCCURRED_AT, 400, SETTLEMENT400_7",
            "ITEM_NOT_FOUND, 404, SETTLEMENT404_2",
            "ITEM_VERSION_CONFLICT, 409, SETTLEMENT409_1",
            "PAYMENT_NOT_ALLOWED, 409, SETTLEMENT409_2"
    })
    @DisplayName("납부 오류는 계약의 HTTP 상태·도메인 코드와 공통 실패 응답을 사용한다")
    void paymentError_code_serializesCommonFailure(String name, int status, String expectedCode) {
        // given
        SettlementErrorCode code = SettlementErrorCode.valueOf(name);

        // when
        var json = objectMapper.readTree(objectMapper.writeValueAsString(ApiResponse.onFailure(code, null)));

        // then
        assertAll(
                () -> assertEquals(status, code.getStatus().value()),
                () -> assertEquals(objectMapper.readTree("""
                        {"isSuccess":false,"code":"%s","message":%s,"result":null}
                        """.formatted(expectedCode, objectMapper.writeValueAsString(code.getMessage()))), json)
        );
    }
}
