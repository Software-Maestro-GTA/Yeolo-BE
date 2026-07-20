package com.soma.yeolo.tasteprofile.client;

import com.soma.yeolo.tasteprofile.domain.GeoLocation;

/**
 * 좌표 → 위치 정보(Reverse Geocode) 변환 포트. (DOM-5 §2 Server 단계)
 *
 * <p>외부 지오코딩 제공자(추후 Google Maps API) 연동부를 격리하기 위한 인터페이스이며,
 * 구현체 교체만으로 실 provider를 붙일 수 있도록 한다. (docs/architecture.md §5)
 */
public interface ReverseGeocodeClient {

    /**
     * 위경도 좌표를 국가·도시·지역·장소 정보로 변환한다.
     *
     * @throws com.soma.yeolo.global.exception.BusinessException 변환 실패 시 (REVERSE_GEOCODE_FAILED)
     */
    GeoLocation reverseGeocode(double latitude, double longitude);
}
