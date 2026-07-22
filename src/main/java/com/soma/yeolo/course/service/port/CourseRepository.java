package com.soma.yeolo.course.service.port;

import com.soma.yeolo.course.domain.Course;
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
}
