package com.moeasy.moeasybe.domain.settlement.dto.response;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moeasy.moeasybe.domain.settlement.enums.SettlementItemStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentSource;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentType;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementSuccessCode;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

class SettlementPaymentResDTOTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("납부 이력 페이지는 취소된 기록의 원본 금액·등록자·취소 정보와 초 단위 시각을 제공한다")
    void paymentPage_cancelledPayment_serializesAgreedResponse() {
        // given
        var actor = SettlementResDTO.PaymentActor.builder().groupMemberId(11L).nickname("정규").build();
        var history = SettlementResDTO.PaymentHistory.builder()
                .paymentId(501L).settlementItemId(101L).paymentType(SettlementPaymentType.PAYMENT)
                .source(SettlementPaymentSource.MANUAL).status(SettlementPaymentStatus.CANCELLED)
                .amount(4000L)
                .occurredAt(LocalDateTime.of(2026, 10, 29, 14, 0, 0, 123456000))
                .createdAt(LocalDateTime.of(2026, 10, 29, 14, 5, 0, 123456000))
                .recordedBy(actor).cancelledBy(actor)
                .cancelledAt(LocalDateTime.of(2026, 10, 29, 15, 0, 0, 123456000))
                .cancelReason("다른 회원의 입금을 잘못 등록했습니다.").build();
        var page = SettlementResDTO.PaymentPage.builder().items(List.of(history))
                .page(0).size(20).totalElements(1).totalPages(1).hasNext(false).build();

        // when
        var json = objectMapper.readTree(objectMapper.writeValueAsString(
                ApiResponse.onSuccess(SettlementSuccessCode.PAYMENT_HISTORY_FETCHED, page)));

        // then
        assertAll(
                () -> assertEquals(HttpStatus.OK, SettlementSuccessCode.PAYMENT_HISTORY_FETCHED.getStatus()),
                () -> assertEquals(objectMapper.readTree("""
                        {
                          "isSuccess":true,"code":"SETTLEMENT200_5","message":"납부 이력 조회에 성공했습니다.",
                          "result":{
                            "items":[{
                              "paymentId":501,"settlementItemId":101,"paymentType":"PAYMENT",
                              "source":"MANUAL","status":"CANCELLED","amount":4000,
                              "occurredAt":"2026-10-29T14:00:00","createdAt":"2026-10-29T14:05:00",
                              "recordedBy":{"groupMemberId":11,"nickname":"정규"},
                              "cancelledAt":"2026-10-29T15:00:00",
                              "cancelledBy":{"groupMemberId":11,"nickname":"정규"},
                              "cancelReason":"다른 회원의 입금을 잘못 등록했습니다."
                            }],
                            "page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false
                          }
                        }
                        """), json)
        );
    }

    @Test
    @DisplayName("등록자·입금 시각을 모르는 기존 기록과 취소되지 않은 기록의 감사 정보는 null로 반환한다")
    void paymentHistory_missingOptionalInformation_serializesExplicitNulls() {
        // given
        var history = SettlementResDTO.PaymentHistory.builder()
                .paymentId(502L).settlementItemId(101L).paymentType(SettlementPaymentType.PAYMENT)
                .source(SettlementPaymentSource.BANK).status(SettlementPaymentStatus.COMPLETED)
                .amount(6000L).createdAt(LocalDateTime.of(2026, 10, 29, 14, 10)).build();

        // when
        var json = objectMapper.readTree(objectMapper.writeValueAsString(history));

        // then
        assertEquals(objectMapper.readTree("""
                {
                  "paymentId":502,"settlementItemId":101,"paymentType":"PAYMENT",
                  "source":"BANK","status":"COMPLETED","amount":6000,
                  "occurredAt":null,"createdAt":"2026-10-29T14:10:00","recordedBy":null,
                  "cancelledAt":null,"cancelledBy":null,"cancelReason":null
                }
                """), json);
    }

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
