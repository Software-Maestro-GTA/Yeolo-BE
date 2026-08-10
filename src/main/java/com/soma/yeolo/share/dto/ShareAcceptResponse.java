package com.soma.yeolo.share.dto;

import java.util.UUID;

/**
 * 공유 링크 수락 응답의 {@code data} 페이로드 (API-SHARE-3). 수락한 코스의 식별자만 담는다 —
 * 앱은 이 값으로 코스 상세 화면(API-COURSE-2)으로 이동한다 (DOM-6 §시나리오 3).
 *
 * @param courseId 목록에 추가된 코스 식별자
 */
public record ShareAcceptResponse(String courseId) {

    public static ShareAcceptResponse of(UUID courseId) {
        return new ShareAcceptResponse(courseId.toString());
    }
}
