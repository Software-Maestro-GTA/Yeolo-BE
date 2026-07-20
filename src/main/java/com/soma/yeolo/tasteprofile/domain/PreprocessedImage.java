package com.soma.yeolo.tasteprofile.domain;

/**
 * 전처리를 마친 사진 한 장. Reverse Geocode 위치 + 시간 맥락을 결합한 결과로,
 * AI 서버(API-BA-6)의 {@code items[]} 한 항목에 대응한다. (DOM-5 §4-5)
 *
 * @param sourceImageId 클라이언트가 부여한 이미지 식별자
 * @param location      Reverse Geocode 위치 정보
 * @param timeContext   촬영 시간 맥락
 */
public record PreprocessedImage(
        String sourceImageId,
        GeoLocation location,
        TimeContext timeContext
) {
}
