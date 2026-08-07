package com.soma.yeolo.user.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 프로필 이미지 업로드 공통 설정 (API-USER-1). provider 와 무관한 정책만 둔다.
 *
 * @param provider 저장소 구현 선택 ({@code stub} | {@code s3})
 * @param maxBytes 허용 최대 용량(바이트). 초과 시 명세의 413으로 거절한다
 */
@ConfigurationProperties(prefix = "profile-image")
public record ProfileImageProperties(String provider, long maxBytes) {
}
