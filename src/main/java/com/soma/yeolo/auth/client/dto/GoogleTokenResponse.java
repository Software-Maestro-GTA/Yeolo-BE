package com.soma.yeolo.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Google 토큰 엔드포인트 응답. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Integer expiresIn,
        @JsonProperty("id_token") String idToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("scope") String scope
) {
}
