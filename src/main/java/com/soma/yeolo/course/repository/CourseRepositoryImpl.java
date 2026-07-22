package com.soma.yeolo.course.repository;

import com.soma.yeolo.course.domain.Course;
import com.soma.yeolo.course.entity.CourseEntity;
import com.soma.yeolo.course.service.port.CourseRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link CourseRepository} 포트의 JPA 어댑터. Spring Data {@link CourseJpaRepository}에 위임해
 * 순수 도메인을 {@link CourseEntity}로 저장하고, 도메인↔엔티티 매핑을 이 경계에 격리한다.
 * (docs/architecture.md §1-2)
 */
@Component
@RequiredArgsConstructor
class CourseRepositoryImpl implements CourseRepository {

    private final CourseJpaRepository jpaRepository;

    @Override
    public UUID save(Course course) {
        return jpaRepository.save(CourseEntity.from(course)).getId();
    }
}
