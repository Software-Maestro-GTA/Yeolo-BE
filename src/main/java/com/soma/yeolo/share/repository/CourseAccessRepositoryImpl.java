package com.soma.yeolo.share.repository;

import com.soma.yeolo.share.domain.CourseAccess;
import com.soma.yeolo.share.entity.CourseAccessEntity;
import com.soma.yeolo.share.service.port.CourseAccessRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * {@link CourseAccessRepository} 포트의 JPA 어댑터. 도메인↔엔티티 매핑을 이 경계에 격리한다.
 * (docs/architecture.md §1-2)
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CourseAccessRepositoryImpl implements CourseAccessRepository {

    private final CourseAccessJpaRepository jpaRepository;

    /**
     * 즉시 flush 해 유니크 제약 위반을 <b>이 메서드 안에서</b> 받아낸다. 기본 {@code save}는 커밋
     * 시점에야 INSERT가 나가므로, 그때 터지는 위반은 서비스가 잡을 수 없는 자리에서 500이 된다.
     */
    @Override
    public boolean saveIfAbsent(CourseAccess access) {
        try {
            jpaRepository.saveAndFlush(CourseAccessEntity.from(access));
            return true;
        } catch (DataIntegrityViolationException e) {
            // uk_course_accesses_course_id_user_id — 동시 수락에서 진 쪽. 이미 권한이 있다는 뜻이다.
            log.debug("이미 부여된 코스 접근 권한이다: courseId={}, userId={}",
                    access.courseId(), access.userId());
            return false;
        }
    }

    @Override
    public boolean exists(UUID courseId, UUID userId) {
        return jpaRepository.existsByCourseIdAndUserId(courseId, userId);
    }

    @Override
    public List<UUID> findCourseIdsByUserId(UUID userId) {
        return jpaRepository.findCourseIdsByUserId(userId);
    }

    @Override
    public boolean delete(UUID courseId, UUID userId) {
        return jpaRepository.deleteByCourseIdAndUserId(courseId, userId) > 0;
    }

    @Override
    public void deleteAllByCourseId(UUID courseId) {
        jpaRepository.deleteByCourseId(courseId);
    }
}
