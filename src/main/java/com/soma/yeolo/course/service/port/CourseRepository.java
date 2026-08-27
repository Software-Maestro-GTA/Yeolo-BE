package com.soma.yeolo.course.service.port;

import com.soma.yeolo.course.domain.Course;
import com.soma.yeolo.course.domain.SavedCourse;
import java.util.Collection;
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
     * 사용자가 생성한 코스를 최신 생성순으로 조회한다. 없으면 빈 목록을 반환한다. (API-COURSE-3)
     * (페이지네이션은 현재 명세에 없으며, 필요 시 파라미터 추가로 확장한다. FUN-9)
     */
    List<SavedCourse> findByUserIdLatestFirst(UUID userId);

    /**
     * 주어진 식별자의 코스를 최신 생성순으로 조회한다. 없는 식별자는 결과에서 빠진다.
     * 공유받아 목록에 추가된 코스를 함께 싣는 데 쓴다. (API-COURSE-3 / DOM-6)
     */
    List<SavedCourse> findAllByIdsLatestFirst(Collection<UUID> courseIds);

    /**
     * 소유하거나 공유받은 코스 중 가장 최근에 만들어진 것의 식별자. 없으면 빈 값.
     * (API-AUTH-1 / API-AUTH-2의 {@code recentCourseId})
     *
     * <p>목록 조회를 재사용하지 않고 식별자만 뽑는 별도 질의를 두는 이유는 <b>로그인 경로</b>이기
     * 때문이다. 코스 행에는 itinerary 원본 JSON이 통째로 들어 있어, 식별자 하나를 얻자고 사용자의
     * 모든 코스를 실어 오면 로그인마다 수백 KB를 읽게 된다.
     *
     * @param sharedCourseIds 공유받아 수락한 코스 식별자 (없으면 빈 목록)
     */
    Optional<UUID> findRecentCourseId(UUID userId, Collection<UUID> sharedCourseIds);

    /** 코스를 식별자로 조회한다. 없으면 빈 값을 반환한다. 소유권 판정은 호출자가 수행한다. (API-COURSE-2) */
    Optional<SavedCourse> findById(UUID courseId);

    /** 코스를 식별자로 삭제한다. 존재·소유권 검증은 호출자가 선행한다. 없는 식별자면 무시한다. (API-COURSE-4) */
    void deleteById(UUID courseId);

    /** 사용자가 생성한 코스가 하나라도 있는지 여부. (온보딩 완료 판정에 사용) */
    boolean existsByUserId(UUID userId);
}
