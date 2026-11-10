package com.moeasy.moeasybe.domain.group.exception.code;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupSuccessCodeTest {

    @Test
    @DisplayName("모임 API는 서로 다른 성공 의미에 동일한 응답 코드를 재사용하지 않는다")
    void successCodes_differentOperations_haveUniqueCodes() {
        // given
        GroupSuccessCode[] codes = GroupSuccessCode.values();
        // when
        long uniqueCodes = Arrays.stream(codes).map(GroupSuccessCode::getCode).distinct().count();
        // then
        assertEquals(codes.length, uniqueCodes);
    }
}
