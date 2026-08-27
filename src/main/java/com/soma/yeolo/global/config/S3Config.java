package com.soma.yeolo.global.config;

import com.soma.yeolo.user.client.S3ProfileImageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 클라이언트 (프로필 이미지 저장, API-USER-1).
 *
 * <p>{@code profile-image.provider=s3}일 때만 만든다 — 로컬(stub)에서는 AWS 자격증명이 없어
 * 빈 생성 자체가 실패하거나, 기동 때마다 자격증명 체인을 뒤지게 되기 때문이다.
 *
 * <p>자격증명은 SDK 기본 체인에 맡긴다(클러스터=IRSA, 로컬=AWS CLI 프로필). 리전은 명시값이
 * 있으면 그것을, 없으면 기본 체인({@code AWS_REGION} 등)을 쓴다.
 */
@Configuration
@ConditionalOnProperty(name = "profile-image.provider", havingValue = "s3")
public class S3Config {

    @Bean
    public S3Client s3Client(S3ProfileImageProperties properties) {
        var builder = S3Client.builder()
                // 동기 호출만 하므로 경량 클라이언트로 충분하다(Apache/Netty 의존성 제외, build.gradle).
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        if (StringUtils.hasText(properties.region())) {
            builder.region(Region.of(properties.region().strip()));
        }
        return builder.build();
    }
}
