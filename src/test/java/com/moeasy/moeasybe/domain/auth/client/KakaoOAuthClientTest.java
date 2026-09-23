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
import com.moeasy.moeasybe.domain.auth.config.KakaoProperties;
import com.moeasy.moeasybe.domain.auth.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class KakaoOAuthClientTest {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private static final String REDIRECT_URI = "https://dev.moeasy.kr/oauth/kakao/callback";

    private MockRestServiceServer server;
    private KakaoOAuthClient kakaoOAuthClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        KakaoProperties properties = new KakaoProperties(
                "rest-api-key",
                "client-secret",
                REDIRECT_URI,
                TOKEN_URI,
                USER_INFO_URI
        );
        kakaoOAuthClient = new KakaoOAuthClient(builder, properties);
    }

    @Test
    void 인가_코드로_카카오_사용자_ID를_조회한다() {
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("grant_type", "authorization_code");
        expectedForm.add("client_id", "rest-api-key");
        expectedForm.add("redirect_uri", REDIRECT_URI);
        expectedForm.add("code", "authorization-code");
        expectedForm.add("client_secret", "client-secret");

        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess("{\"access_token\":\"kakao-access-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer kakao-access-token"))
                .andRespond(withSuccess("{\"id\":123456789}", MediaType.APPLICATION_JSON));

        String userId = kakaoOAuthClient.getUserId("authorization-code");

        assertEquals("123456789", userId);
        server.verify();
    }

    @Test
    void 유효하지_않은_인가_코드는_인증_실패로_변환한다() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest());

        AuthException exception = assertThrows(
                AuthException.class,
                () -> kakaoOAuthClient.getUserId("invalid-code")
        );

        assertEquals(AuthErrorCode.KAKAO_AUTHENTICATION_FAILED, exception.getCode());
        server.verify();
    }
}
