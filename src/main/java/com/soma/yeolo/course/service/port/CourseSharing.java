package com.soma.yeolo.course.service.port;

import java.util.List;
import java.util.UUID;

/**
 * 코스 공유 조회·정리 포트 (DOM-6). 코스 조회·삭제(API-COURSE-2/3/4)가 "공유받은 코스"를 함께
 * 다루기 위해 필요한 최소 동작만 코스 도메인 쪽에 선언하고, 구현은 share 도메인의 어댑터
 * ({@code CourseSharingService})가 맡는다.
 *
 * <p>포트를 코스 쪽에 두는 이유는 의존 방향 때문이다 — share는 코스를 읽어야 하므로 이미
 * {@code course}에 의존한다. 코스가 share 구현을 직접 참조하면 두 패키지가 서로를 부르게 된다.
 * (docs/architecture.md §1-2, {@code TasteProfileEraser}와 같은 방식)
 */
public interface CourseSharing {

    /** 사용자가 수락해 목록에 추가한 코스 식별자. 없으면 빈 목록. (API-COURSE-3) */
    List<UUID> findSharedCourseIds(UUID userId);

    /** 사용자가 공유받아 접근할 수 있는 코스인지. (API-COURSE-2 상세 접근 판정) */
    boolean isSharedWith(UUID courseId, UUID userId);

    /**
     * 공유받은 사용자의 코스를 <b>내 목록에서만</b> 제거한다. 원본 코스는 삭제되지 않는다
     * (DOM-6 §보안 및 운영 정책).
     *
     * @return 제거할 권한이 실제로 있었으면 {@code true}
     */
    boolean removeShare(UUID courseId, UUID userId);

    /**
     * 코스에 걸린 공유를 모두 정리한다 — 접근 권한을 지우고 발급된 링크를 회수한다.
     * 소유자가 원본 코스를 삭제할 때 호출한다. (API-COURSE-4)
     */
    void revokeAllForCourse(UUID courseId);
}
