package com.soma.yeolo.course.service.port;

import com.soma.yeolo.course.domain.Course;
import com.soma.yeolo.course.domain.SavedCourse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 코스 영속 포트. 서비스(응용 계층)가 소유하는 아웃바운드 인터페이스로, 순수 도메인 {@link Course}만
 * 주고받는다. JPA·Spring Data 등 영속성 세부는 알지 못한다(DIP). 애그리거트당 포트 하나로
 * 저장·조회를 함께 둔다(CQRS 분리 지양).
 *
 * <p>구현체 {@code CourseRepositoryImpl}({@code repository/})가 Spring Data {@code CourseJpaRepository}에
 * 위임하며 도메인↔엔티티 매핑을 담당한다. (docs/architecture.md §1-2)
 */
public interface CourseRepository {

    /**
     * 코스를 저장하고 부여된 식별자를 반환한다.
     *
     * @return 저장된 코스의 id
     */
    UUID save(Course course);

    /**
     * 사용자가 생성한 코스를 최신 생성순으로 조회한다. 없으면 빈 목록을 반환한다. (API-FB-10)
     * (페이지네이션은 현재 명세에 없으며, 필요 시 파라미터 추가로 확장한다. FUN-7)
     */
    List<SavedCourse> findByUserIdLatestFirst(UUID userId);

    /** 코스를 식별자로 조회한다. 없으면 빈 값을 반환한다. 소유권 판정은 호출자가 수행한다. (API-FB-7) */
    Optional<SavedCourse> findById(UUID courseId);
}
