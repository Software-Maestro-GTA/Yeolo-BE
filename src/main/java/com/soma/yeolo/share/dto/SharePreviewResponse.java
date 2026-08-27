package com.soma.yeolo.share.dto;

import com.soma.yeolo.course.domain.SavedCourse;
import com.soma.yeolo.share.domain.SavedShareLink;
import com.soma.yeolo.user.domain.UserDisplayProfile;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 공유 링크 조회(미리보기) 응답의 {@code data} 페이로드 (API-SHARE-2 / DOM-6 §공유 코스 미리보기).
 *
 * <p>비로그인 사용자도 받는 응답이므로 명세가 정의한 항목만 담는다 — {@code courseId}는 싣지
 * 않는다(DOM-6: "공유 링크에는 실제 courseId를 직접 노출하지 않는다"). 일정·방문지도 수락 전에는
 * 보여주지 않는다.
 */
public record SharePreviewResponse(
        SharedCourse course,
        Inviter inviter,
        Instant expiresAt
) {

    /** 미리보기용 코스 요약. 필드명은 명세 그대로 사용한다. */
    public record SharedCourse(
            String title,
            String destinationCountry,
            String destinationCity,
            LocalDate startDate,
            int totalDays
    ) {
    }

    /** 초대한 사용자. 두 항목 모두 명세상 nullable이다. */
    public record Inviter(
            String displayName,
            String profileImageUrl
    ) {
    }

    /** 코스·초대자·링크 상태로 미리보기 응답을 조립한다. 초대자를 찾지 못하면 항목을 비운다. */
    public static SharePreviewResponse from(SavedCourse course, UserDisplayProfile inviter,
                                            SavedShareLink link) {
        return new SharePreviewResponse(
                new SharedCourse(
                        course.title(),
                        course.destinationCountry(),
                        course.destinationCity(),
                        course.startDate(),
                        course.totalDays()
                ),
                new Inviter(
                        inviter == null ? null : inviter.displayName(),
                        inviter == null ? null : inviter.profileImageUrl()
                ),
                link.expiresAt()
        );
    }
}
