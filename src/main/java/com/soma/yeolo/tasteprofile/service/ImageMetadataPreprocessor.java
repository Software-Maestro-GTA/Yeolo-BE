package com.soma.yeolo.tasteprofile.service;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.client.ReverseGeocodeClient;
import com.soma.yeolo.tasteprofile.domain.GeoLocation;
import com.soma.yeolo.tasteprofile.domain.PreprocessedImage;
import com.soma.yeolo.tasteprofile.domain.TimeContext;
import com.soma.yeolo.tasteprofile.dto.BehaviorAnalysisRequest.ImageMetadata;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 이미지 메타데이터 전처리 (DOM-5 §2 Server 단계). 좌표를 Reverse Geocode로 위치 정보화하고,
 * 촬영 시각으로부터 시간 맥락을 파생해 AI 전송용 {@link PreprocessedImage} 목록을 만든다.
 */
@Component
@RequiredArgsConstructor
public class ImageMetadataPreprocessor {

    private final ReverseGeocodeClient reverseGeocodeClient;

    /**
     * 이미지 메타데이터 목록을 전처리한다.
     *
     * @throws BusinessException 분석 가능한 메타데이터가 없거나 형식이 잘못된 경우
     *                           (INSUFFICIENT_IMAGE_METADATA)
     */
    public List<PreprocessedImage> preprocess(List<ImageMetadata> images) {
        if (images == null || images.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_IMAGE_METADATA);
        }
        return images.stream().map(this::toPreprocessed).toList();
    }

    private PreprocessedImage toPreprocessed(ImageMetadata image) {
        try {
            GeoLocation location = reverseGeocodeClient.reverseGeocode(image.latitude(), image.longitude());
            TimeContext timeContext = TimeContext.derive(image.capturedAt(), image.timezone());
            return new PreprocessedImage(image.sourceImageId(), location, timeContext);
        } catch (IllegalArgumentException e) {
            // capturedAt 형식 오류 등 → 분석 불가한 메타데이터
            throw new BusinessException(ErrorCode.INSUFFICIENT_IMAGE_METADATA, e);
        }
    }
}
