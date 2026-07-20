package com.soma.yeolo.tasteprofile.domain;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

/**
 * 촬영 시각으로부터 파생한 시간 맥락 정보 (DOM-5 §4). 순수 도메인 — 프레임워크 무관.
 *
 * <p>{@code capturedAt}은 오프셋을 포함한 ISO-8601 문자열이며, 요일·시간대·계절은
 * {@code timezone}(있으면)에 해당하는 지역 시각을 기준으로 계산한다. 타임존이 없거나
 * 유효하지 않으면 {@code capturedAt}의 오프셋 지역 시각을 사용한다.
 *
 * @param capturedAt 원본 ISO-8601 촬영 시각(그대로 보존해 AI 서버로 전달)
 * @param dayOfWeek  촬영 요일
 * @param isWeekend  주말 촬영 여부
 * @param timeBucket 촬영 시간대
 * @param season     촬영 계절
 */
public record TimeContext(
        String capturedAt,
        Weekday dayOfWeek,
        boolean isWeekend,
        TimeBucket timeBucket,
        Season season
) {

    /**
     * ISO-8601 촬영 시각과 타임존으로 시간 맥락을 파생한다.
     *
     * @throws IllegalArgumentException capturedAt이 유효한 ISO-8601 오프셋 시각이 아닐 때
     */
    public static TimeContext derive(String capturedAt, String timezone) {
        OffsetDateTime offsetDateTime = parse(capturedAt);
        ZonedDateTime zoned = applyZone(offsetDateTime, timezone);

        Weekday weekday = Weekday.from(zoned.getDayOfWeek());
        TimeBucket timeBucket = TimeBucket.fromHour(zoned.getHour());
        Season season = Season.fromMonth(zoned.getMonthValue());

        return new TimeContext(capturedAt, weekday, weekday.isWeekend(), timeBucket, season);
    }

    private static OffsetDateTime parse(String capturedAt) {
        try {
            return OffsetDateTime.parse(capturedAt);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("invalid ISO-8601 capturedAt: " + capturedAt, e);
        }
    }

    private static ZonedDateTime applyZone(OffsetDateTime offsetDateTime, String timezone) {
        if (timezone != null && !timezone.isBlank()) {
            try {
                return offsetDateTime.atZoneSameInstant(ZoneId.of(timezone));
            } catch (DateTimeException ignored) {
                // 유효하지 않은 타임존 → capturedAt의 오프셋 기준으로 폴백
            }
        }
        return offsetDateTime.toZonedDateTime();
    }
}
