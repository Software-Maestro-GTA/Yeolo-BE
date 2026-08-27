package com.soma.yeolo.share.repository;

import com.soma.yeolo.share.entity.CourseAccessEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 코스 접근 권한 Spring Data JPA 리포지토리. 포트 구현체 {@link CourseAccessRepositoryImpl}
 * 내부에서만 사용한다. (docs/architecture.md §1-2)
 */
public interface CourseAccessJpaRepository extends JpaRepository<CourseAccessEntity, UUID> {

    boolean existsByCourseIdAndUserId(UUID courseId, UUID userId);

    /** 목록 조회는 식별자만 있으면 되므로 엔티티를 싣지 않는다. */
    @Query("select a.courseId from CourseAccessEntity a where a.userId = :userId")
    List<UUID> findCourseIdsByUserId(UUID userId);

    long deleteByCourseIdAndUserId(UUID courseId, UUID userId);

    void deleteByCourseId(UUID courseId);
}
