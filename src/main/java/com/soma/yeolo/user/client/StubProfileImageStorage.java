package com.soma.yeolo.user.client;

import com.soma.yeolo.user.domain.ProfileImage;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 프로필 이미지 저장 스텁(기본값). 외부 호출 없이 합성 URL만 돌려준다.
 *
 * <p>{@code profile-image.provider=s3}로 설정하면 실 저장소 구현이 대신 활성화된다. 스텁은 AWS
 * 자격증명·버킷 없이도 "업로드 → 검증 → profileImageUrl 갱신 → 응답" 전체 흐름을 로컬에서
 * 검증할 수 있게 한다({@code place.provider=stub}과 같은 방식).
 *
 * <p>반환 URL은 실제로 열리지 않는다 — 그림이 보여야 하는 화면 검증에는 쓸 수 없다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "profile-image.provider", havingValue = "stub", matchIfMissing = true)
public class StubProfileImageStorage implements ProfileImageStorage {

    private static final String BASE_URL = "https://stub.local/profile-images";

    @Override
    public String store(UUID userId, ProfileImage image) {
        log.debug("Profile image upload (stub): userId={}, bytes={}", userId, image.content().length);
        return "%s/%s/%s.%s".formatted(
                BASE_URL, userId, UUID.randomUUID(), image.format().getExtension());
    }
}
