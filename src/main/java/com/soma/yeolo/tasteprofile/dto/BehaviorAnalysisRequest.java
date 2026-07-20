package com.soma.yeolo.tasteprofile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 이미지 메타데이터 기반 성향 분석 요청 (API-FB-2 Request Body).
 * 개인정보 수집·활용 동의는 클라이언트에서 선행 처리하며, 미동의 시 호출하지 않는다(FUN-1).
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
     * @param capturedAt    ISO-8601 촬영 시각(오프셋 포함)
     * @param latitude      촬영 위도
     * @param longitude     촬영 경도
     * @param timezone      촬영 시간 해석에 사용할 타임존 (예: Asia/Seoul)
     */
    public record ImageMetadata(
            @NotBlank(message = "sourceImageId는 필수입니다.")
            String sourceImageId,

            @NotBlank(message = "capturedAt은 필수입니다.")
            String capturedAt,

            @NotNull(message = "latitude는 필수입니다.")
            Double latitude,

            @NotNull(message = "longitude는 필수입니다.")
            Double longitude,

            String timezone
    ) {
    }
}
