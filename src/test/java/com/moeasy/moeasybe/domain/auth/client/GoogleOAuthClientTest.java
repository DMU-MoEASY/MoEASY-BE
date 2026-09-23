package com.moeasy.moeasybe.domain.auth.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.moeasy.moeasybe.domain.auth.exception.code.AuthErrorCode;
import com.moeasy.moeasybe.domain.auth.config.GoogleProperties;
import com.moeasy.moeasybe.domain.auth.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class GoogleOAuthClientTest {

    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URI = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final String REDIRECT_URI = "https://dev.moeasy.kr/oauth/google/callback";

    private MockRestServiceServer server;
    private GoogleOAuthClient googleOAuthClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GoogleProperties properties = new GoogleProperties(
                "web-client-id",
                "client-secret",
                REDIRECT_URI,
                TOKEN_URI,
                USER_INFO_URI
        );
        googleOAuthClient = new GoogleOAuthClient(builder, properties);
    }

    @Test
    void 인가_코드로_구글_사용자_ID를_조회한다() {
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("grant_type", "authorization_code");
        expectedForm.add("client_id", "web-client-id");
        expectedForm.add("client_secret", "client-secret");
        expectedForm.add("redirect_uri", REDIRECT_URI);
        expectedForm.add("code", "authorization-code");

        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess("{\"access_token\":\"google-access-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer google-access-token"))
                .andRespond(withSuccess("{\"sub\":\"google-user-123\"}", MediaType.APPLICATION_JSON));

        String userId = googleOAuthClient.getUserId("authorization-code");

        assertEquals("google-user-123", userId);
        server.verify();
    }

    @Test
    void URL_인코딩된_인가_코드를_디코딩해서_토큰을_요청한다() {
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("grant_type", "authorization_code");
        expectedForm.add("client_id", "web-client-id");
        expectedForm.add("client_secret", "client-secret");
        expectedForm.add("redirect_uri", REDIRECT_URI);
        expectedForm.add("code", "4/0AbCdEf");

        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess("{\"access_token\":\"google-access-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer google-access-token"))
                .andRespond(withSuccess("{\"sub\":\"google-user-123\"}", MediaType.APPLICATION_JSON));

        String userId = googleOAuthClient.getUserId("4%2F0AbCdEf");

        assertEquals("google-user-123", userId);
        server.verify();
    }

    @Test
    void 유효하지_않은_인가_코드는_인증_실패로_변환한다() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest());

        AuthException exception = assertThrows(
                AuthException.class,
                () -> googleOAuthClient.getUserId("invalid-code")
        );

        assertEquals(AuthErrorCode.GOOGLE_AUTHENTICATION_FAILED, exception.getCode());
        server.verify();
    }
}
