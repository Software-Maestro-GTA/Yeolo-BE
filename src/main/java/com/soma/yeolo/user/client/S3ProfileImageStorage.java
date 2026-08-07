package com.soma.yeolo.user.client;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.user.domain.ProfileImage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3 프로필 이미지 저장 어댑터 (API-USER-1). {@code profile-image.provider=s3}일 때 활성화된다.
 *
 * <p>업로드마다 새 키를 발급한다(사용자별 고정 키가 아니라). 고정 키면 URL이 그대로여서 CDN·앱
 * 캐시에 남은 옛 이미지가 계속 보이고, 캐시 무효화를 매번 해야 한다. 키가 매번 달라지면 URL 자체가
 * 불변이라 장기 캐시가 가능하다.
 *
 * <p>대신 교체된 옛 객체는 남는다. 이건 <b>버킷 수명주기 규칙으로 지울 수 없다</b> — 나이 기준
 * 만료는 한 번 올리고 계속 쓰는 현재 이미지까지 지운다. 지우려면 {@code users.profile_image_url}과
 * 버킷 키를 대조하는 정리 작업이 필요하며, 지금은 만들지 않고 방치한다(사용자당 수 KB~MB 수준).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "profile-image.provider", havingValue = "s3")
public class S3ProfileImageStorage implements ProfileImageStorage {

    private final S3Client s3Client;
    private final S3ProfileImageProperties properties;

    @Override
    public String store(UUID userId, ProfileImage image) {
        String key = objectKey(userId, image);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            // 신뢰한 Content-Type 은 요청 헤더가 아니라 시그니처로 판정한 값이다.
                            // 이 값이 그대로 응답 헤더가 되므로, 클라이언트 주장을 그대로 쓰면
                            // 브라우저가 이미지가 아닌 것을 이미지로 해석하게 된다.
                            .contentType(image.format().getContentType())
                            .build(),
                    RequestBody.fromBytes(image.content()));
            return properties.baseUrl() + "/" + key;
        } catch (Exception e) {
            // 저장 실패를 성공으로 응답하면 존재하지 않는 URL이 프로필에 박힌다 — 500으로 드러낸다.
            log.error("프로필 이미지 업로드 실패. userId={}, key={}", userId, key, e);
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_UPLOAD_FAILED, e);
        }
    }

    /** {@code <prefix>/<userId>/<random>.<ext>} — 사용자별로 묶되 업로드마다 유일하게. */
    private String objectKey(UUID userId, ProfileImage image) {
        String prefix = properties.keyPrefix() == null ? "" : properties.keyPrefix().strip();
        String path = "%s/%s.%s".formatted(userId, UUID.randomUUID(), image.format().getExtension());
        return prefix.isEmpty() ? path : prefix + "/" + path;
    }
}
