package com.soma.yeolo.user.client;

import com.soma.yeolo.user.domain.ProfileImage;
import java.util.UUID;

/**
 * 프로필 이미지 저장 포트 (API-USER-1 / DOM-1 §"사용자가 직접 프로필 이미지를 업로드하면 서버가
 * 저장 후 profileImageUrl 을 생성한다").
 *
 * <p>외부 저장소(S3) 연동을 격리하는 인터페이스이며 {@code profile-image.provider} 설정으로
 * 구현체를 교체한다({@code PlaceLookupClient}와 같은 방식, docs/architecture.md §5).
 *
 * <p><b>구현 계약:</b> 조회 계열 포트와 달리 실패를 삼키지 않는다. 저장에 실패했는데 성공으로
 * 응답하면 존재하지 않는 URL이 사용자 프로필에 박히므로, 실패는 예외로 드러낸다.
 */
public interface ProfileImageStorage {

    /**
     * 이미지를 저장하고 공개적으로 접근 가능한 URL을 반환한다.
     *
     * @throws com.soma.yeolo.global.exception.BusinessException 저장 실패(500)
     */
    String store(UUID userId, ProfileImage image);
}
