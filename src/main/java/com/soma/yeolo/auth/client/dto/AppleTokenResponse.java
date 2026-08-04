package com.soma.yeolo.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Apple 토큰 엔드포인트 응답. 사용자 식별 정보는 {@code id_token}(JWT) 안에 담겨 온다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Integer expiresIn,
        @JsonProperty("id_token") String idToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType
) {
}
