package com.moeasy.moeasybe.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CorsProperties corsProperties;

    @Test
    @DisplayName("Swagger는 Bearer 입력을 제공하고 보호 API와 공개 API의 인증 요구를 구분한다")
    void apiDocs_bearerSecurity_preservesPublicEndpoints() throws Exception {
        // when
        var response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        var document = objectMapper.readTree(response.getContentAsString());

        // then
        var scheme = document.at("/components/securitySchemes/bearerAuth");
        assertThat(scheme.path("type").asText()).isEqualTo("http");
        assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.path("bearerFormat").asText()).isEqualTo("JWT");
        var paths = document.path("paths");
        assertThat(paths.path("/api/v1/groups").path("post").at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(paths.path("/api/v1/groups/{groupId}/bank-account").path("get")
                .at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(paths.path("/api/v1/auth/csrf").path("get").path("security").isEmpty()).isTrue();
        for (String path : new String[]{"/api/v1/auth/oauth/states", "/api/v1/auth/oauth/kakao",
                "/api/v1/auth/oauth/google", "/api/v1/auth/reissue", "/api/v1/auth/logout"}) {
            var operation = paths.path(path).path("post");
            assertThat(operation.isMissingNode()).as(path).isFalse();
            assertThat(operation.path("security").isEmpty()).as(path).isTrue();
        }
    }

    @Test
    @DisplayName("허용된 출처의 Authorization·CSRF 헤더 사전 요청을 허용한다")
    void cors_allowedOrigin_allowsAuthorizationAndCsrf() throws Exception {
        // given
        String origin = corsProperties.allowedOrigins().getFirst();

        // when
        mockMvc.perform(options("/api/v1/groups")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,x-xsrf-token,content-type"))
                // then
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        "authorization, x-xsrf-token, content-type"));
    }

    @Test
    @DisplayName("Authorization 헤더를 추가해도 허용하지 않은 출처는 차단한다")
    void cors_untrustedOrigin_rejects() throws Exception {
        // when
        mockMvc.perform(options("/api/v1/groups")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                // then
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
