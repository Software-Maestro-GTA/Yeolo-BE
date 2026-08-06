package com.soma.yeolo.place.controller;

import com.soma.yeolo.global.response.ApiResponse;
import com.soma.yeolo.place.dto.PlaceDetailResponse;
import com.soma.yeolo.place.service.PlaceQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장소 조회 API (API-PLACE-1). 코스 상세의 stop이 참조하는 내부 {@code placeId}로 장소 상세를 반환한다.
 */
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceQueryService placeQueryService;

    /**
     * 장소 상세 조회 (API-PLACE-1). 식별자 형식이 잘못되면 400, 장소가 없으면 404로 전역 핸들러가
     * 응답한다. 인증은 SecurityConfig의 기본 정책(인증 필요)을 따른다.
     */
    @GetMapping("/{placeId}")
    public ApiResponse<PlaceDetailResponse> getPlace(@PathVariable UUID placeId) {
        return ApiResponse.success("장소 상세 조회 성공", placeQueryService.getPlace(placeId));
    }
}
