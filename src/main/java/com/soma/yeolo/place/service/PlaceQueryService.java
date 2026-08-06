package com.soma.yeolo.place.service;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.place.domain.SavedPlace;
import com.soma.yeolo.place.dto.PlaceDetailResponse;
import com.soma.yeolo.place.service.port.PlaceRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내부 placeId 기반 장소 상세 조회 (API-PLACE-1).
 *
 * <p>코스 상세의 stop이 참조하는 내부 {@code placeId}로 장소를 찾아 반환한다. 없는 장소는 404로
 * 노출하고, 형식이 잘못된 식별자는 컨트롤러의 {@code UUID} 바인딩 실패를 전역 핸들러가 400으로
 * 변환한다.
 */
@Service
@RequiredArgsConstructor
public class PlaceQueryService {

    private final PlaceRepository placeRepository;

    /**
     * 장소 상세를 반환한다.
     *
     * @throws BusinessException 없는 장소면 {@code PLACE_NOT_FOUND}(404)
     */
    @Transactional(readOnly = true)
    public PlaceDetailResponse getPlace(UUID placeId) {
        SavedPlace place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        return PlaceDetailResponse.from(place);
    }
}
