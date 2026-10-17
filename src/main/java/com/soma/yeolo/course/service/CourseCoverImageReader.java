package com.soma.yeolo.course.service;

import com.soma.yeolo.course.domain.SavedCourse;
import java.util.function.Function;

/**
 * 코스 대표 이미지 조회 포트 (API-COURSE-3 목록 · API-SHARE-2 공유 미리보기, #109).
 *
 * <p>소비자는 결정 규칙({@link CourseCoverImageResolver} — 저장값·도시 사진 폴백)을 모른 채 이 포트에만
 * 의존하므로 단위 테스트에서 람다로 대체할 수 있다 (docs/architecture.md §1-2 —
 * {@code RecentCourseReader}·{@code UserDisplayProfileReader}와 같은 방식).
 */
@FunctionalInterface
public interface CourseCoverImageReader {

    /** 코스 하나의 대표 이미지 URL. 저장값이 없고 목적지 도시 사진도 없으면 null. */
    String resolve(SavedCourse course);

    /**
     * 여러 코스를 연달아 처리할 때 쓰는 공급자. 구현이 같은 목적지의 반복 조회를 줄이는 자리이며,
     * 기본은 코스마다 {@link #resolve}를 부르는 것이다.
     */
    default Function<SavedCourse, String> batch() {
        return this::resolve;
    }
}
