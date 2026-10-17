package com.soma.yeolo.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.course.domain.SavedCourse;
import com.soma.yeolo.location.service.LocationImageReader;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * 코스 대표 이미지 규칙 — 목록(API-COURSE-3)과 공유 미리보기(API-SHARE-2)가 같은 규칙을 쓴다(#109).
 * 도시 사진 포트는 호출을 기록하는 fake 로 대체한다.
 */
class CourseCoverImageResolverTest {

    private final List<String> lookups = new ArrayList<>();
    private String cityImage;
    private final LocationImageReader reader = (country, city) -> {
        lookups.add(country + "/" + city);
        return Optional.ofNullable(cityImage);
    };
    private final CourseCoverImageResolver resolver = new CourseCoverImageResolver(reader);

    private static SavedCourse course(String city, String coverImageUrl) {
        return new SavedCourse(UUID.randomUUID(), UUID.randomUUID(), city + " 여행", "일본", city, coverImageUrl,
                LocalDate.of(2026, 9, 1), 3, null, List.of(), "{\"days\":[]}", Instant.now());
    }

    @Test
    void 저장된_대표_이미지가_있으면_도시_사진을_조회하지_않고_그대로_쓴다() {
        cityImage = "https://cdn/city.jpg";

        assertThat(resolver.resolve(course("오사카", "https://cdn/saved.jpg"))).isEqualTo("https://cdn/saved.jpg");
        assertThat(lookups).isEmpty();
    }

    @Test
    void 저장된_대표_이미지가_없으면_목적지_도시_사진으로_채운다() {
        cityImage = "https://cdn/city.jpg";

        assertThat(resolver.resolve(course("오사카", null))).isEqualTo("https://cdn/city.jpg");
        assertThat(lookups).containsExactly("일본/오사카");
    }

    @Test
    void 도시_사진도_없으면_null이다() {
        assertThat(resolver.resolve(course("오사카", null))).isNull();
    }

    /** 같은 목적지의 코스가 여럿이면 사진 조회를 한 번만 한다 — 목록 조회의 조회 횟수 상한. */
    @Test
    void 일괄_공급자는_같은_목적지를_한_번만_조회한다() {
        cityImage = "https://cdn/city.jpg";
        Function<SavedCourse, String> batch = resolver.batch();

        assertThat(batch.apply(course("오사카", null))).isEqualTo("https://cdn/city.jpg");
        assertThat(batch.apply(course("오사카", null))).isEqualTo("https://cdn/city.jpg");
        assertThat(batch.apply(course("도쿄", null))).isEqualTo("https://cdn/city.jpg");

        assertThat(lookups).containsExactly("일본/오사카", "일본/도쿄");
    }

    /** 사진이 없다는 결과도 기억한다 — 없는 도시를 코스마다 다시 묻지 않는다. */
    @Test
    void 일괄_공급자는_사진_없음도_한_번만_조회한다() {
        Function<SavedCourse, String> batch = resolver.batch();

        assertThat(batch.apply(course("오사카", null))).isNull();
        assertThat(batch.apply(course("오사카", null))).isNull();

        assertThat(lookups).containsExactly("일본/오사카");
    }
}
