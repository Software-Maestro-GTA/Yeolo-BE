package com.soma.yeolo.auth.client;

import com.soma.yeolo.auth.client.dto.GoogleTokenResponse;
import com.soma.yeolo.auth.client.dto.GoogleUserInfo;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Google OAuth 호출 어댑터. 인가 코드로 토큰을 교환하고 사용자 프로필을 조회한다. (API-FB-1)
 * 4xx(코드 오류)는 {@code INVALID_GOOGLE_CODE}, 그 외 통신/서버 오류는 {@code GOOGLE_AUTH_SERVER_ERROR}.
 */
@Slf4j
@Component
public class GoogleOAuthClient {

    private final RestClient restClient;
    private final GoogleOAuthProperties properties;

    public GoogleOAuthClient(RestClient restClient, GoogleOAuthProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** 인가 코드 → Google 토큰 교환 → 사용자 프로필 조회. */
    public GoogleUserInfo authenticate(String code, String redirectUri) {
        GoogleTokenResponse token = exchangeCode(code, redirectUri);
        return fetchUserInfo(token.accessToken());
    }

    private GoogleTokenResponse exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", StringUtils.hasText(redirectUri) ? redirectUri : properties.redirectUri());
        form.add("grant_type", "authorization_code");

        try {
            GoogleTokenResponse response = restClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
            if (response == null || !StringUtils.hasText(response.accessToken())) {
                throw new BusinessException(ErrorCode.GOOGLE_AUTH_SERVER_ERROR);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw toBusinessException("token exchange", e);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google token exchange failed (connectivity)", e);
            throw new BusinessException(ErrorCode.GOOGLE_AUTH_SERVER_ERROR, e);
        }
    }

    private GoogleUserInfo fetchUserInfo(String accessToken) {
        try {
            GoogleUserInfo userInfo = restClient.get()
                    .uri(properties.userinfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleUserInfo.class);
            if (userInfo == null || !StringUtils.hasText(userInfo.sub())) {
                throw new BusinessException(ErrorCode.GOOGLE_AUTH_SERVER_ERROR);
            }
            return userInfo;
        } catch (RestClientResponseException e) {
            throw toBusinessException("userinfo", e);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google userinfo fetch failed (connectivity)", e);
            throw new BusinessException(ErrorCode.GOOGLE_AUTH_SERVER_ERROR, e);
        }
    }

    private BusinessException toBusinessException(String phase, RestClientResponseException e) {
        if (e.getStatusCode().is4xxClientError()) {
            log.warn("Google {} rejected request: {} {}", phase, e.getStatusCode(), e.getResponseBodyAsString());
            return new BusinessException(ErrorCode.INVALID_GOOGLE_CODE, e);
        }
        log.error("Google {} server error: {}", phase, e.getStatusCode(), e);
        return new BusinessException(ErrorCode.GOOGLE_AUTH_SERVER_ERROR, e);
    }
}
