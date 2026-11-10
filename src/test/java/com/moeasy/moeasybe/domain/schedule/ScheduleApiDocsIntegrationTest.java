package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleApiDocsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @ParameterizedTest
    @CsvSource({
            "/api/v1/schedules/{scheduleId}/availability-poll,post",
            "/api/v1/schedules/{scheduleId}/availability-poll,patch",
            "/api/v1/schedules/{scheduleId}/availability-poll,delete",
            "/api/v1/schedules/{scheduleId}/availability/me,put",
            "/api/v1/schedules/{scheduleId}/availability/me,delete",
            "/api/v1/schedules/{scheduleId}/availability-poll/confirm,post"
    })
    @DisplayName("가용시간 쓰기 API의 Swagger에 CSRF 헤더와 토큰 발급 방법을 제공한다")
    void apiDocs_availabilityWrites_exposesCsrfHeader(String path, String method) throws Exception {
        // given
        var request = get("/v3/api-docs");

        // when
        var response = mvc.perform(request).andReturn().getResponse();
        JsonNode operation = mapper.readTree(response.getContentAsString()).path("paths").path(path).path(method);
        JsonNode csrf = null;
        for (JsonNode parameter : operation.path("parameters")) {
            if (parameter.path("name").asText().equals("X-XSRF-TOKEN")) csrf = parameter;
        }
        JsonNode header = csrf;

        // then
        org.junit.jupiter.api.Assertions.assertNotNull(header);
        assertAll(
                () -> assertEquals(200, response.getStatus()),
                () -> assertEquals("header", header.path("in").asText()),
                () -> assertTrue(header.path("required").asBoolean()),
                () -> assertEquals("string", header.path("schema").path("type").asText()),
                () -> assertTrue(operation.path("description").asText().contains("GET /api/v1/auth/csrf")));
    }

    @Test
    @DisplayName("일정 요청·응답 스키마가 그룹 DTO와 충돌하지 않는다")
    void apiDocs_scheduleSchemas_preservesDistinctGroupSchemas() throws Exception {
        // given
        var request = get("/v3/api-docs");

        // when
        var response = mvc.perform(request).andReturn().getResponse();
        JsonNode document = mapper.readTree(response.getContentAsString());
        JsonNode createOperation = document.path("paths").path("/api/v1/groups/{groupId}/schedules").path("post");
        JsonNode detailOperation = document.path("paths").path("/api/v1/schedules/{scheduleId}").path("get");
        JsonNode listOperation = document.path("paths").path("/api/v1/groups/{groupId}/schedules").path("get");
        JsonNode groupCreate = document.path("paths").path("/api/v1/groups").path("post");
        JsonNode groupDetail = document.path("paths").path("/api/v1/groups/{groupId}").path("get");

        // then
        assertAll(
                () -> assertEquals(200, response.getStatus()),
                () -> assertEquals("#/components/schemas/ScheduleCreate", requestSchema(createOperation).path("$ref").asText()),
                () -> assertTrue(resolve(document, requestSchema(createOperation)).path("properties").has("startsAt")),
                () -> assertEquals("#/components/schemas/ScheduleCreated", resultSchema(document, createOperation, "201").path("$ref").asText()),
                () -> assertTrue(resolve(document, resultSchema(document, createOperation, "201")).path("properties").has("scheduleId")),
                () -> assertEquals("#/components/schemas/ScheduleDetail", resultSchema(document, detailOperation, "200").path("$ref").asText()),
                () -> assertTrue(resolve(document, resultSchema(document, detailOperation, "200")).path("properties").has("endsAt")),
                () -> assertEquals("#/components/schemas/SchedulePage", resultSchema(document, listOperation, "200").path("$ref").asText()),
                () -> assertEquals("#/components/schemas/ScheduleSummary", resolve(document,
                        resultSchema(document, listOperation, "200")).path("properties").path("items").path("items").path("$ref").asText()),
                () -> assertTrue(resolve(document, requestSchema(groupCreate)).path("properties").has("categoryId")),
                () -> assertFalse(resolve(document, requestSchema(groupCreate)).path("properties").has("startsAt")),
                () -> assertTrue(resolve(document, groupDetail.path("responses").path("200").path("content").properties().iterator().next().getValue().path("schema")).path("properties").has("maxMembers")),
                () -> assertFalse(resolve(document, groupDetail.path("responses").path("200").path("content").properties().iterator().next().getValue().path("schema")).path("properties").has("scheduleId"))
        );
    }

    @Test
    @DisplayName("PATCH 스키마는 선택적인 최상위 필드와 명시적 null을 설명한다")
    void apiDocs_updateSchema_describesFlatPartialRequest() throws Exception {
        // given
        var request = get("/v3/api-docs");

        // when
        var response = mvc.perform(request).andReturn().getResponse();
        JsonNode document = mapper.readTree(response.getContentAsString());
        JsonNode operation = document.path("paths").path("/api/v1/schedules/{scheduleId}").path("patch");
        JsonNode schema = resolve(document, requestSchema(operation));
        JsonNode properties = schema.path("properties");
        Set<String> names = new HashSet<>();
        properties.properties().forEach(entry -> names.add(entry.getKey()));
        JsonNode examples = operation.path("requestBody").path("content").path("application/json").path("examples");

        // then
        assertAll(
                () -> assertEquals(200, response.getStatus()),
                () -> assertEquals("#/components/schemas/ScheduleUpdate", requestSchema(operation).path("$ref").asText()),
                () -> assertEquals(Set.of("title", "description", "startsAt", "endsAt", "locationName",
                        "locationAddress", "latitude", "longitude", "placeProvider", "placeId"), names),
                () -> assertEquals(0, schema.path("required").size()),
                () -> assertEquals(1, schema.path("minProperties").intValue()),
                () -> assertFalse(schema.path("additionalProperties").asBoolean(true)),
                () -> assertEquals(Set.of("number", "null"), mapper.convertValue(properties.path("latitude").path("type"), Set.class)),
                () -> assertEquals(Set.of("string", "null"), mapper.convertValue(properties.path("placeId").path("type"), Set.class)),
                () -> assertEquals("변경한 모임", examples.path("제목 변경").path("value").path("title").asText()),
                () -> assertTrue(examples.path("좌표 제거").path("value").path("latitude").isNull())
        );
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/groups/{groupId}/schedules, get, 200, false",
            "/api/v1/groups/{groupId}/schedules, post, 201, true",
            "/api/v1/schedules/{scheduleId}, get, 200, false",
            "/api/v1/schedules/{scheduleId}, patch, 200, true",
            "/api/v1/schedules/{scheduleId}/cancel, post, 200, true",
            "/api/v1/schedules/{scheduleId}, delete, 200, true"
    })
    @DisplayName("각 일정 API는 실제 성공·실패 상태를 문서화하고 인증 principal을 숨긴다")
    void apiDocs_operations_describesResponsesAndHidesPrincipal(
            String path, String method, String success, boolean write) throws Exception {
        // given
        var request = get("/v3/api-docs");

        // when
        var response = mvc.perform(request).andReturn().getResponse();
        JsonNode document = mapper.readTree(response.getContentAsString());
        JsonNode operation = document.path("paths").path(path).path(method);
        Set<String> codes = new HashSet<>();
        operation.path("responses").properties().forEach(entry -> codes.add(entry.getKey()));
        Set<String> parameters = new HashSet<>();
        operation.path("parameters").forEach(parameter -> parameters.add(parameter.path("name").asText()));

        // then
        assertAll(
                () -> assertEquals(200, response.getStatus()),
                () -> assertEquals(write ? Set.of(success, "400", "401", "403", "404", "409")
                        : Set.of(success, "400", "401", "403", "404"), codes),
                () -> assertFalse(parameters.contains("memberId")),
                () -> assertTrue(operation.path("responses").path(success).path("content").has("application/json"))
        );
    }

    private JsonNode requestSchema(JsonNode operation) {
        return operation.path("requestBody").path("content").path("application/json").path("schema");
    }

    private JsonNode resultSchema(JsonNode document, JsonNode operation, String status) {
        JsonNode envelope = operation.path("responses").path(status).path("content").path("application/json").path("schema");
        return resolve(document, envelope).path("properties").path("result");
    }

    private JsonNode resolve(JsonNode document, JsonNode schema) {
        String ref = schema.path("$ref").asText();
        return ref.isEmpty() ? schema : document.path("components").path("schemas").path(ref.substring(ref.lastIndexOf('/') + 1));
    }
}
