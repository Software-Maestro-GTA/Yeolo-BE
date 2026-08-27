package com.soma.yeolo.share.service;

import com.soma.yeolo.course.service.port.CourseSharing;
import com.soma.yeolo.share.service.port.CourseAccessRepository;
import com.soma.yeolo.share.service.port.ShareLinkRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코스 도메인이 소유한 {@link CourseSharing} 포트의 share 쪽 어댑터 (DOM-6). 코스 목록·상세·삭제가
 * 공유 상태를 알아야 하는 지점만 얇게 열어 준다. (docs/architecture.md §1-2)
 */
@Service
@RequiredArgsConstructor
public class CourseSharingService implements CourseSharing {

    private final CourseAccessRepository courseAccessRepository;
    private final ShareLinkRepository shareLinkRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findSharedCourseIds(UUID userId) {
        return courseAccessRepository.findCourseIdsByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSharedWith(UUID courseId, UUID userId) {
        return courseAccessRepository.exists(courseId, userId);
    }

    /** 호출자(코스 삭제)의 트랜잭션에 참여해 원본 삭제와 함께 커밋되거나 함께 롤백된다. */
    @Override
    @Transactional
    public boolean removeShare(UUID courseId, UUID userId) {
        return courseAccessRepository.delete(courseId, userId);
    }

    /**
     * 접근 권한을 지우고 링크를 회수한다.
     *
     * <p>링크 행을 지우지 않고 회수 표시만 남기는 이유는 조회(API-SHARE-2)가 "없는 링크"(404
     * "유효하지 않은 공유 링크입니다.")와 "코스가 사라진 링크"(404 "여행 코스를 찾을 수 없습니다.")를
     * 구분해 안내해야 하기 때문이다 (DOM-6 §예외 상황).
     */
    @Override
    @Transactional
    public void revokeAllForCourse(UUID courseId) {
        courseAccessRepository.deleteAllByCourseId(courseId);
        shareLinkRepository.revokeAllByCourseId(courseId, Instant.now());
    }
}
