package com.soma.yeolo.course.client;

/**
 * 장소명 → 내부 장소 정보(정규화) 포트 (DOM-3 장소 정보 처리 기준). AI 서버는 장소명과 카테고리 중심의
 * 코스를 반환하고, BE는 이를 바탕으로 장소 조회/지오코딩을 수행해 내부 {@code placeId}와 좌표로
 * 정규화한다. 외부 지오코딩 제공자 연동부를 격리하기 위한 인터페이스이며, 구현체 교체만으로 provider를
 * 바꿀 수 있다. (docs/architecture.md §5)
 *
 * <p>구현체는 실패 시에도 예외를 던지지 않고 좌표 없는 결과({@code placeId}만)를 반환해, 코스 생성
 * 파이프라인이 지오코딩 결과와 무관하게 진행되도록 한다.
 */
public interface PlaceNormalizer {

    /**
     * 장소명·카테고리와 여행 도시·국가 맥락으로 내부 장소 정보를 해석한다.
     *
     * @param placeName 장소명 (필수)
     * @param category  장소 분류 (없으면 {@code null})
     * @param city      여행 도시 (지오코딩 맥락, 없으면 {@code null})
     * @param country   여행 국가 (지오코딩 맥락, 없으면 {@code null})
     * @return 내부 {@code placeId}와 좌표(미해결 시 좌표 {@code null})
     */
    NormalizedPlace normalize(String placeName, String category, String city, String country);
}
