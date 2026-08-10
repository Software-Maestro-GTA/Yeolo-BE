package com.soma.yeolo.share.domain;

import java.util.UUID;

/**
 * 공유 수락으로 부여된 코스 접근 권한 (DOM-6 §권한 정책). 코스 소유권과 달리 "내 목록에 추가된
 * 남의 코스"를 뜻하며, 제거해도 원본 코스는 삭제되지 않는다.
 *
 * @param courseId    접근이 허용된 코스
 * @param userId      권한을 받은 사용자
 * @param shareLinkId 어떤 초대 링크로 들어왔는지 (추적용)
 */
public record CourseAccess(
        UUID courseId,
        UUID userId,
        UUID shareLinkId
) {
}
