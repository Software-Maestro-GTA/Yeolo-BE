package com.soma.yeolo.user.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 기반 프로필 이미지 저장 설정 ({@code profile-image.provider=s3}일 때만 사용).
 *
 * <p>자격증명은 설정으로 받지 않는다 — 클러스터에서는 IRSA(ServiceAccount ↔ IAM Role)로,
 * 로컬에서는 AWS CLI 프로필로 SDK 기본 자격증명 체인이 해결한다. 액세스 키를 앱 설정에 두면
 * 키가 배포 산출물에 남고 로테이션도 앱 재배포에 묶인다.
 *
 * @param bucket    업로드 대상 버킷명
 * @param region    버킷 리전. 비우면 SDK 기본 리전 체인({@code AWS_REGION} 등)을 따른다
 * @param baseUrl   저장된 객체를 공개 제공하는 기준 URL (CloudFront 배포 도메인 등, 끝 슬래시 없이)
 * @param keyPrefix 오브젝트 키 접두사. 버킷을 다른 용도와 공유할 때 경로를 분리한다
 */
@ConfigurationProperties(prefix = "profile-image.s3")
public record S3ProfileImageProperties(String bucket, String region, String baseUrl, String keyPrefix) {
}
