package com.soma.yeolo.tasteprofile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 이미지 메타데이터 기반 취향 분석 요청 (API-PREF-3 Request Body).
 * 개인정보 수집·활용 동의 여부는 서버가 요청 처리 전에 검증한다(REQ-8) — 클라이언트 선행 안내와
 * 별개로, 미동의 요청은 컨트롤러에서 403으로 차단된다.
 */
public record BehaviorAnalysisRequest(
        @NotEmpty(message = "분석 가능한 이미지 메타데이터가 부족합니다.")
        @Valid
        List<ImageMetadata> images
) {

    /**
     * 사진 한 장의 EXIF 기반 메타데이터 (DOM-5 §4-2).
     *
     * @param sourceImageId 클라이언트 이미지 식별자
     * @param capturedAt    ISO-8601 촬영 시각(UTC)
     * @param latitude      촬영 위도
     * @param longitude     촬영 경도
     */
    public record ImageMetadata(
            @NotBlank(message = "sourceImageId는 필수입니다.")
            String sourceImageId,

            @NotBlank(message = "capturedAt은 필수입니다.")
            String capturedAt,

            @NotNull(message = "latitude는 필수입니다.")
            Double latitude,

            @NotNull(message = "longitude는 필수입니다.")
            Double longitude
    ) {
    }
}
