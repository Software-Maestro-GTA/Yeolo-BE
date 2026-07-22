package com.soma.yeolo.course.repository;

import com.soma.yeolo.course.entity.CourseEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 코스 Spring Data JPA 리포지토리. 포트 구현체 {@link CourseRepositoryImpl} 내부에서만 사용하며,
 * 서비스(응용 계층)에서 직접 주입하지 않는다. (docs/architecture.md §1-2)
 */
public interface CourseJpaRepository extends JpaRepository<CourseEntity, UUID> {
}
