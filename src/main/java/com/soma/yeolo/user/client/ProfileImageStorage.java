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

    /**
     * 사용자의 프로필 이미지를 <b>모두</b> 파기한다 (API-USER-2 회원탈퇴). 지울 것이 없으면
     * 아무것도 하지 않는다(멱등).
     *
     * <p>현재 이미지 한 장이 아니라 사용자 소유 객체 전부를 지운다 — 업로드마다 새 키를 발급하는
     * 구조라 교체된 옛 이미지가 저장소에 남아 있고, 그것들도 같은 사람의 사진이라 파기 대상이다.
     *
     * <p><b>구현 계약:</b> 저장과 마찬가지로 실패를 삼키지 않는다. 지우지 못했는데 탈퇴를 성공으로
     * 응답하면 파기했다고 알린 개인정보가 저장소에 그대로 남는다.
     *
     * @throws com.soma.yeolo.global.exception.BusinessException 삭제 실패(500)
     */
    void deleteAll(UUID userId);
}
