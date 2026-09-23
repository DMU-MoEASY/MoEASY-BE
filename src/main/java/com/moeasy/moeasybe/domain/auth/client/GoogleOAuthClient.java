package com.moeasy.moeasybe.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moeasy.moeasybe.domain.auth.exception.code.AuthErrorCode;
import com.moeasy.moeasybe.domain.auth.config.GoogleProperties;
import com.moeasy.moeasybe.domain.auth.exception.AuthException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

@Component
@Slf4j
public class GoogleOAuthClient {

    private static final String AUTHORIZATION_CODE_GRANT_TYPE = "authorization_code";

    private final RestClient restClient;
    private final GoogleProperties googleProperties;

    public GoogleOAuthClient(RestClient.Builder restClientBuilder, GoogleProperties googleProperties) {
        this.restClient = restClientBuilder.build();
        this.googleProperties = googleProperties;
    }

    public String getUserId(String authorizationCode) {
        String accessToken = requestAccessToken(authorizationCode);
        return requestUserId(accessToken);
    }

    private String requestAccessToken(String authorizationCode) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", AUTHORIZATION_CODE_GRANT_TYPE);
        formData.add("client_id", googleProperties.webClientId());
        formData.add("client_secret", googleProperties.clientSecret());
        formData.add("redirect_uri", googleProperties.redirectUri());
        formData.add("code", decodeAuthorizationCode(authorizationCode));

        try {
            GoogleTokenResponse response = restClient.post()
                    .uri(googleProperties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(GoogleTokenResponse.class);

            if (response == null || !StringUtils.hasText(response.accessToken())) {
                throw new AuthException(AuthErrorCode.GOOGLE_SERVER_ERROR);
            }
            return response.accessToken();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                log.warn("구글 인가 코드를 액세스 토큰으로 교환하지 못했습니다. status={}", ex.getStatusCode());
                throw new AuthException(AuthErrorCode.GOOGLE_AUTHENTICATION_FAILED);
            }
            log.error("구글 토큰 API 호출에 실패했습니다. status={}", ex.getStatusCode(), ex);
            throw new AuthException(AuthErrorCode.GOOGLE_SERVER_ERROR);
        } catch (RestClientException ex) {
            log.error("구글 토큰 API에 연결하지 못했습니다.", ex);
            throw new AuthException(AuthErrorCode.GOOGLE_SERVER_ERROR);
        }
    }

    private String decodeAuthorizationCode(String authorizationCode) {
        try {
            return UriUtils.decode(authorizationCode, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            log.warn("구글 인가 코드의 URL 인코딩 형식이 올바르지 않습니다.");
            throw new AuthException(AuthErrorCode.GOOGLE_AUTHENTICATION_FAILED);
        }
    }

    private String requestUserId(String accessToken) {
        try {
            GoogleUserResponse response = restClient.get()
                    .uri(googleProperties.userInfoUri())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(GoogleUserResponse.class);

            if (response == null || !StringUtils.hasText(response.subject())) {
                throw new AuthException(AuthErrorCode.GOOGLE_SERVER_ERROR);
            }
            return response.subject();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                log.warn("구글 액세스 토큰으로 사용자 정보를 조회하지 못했습니다. status={}", ex.getStatusCode());
                throw new AuthException(AuthErrorCode.GOOGLE_AUTHENTICATION_FAILED);
            }
            log.error("구글 사용자 정보 API 호출에 실패했습니다. status={}", ex.getStatusCode(), ex);
            throw new AuthException(AuthErrorCode.GOOGLE_SERVER_ERROR);
        } catch (RestClientException ex) {
            log.error("구글 사용자 정보 API에 연결하지 못했습니다.", ex);
            throw new AuthException(AuthErrorCode.GOOGLE_SERVER_ERROR);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleUserResponse(
            @JsonProperty("sub") String subject
    ) {
    }
}
