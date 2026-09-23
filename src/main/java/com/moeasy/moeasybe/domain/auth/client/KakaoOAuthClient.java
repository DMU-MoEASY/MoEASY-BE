package com.moeasy.moeasybe.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moeasy.moeasybe.domain.auth.exception.code.AuthErrorCode;
import com.moeasy.moeasybe.domain.auth.config.KakaoProperties;
import com.moeasy.moeasybe.domain.auth.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
public class KakaoOAuthClient {

    private static final String AUTHORIZATION_CODE_GRANT_TYPE = "authorization_code";

    private final RestClient restClient;
    private final KakaoProperties kakaoProperties;

    public KakaoOAuthClient(RestClient.Builder restClientBuilder, KakaoProperties kakaoProperties) {
        this.restClient = restClientBuilder.build();
        this.kakaoProperties = kakaoProperties;
    }

    public String getUserId(String authorizationCode) {
        String accessToken = requestAccessToken(authorizationCode);
        return requestUserId(accessToken);
    }

    private String requestAccessToken(String authorizationCode) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", AUTHORIZATION_CODE_GRANT_TYPE);
        formData.add("client_id", kakaoProperties.restApiKey());
        formData.add("redirect_uri", kakaoProperties.redirectUri());
        formData.add("code", authorizationCode);

        if (StringUtils.hasText(kakaoProperties.clientSecret())) {
            formData.add("client_secret", kakaoProperties.clientSecret());
        }

        try {
            KakaoTokenResponse response = restClient.post()
                    .uri(kakaoProperties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(KakaoTokenResponse.class);

            if (response == null || !StringUtils.hasText(response.accessToken())) {
                throw new AuthException(AuthErrorCode.KAKAO_SERVER_ERROR);
            }
            return response.accessToken();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                log.warn("카카오 인가 코드를 액세스 토큰으로 교환하지 못했습니다. status={}", ex.getStatusCode());
                throw new AuthException(AuthErrorCode.KAKAO_AUTHENTICATION_FAILED);
            }
            log.error("카카오 토큰 API 호출에 실패했습니다. status={}", ex.getStatusCode(), ex);
            throw new AuthException(AuthErrorCode.KAKAO_SERVER_ERROR);
        } catch (RestClientException ex) {
            log.error("카카오 토큰 API에 연결하지 못했습니다.", ex);
            throw new AuthException(AuthErrorCode.KAKAO_SERVER_ERROR);
        }
    }

    private String requestUserId(String accessToken) {
        try {
            KakaoUserResponse response = restClient.get()
                    .uri(kakaoProperties.userInfoUri())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(KakaoUserResponse.class);

            if (response == null || response.id() == null) {
                throw new AuthException(AuthErrorCode.KAKAO_SERVER_ERROR);
            }
            return response.id().toString();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                log.warn("카카오 액세스 토큰으로 사용자 정보를 조회하지 못했습니다. status={}", ex.getStatusCode());
                throw new AuthException(AuthErrorCode.KAKAO_AUTHENTICATION_FAILED);
            }
            log.error("카카오 사용자 정보 API 호출에 실패했습니다. status={}", ex.getStatusCode(), ex);
            throw new AuthException(AuthErrorCode.KAKAO_SERVER_ERROR);
        } catch (RestClientException ex) {
            log.error("카카오 사용자 정보 API에 연결하지 못했습니다.", ex);
            throw new AuthException(AuthErrorCode.KAKAO_SERVER_ERROR);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoUserResponse(
            Long id
    ) {
    }
}
