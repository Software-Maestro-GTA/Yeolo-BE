package com.soma.yeolo.course.repository;

import com.soma.yeolo.course.entity.CourseEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 코스 Spring Data JPA 리포지토리. 포트 구현체 {@link CourseRepositoryImpl} 내부에서만 사용하며,
 * 서비스(응용 계층)에서 직접 주입하지 않는다. (docs/architecture.md §1-2)
 */
public interface CourseJpaRepository extends JpaRepository<CourseEntity, UUID> {

    /** 사용자의 코스를 최신 생성순으로 조회한다. (API-FB-10) */
    List<CourseEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** 공유받은 코스를 식별자 묶음으로 최신 생성순 조회한다. (API-COURSE-3 / DOM-6) */
    List<CourseEntity> findByIdInOrderByCreatedAtDesc(Collection<UUID> courseIds);

    /** 사용자가 생성한 코스 존재 여부. (온보딩 완료 판정) */
    boolean existsByUserId(UUID userId);

    /**
     * 사용자가 만든 코스의 식별자만 최신 생성순으로 조회한다. (API-AUTH-1 {@code recentCourseId})
     * itinerary 원본 JSON을 싣지 않으려고 식별자만 투영한다.
     */
    @Query("select c.id from CourseEntity c where c.userId = :userId order by c.createdAt desc")
    List<UUID> findIdsByUserIdLatestFirst(@Param("userId") UUID userId, Pageable pageable);

    /**
     * 소유하거나 공유받은 코스의 식별자를 최신 생성순으로 조회한다.
     * ({@code courseIds}가 비어 있으면 호출하지 않는다 — 빈 {@code in} 절을 만들지 않기 위함)
     */
    @Query("""
            select c.id from CourseEntity c
            where c.userId = :userId or c.id in :courseIds
            order by c.createdAt desc
            """)
    List<UUID> findAccessibleIdsLatestFirst(@Param("userId") UUID userId,
                                            @Param("courseIds") Collection<UUID> courseIds,
                                            Pageable pageable);
}
