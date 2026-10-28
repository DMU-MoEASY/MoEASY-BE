package com.moeasy.moeasybe.domain.settlement.dto.response;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moeasy.moeasybe.domain.settlement.enums.SettlementItemStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementStatus;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementSuccessCode;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SettlementLookupResDTOTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("목록 응답은 요약·페이지 정보만 제공하고 날짜를 초 단위 문자열로 반환한다")
    void page_summary_serializesAgreedResponse() {
        // given
        var summary = SettlementResDTO.Summary.builder()
                .settlementId(31L).title("러닝 후 식사비").scheduleId(7L)
                .status(SettlementStatus.OPEN).splitMode(SettlementSplitMode.EQUAL)
                .totalAmount(10000L).targetCount(3)
                .dueAt(LocalDateTime.of(2026, 11, 1, 23, 59, 59))
                .createdAt(LocalDateTime.of(2026, 10, 20, 14, 0, 0, 123456000))
                .build();
        var page = SettlementResDTO.Page.builder()
                .items(List.of(summary)).page(0).size(20)
                .totalElements(1L).totalPages(1).hasNext(false).build();

        // when
        JsonNode json = serialize(
                ApiResponse.onSuccess(SettlementSuccessCode.SETTLEMENT_LIST_FETCHED, page));

        // then
        assertAll(
                () -> assertEquals(HttpStatus.OK, SettlementSuccessCode.SETTLEMENT_LIST_FETCHED.getStatus()),
                () -> assertEquals(objectMapper.readTree("""
                        {
                          "isSuccess":true,"code":"SETTLEMENT200_2","message":"정산 목록 조회에 성공했습니다.",
                          "result":{
                            "items":[{
                              "settlementId":31,"title":"러닝 후 식사비","scheduleId":7,
                              "status":"OPEN","splitMode":"EQUAL","totalAmount":10000,
                              "targetCount":3,"dueAt":"2026-11-01T23:59:59","createdAt":"2026-10-20T14:00:00"
                            }],
                            "page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false
                          }
                        }
                        """), json)
        );
    }

    @Test
    @DisplayName("빈 목록은 items 빈 배열과 0건의 페이지 정보를 반환한다")
    void page_emptyResult_preservesEmptyArrayAndMetadata() {
        // given
        var page = SettlementResDTO.Page.builder()
                .items(List.of()).page(0).size(20)
                .totalElements(0L).totalPages(0).hasNext(false).build();

        // when
        JsonNode json = serialize(page);

        // then
        assertEquals(objectMapper.readTree("""
                {"items":[],"page":0,"size":20,"totalElements":0,"totalPages":0,"hasNext":false}
                """), json);
    }

    @Test
    @DisplayName("상세 응답은 생성자와 청구 항목 ID·저장 금액·상태를 반환하고 본인 전용 필드를 노출하지 않는다")
    void detail_items_serializesAgreedResponse() {
        // given
        var creator = SettlementResDTO.Creator.builder().groupMemberId(11L).nickname("정규").build();
        var item = SettlementResDTO.Item.builder()
                .settlementItemId(101L).groupMemberId(11L).nickname("정규")
                .amount(Long.MAX_VALUE).status(SettlementItemStatus.PENDING).build();
        var detail = SettlementResDTO.Detail.builder()
                .settlementId(31L).groupId(1L).scheduleId(7L)
                .title("개별 식사비").description("식사비 정산입니다.")
                .status(SettlementStatus.OPEN).splitMode(SettlementSplitMode.CUSTOM)
                .totalAmount(Long.MAX_VALUE).targetCount(1)
                .dueAt(LocalDateTime.of(2026, 11, 1, 23, 59, 59))
                .createdAt(LocalDateTime.of(2026, 10, 20, 14, 0, 0, 123456000))
                .createdBy(creator).items(List.of(item)).build();

        // when
        JsonNode json = serialize(
                ApiResponse.onSuccess(SettlementSuccessCode.SETTLEMENT_DETAIL_FETCHED, detail));

        // then
        assertAll(
                () -> assertEquals(HttpStatus.OK, SettlementSuccessCode.SETTLEMENT_DETAIL_FETCHED.getStatus()),
                () -> assertEquals(objectMapper.readTree("""
                        {
                          "isSuccess":true,"code":"SETTLEMENT200_3","message":"정산 상세 조회에 성공했습니다.",
                          "result":{
                            "settlementId":31,"groupId":1,"scheduleId":7,
                            "title":"개별 식사비","description":"식사비 정산입니다.",
                            "status":"OPEN","splitMode":"CUSTOM","totalAmount":9223372036854775807,
                            "targetCount":1,"dueAt":"2026-11-01T23:59:59","createdAt":"2026-10-20T14:00:00",
                            "createdBy":{"groupMemberId":11,"nickname":"정규"},
                            "items":[{"settlementItemId":101,"groupMemberId":11,"nickname":"정규",
                                      "amount":9223372036854775807,"status":"PENDING"}]
                          }
                        }
                        """), json)
        );
    }

    @Test
    @DisplayName("조회 응답의 선택 정보는 생략하지 않고 null로 반환한다")
    void lookup_optionalFields_serializesExplicitNulls() {
        // given
        var summary = SettlementResDTO.Summary.builder().build();
        var detail = SettlementResDTO.Detail.builder().build();

        // when
        JsonNode summaryJson = serialize(summary);
        JsonNode detailJson = serialize(detail);

        // then
        assertAll(
                () -> assertTrue(summaryJson.get("scheduleId").isNull()),
                () -> assertTrue(summaryJson.get("dueAt").isNull()),
                () -> assertTrue(detailJson.get("scheduleId").isNull()),
                () -> assertTrue(detailJson.get("description").isNull()),
                () -> assertTrue(detailJson.get("dueAt").isNull()),
                () -> assertFalse(summaryJson.has("myItem")),
                () -> assertFalse(detailJson.has("mySettlementItemId"))
        );
    }

    @Test
    @DisplayName("정산 없음 코드는 404 상태와 공통 실패 응답 형태를 사용한다")
    void notFound_code_serializesCommonFailure() {
        // given
        SettlementErrorCode code = SettlementErrorCode.SETTLEMENT_NOT_FOUND;

        // when
        JsonNode json = serialize(ApiResponse.onFailure(code, null));

        // then
        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, code.getStatus()),
                () -> assertEquals(objectMapper.readTree("""
                        {"isSuccess":false,"code":"SETTLEMENT404_1","message":"정산을 찾을 수 없습니다.","result":null}
                        """), json)
        );
    }

    private JsonNode serialize(Object value) {
        return objectMapper.readTree(objectMapper.writeValueAsString(value));
    }
}
