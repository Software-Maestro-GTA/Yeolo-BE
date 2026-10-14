package com.soma.yeolo.location.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 도시 사진 URL 조립 설정.
 *
 * @param baseUrl 사진이 올라간 버킷의 공개 주소(CloudFront). 데이터셋(city_images.tsv)에는 환경에
 *                무관한 S3 키만 있고, URL 은 런타임에 {@code baseUrl + "/" + key} 로 만든다 — 그래야
 *                dev·prod 가 같은 TSV 를 쓴다. 비어 있으면 사진은 전부 빈 값으로 나간다.
 */
@ConfigurationProperties(prefix = "location.images")
public record LocationImageProperties(String baseUrl) {
}
