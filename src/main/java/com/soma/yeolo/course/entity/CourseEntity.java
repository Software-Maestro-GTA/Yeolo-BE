package com.soma.yeolo.course.entity;

import com.soma.yeolo.course.domain.Course;
import com.soma.yeolo.course.domain.SavedCourse;
import com.soma.yeolo.global.entity.BaseTimeEntity;
import com.soma.yeolo.global.entity.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 코스 정보 (DOM-3). 목록·상세에 쓰이는 상위 정보는 컬럼으로 승격하고, 일자·방문지 전체는
 * {@code itinerary}(원본 JSON)로 보존한다.
 *
 * <p>명세 개정(2026-10)으로 {@code recommendation_reason}은 매핑에서 뺐고(prod 컬럼은
 * {@code docs/ddl/courses.sql}로 정리) {@code total_cost}를 추가했다. {@code cover_image_url}은
 * 목록 응답(API-COURSE-3)에 남아 있어 유지한다.
 */
@Getter
@Entity
@Table(name = "courses", indexes = {
        // 사용자별 코스 조회 전부(목록 API-COURSE-3, 로그인 recentCourseId, existsByUserId)가
        // user_id 필터 + created_at DESC 정렬이다. 행이 itinerary JSON으로 넓어 seq scan이 비싸다.
        @Index(name = "idx_courses_user_id_created_at", columnList = "user_id, created_at DESC")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "destination_country", nullable = false)
    private String destinationCountry;

    @Column(name = "destination_city", nullable = false)
    private String destinationCity;

    @Column(name = "cover_image_url", columnDefinition = "text")
    private String coverImageUrl;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    /** 코스 전체 예상 비용 (API-AI-2 {@code totalCost}). 개정 전 코스와 AI가 못 준 경우 NULL. */
    @Column(name = "total_cost")
    private Long totalCost;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "tags", columnDefinition = "text")
    private List<String> tags;

    @Column(name = "itinerary", nullable = false, columnDefinition = "text")
    private String itinerary;

    @Builder
    private CourseEntity(UUID userId, String title, String destinationCountry, String destinationCity,
                         String coverImageUrl, LocalDate startDate, int totalDays, Long totalCost,
                         List<String> tags, String itinerary) {
        this.userId = userId;
        this.title = title;
        this.destinationCountry = destinationCountry;
        this.destinationCity = destinationCity;
        this.coverImageUrl = coverImageUrl;
        this.startDate = startDate;
        this.totalDays = totalDays;
        this.totalCost = totalCost;
        this.tags = tags;
        this.itinerary = itinerary;
    }

    /** 순수 도메인 → 영속 엔티티 매핑. */
    public static CourseEntity from(Course course) {
        return CourseEntity.builder()
                .userId(course.userId())
                .title(course.title())
                .destinationCountry(course.destinationCountry())
                .destinationCity(course.destinationCity())
                .coverImageUrl(course.coverImageUrl())
                .startDate(course.startDate())
                .totalDays(course.totalDays())
                .totalCost(course.totalCost())
                .tags(course.tags())
                .itinerary(course.itineraryJson())
                .build();
    }

    /** 영속 엔티티 → 조회용 읽기 모델 매핑 (API-COURSE-2 / API-COURSE-3). 부여된 식별자·생성 시각을 함께 담는다. */
    public SavedCourse toSavedCourse() {
        return new SavedCourse(id, userId, title, destinationCountry, destinationCity, coverImageUrl,
                startDate, totalDays, totalCost, tags, itinerary, getCreatedAt());
    }
}
