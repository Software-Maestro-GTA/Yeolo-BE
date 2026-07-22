package com.soma.yeolo.course.entity;

import com.soma.yeolo.course.domain.Course;
import com.soma.yeolo.course.domain.SavedCourse;
import com.soma.yeolo.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 코스 정보 (DOM-2). 목록·상세에 쓰이는 상위 정보는 컬럼으로 승격하고, 일자·방문지 전체는
 * {@code itinerary}(원본 JSON)로 보존한다. ({@code constraints}는 명세 개정으로 제거됨)
 */
@Getter
@Entity
@Table(name = "courses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Lob
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "destination_country", nullable = false)
    private String destinationCountry;

    @Column(name = "destination_city", nullable = false)
    private String destinationCity;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    @Lob
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "tags")
    private List<String> tags;

    @Lob
    @Column(name = "recommendation_reason")
    private String recommendationReason;

    @Lob
    @Column(name = "itinerary", nullable = false)
    private String itinerary;

    @Builder
    private CourseEntity(UUID userId, String title, String destinationCountry, String destinationCity,
                         LocalDate startDate, int totalDays, List<String> tags,
                         String recommendationReason, String itinerary) {
        this.userId = userId;
        this.title = title;
        this.destinationCountry = destinationCountry;
        this.destinationCity = destinationCity;
        this.startDate = startDate;
        this.totalDays = totalDays;
        this.tags = tags;
        this.recommendationReason = recommendationReason;
        this.itinerary = itinerary;
    }

    /** 순수 도메인 → 영속 엔티티 매핑. */
    public static CourseEntity from(Course course) {
        return CourseEntity.builder()
                .userId(course.userId())
                .title(course.title())
                .destinationCountry(course.destinationCountry())
                .destinationCity(course.destinationCity())
                .startDate(course.startDate())
                .totalDays(course.totalDays())
                .tags(course.tags())
                .recommendationReason(course.recommendationReason())
                .itinerary(course.itineraryJson())
                .build();
    }

    /** 영속 엔티티 → 조회용 읽기 모델 매핑 (API-FB-7 / API-FB-10). 부여된 식별자·생성 시각을 함께 담는다. */
    public SavedCourse toSavedCourse() {
        return new SavedCourse(id, userId, title, destinationCountry, destinationCity,
                startDate, totalDays, tags, recommendationReason, itinerary, getCreatedAt());
    }
}
