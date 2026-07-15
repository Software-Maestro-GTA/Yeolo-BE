package com.soma.yeolo.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Google userinfo(v3) 응답. {@code sub}가 제공자 기준 사용자 고유 식별자(DOM-3 providerUserId). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleUserInfo(
        @JsonProperty("sub") String sub,
        @JsonProperty("email") String email,
        @JsonProperty("name") String name,
        @JsonProperty("picture") String picture,
        @JsonProperty("email_verified") Boolean emailVerified
) {
}
