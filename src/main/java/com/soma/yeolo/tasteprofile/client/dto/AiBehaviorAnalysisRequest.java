package com.soma.yeolo.tasteprofile.client.dto;

import com.soma.yeolo.tasteprofile.domain.PreprocessedImage;
import java.util.List;
import java.util.UUID;

/**
 * BE → AI 전처리 메타데이터 기반 성향 분석 요청 (API-BA-6 Request Body).
 * 전송 스키마의 필드명(location/timeContext 등)을 명세 그대로 사용한다.
 */
public record AiBehaviorAnalysisRequest(
        String userId,
        List<Item> items
) {

    public record Item(
            String sourceImageId,
            Location location,
            TimeContext timeContext
    ) {
    }

    public record Location(
            String country,
            String city,
            String region,
            String district,
            String placeName,
            List<String> placeTypes
    ) {
    }

    public record TimeContext(
            String capturedAt,
            String dayOfWeek,
            boolean isWeekend,
            String timeBucket,
            String season
    ) {
    }

    /** 사용자 식별자와 전처리 결과 목록으로 AI 요청 본문을 구성한다. */
    public static AiBehaviorAnalysisRequest of(UUID userId, List<PreprocessedImage> images) {
        List<Item> items = images.stream().map(AiBehaviorAnalysisRequest::toItem).toList();
        return new AiBehaviorAnalysisRequest(userId.toString(), items);
    }

    private static Item toItem(PreprocessedImage image) {
        var loc = image.location();
        var tc = image.timeContext();
        return new Item(
                image.sourceImageId(),
                new Location(loc.country(), loc.city(), loc.region(), loc.district(),
                        loc.placeName(), loc.placeTypes()),
                new TimeContext(tc.capturedAt(), tc.dayOfWeek().getValue(), tc.isWeekend(),
                        tc.timeBucket().getValue(), tc.season().getValue())
        );
    }
}
