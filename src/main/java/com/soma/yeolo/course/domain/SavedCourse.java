package com.soma.yeolo.course.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 저장된 코스 조회 결과 (API-FB-7 / API-FB-10). 신규 저장용 {@link Course}와 달리 부여된 식별자·
 * 생성 시각 등 영속 이후의 권위 값을 함께 담는 읽기 전용 모델이다. 일자·방문지 전체는
 * {@code itineraryJson}(원본 JSON)에 보존되어 있으며, 상세 응답 조립 시 파싱해 전달한다.
 *
 * @param courseId             코스 식별자
 * @param userId               소유자 식별자
 * @param title                코스 제목
 * @param destinationCountry   여행 국가
 * @param destinationCity      여행 도시
 * @param startDate            여행 시작일
 * @param totalDays            총 여행 일수
 * @param tags                 코스 태그
 * @param recommendationReason 추천 이유
 * @param itineraryJson        일자·방문지·순서 전체 (itinerary 원본 JSON)
 * @param createdAt            생성 시각 (UTC)
 */
public record SavedCourse(
        UUID courseId,
        UUID userId,
        String title,
        String destinationCountry,
        String destinationCity,
        LocalDate startDate,
        int totalDays,
        List<String> tags,
        String recommendationReason,
        String itineraryJson,
        Instant createdAt
) {

    /** 주어진 사용자가 이 코스의 소유자인지 여부. 상세 조회 접근 권한(403) 판정에 쓰인다. (FUN-7) */
    public boolean isOwnedBy(UUID userId) {
        return this.userId.equals(userId);
    }
}
