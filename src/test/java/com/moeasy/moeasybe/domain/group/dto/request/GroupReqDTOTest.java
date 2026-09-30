package com.moeasy.moeasybe.domain.group.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupReqDTOTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("PATCH에서 생략과 명시적 null을 구분한다")
    void update_absentAndNull_preservesPresence() throws Exception {
        // given
        String json = "{\"coverImageKey\":null,\"name\":\"새 이름\"}";

        // when
        GroupReqDTO.Update request = objectMapper.readValue(json, GroupReqDTO.Update.class);

        // then
        assertThat(request.has("coverImageKey")).isTrue();
        assertThat(request.has("name")).isTrue();
        assertThat(request.has("latitude")).isFalse();
        assertThat(request.value("coverImageKey").isNull()).isTrue();
    }
}
