package com.soma.yeolo.course.service;

import java.util.Optional;
import java.util.UUID;

/**
 * 최근 코스 조회 포트 (API-AUTH-1 / API-AUTH-2의 {@code recentCourseId}).
 *
 * <p>로그인 응답은 "앱이 곧바로 열어 줄 코스"의 식별자만 필요하므로, 코스 도메인의 조회·삭제까지
 * 끌어오지 않도록 읽기 전용 포트로 좁혀 노출한다. 소비자(인증)는 영속 계층을 모른 채 이 포트에만
 * 의존하므로 단위 테스트에서 람다로 대체할 수 있다
 * (docs/architecture.md §1-2 — {@code UserMbtiReader}와 같은 방식).
 */
@FunctionalInterface
public interface RecentCourseReader {

    /**
     * 사용자가 볼 수 있는 코스 중 가장 최근에 만들어진 것의 식별자. 코스가 하나도 없으면 빈 값.
     *
     * <p>"볼 수 있는"에는 <b>공유받아 수락한 코스도 포함된다</b> — 이 값은 코스 목록
     * (API-COURSE-3)의 맨 위 항목과 같아야 하고, 그 목록이 소유·공유를 구분하지 않기 때문이다(DOM-6).
     */
    Optional<UUID> findRecentCourseId(UUID userId);
}
