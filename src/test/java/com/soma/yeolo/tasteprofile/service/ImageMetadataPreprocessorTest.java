package com.soma.yeolo.tasteprofile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.client.ReverseGeocodeClient;
import com.soma.yeolo.tasteprofile.domain.GeoLocation;
import com.soma.yeolo.tasteprofile.domain.PreprocessedImage;
import com.soma.yeolo.tasteprofile.domain.TimeBucket;
import com.soma.yeolo.tasteprofile.domain.Weekday;
import com.soma.yeolo.tasteprofile.dto.BehaviorAnalysisRequest.ImageMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageMetadataPreprocessorTest {

    @Mock
    private ReverseGeocodeClient reverseGeocodeClient;

    private ImageMetadataPreprocessor preprocessor() {
        return new ImageMetadataPreprocessor(reverseGeocodeClient);
    }

    @Test
    void 위치와_시간맥락을_결합해_전처리_항목을_만든다() {
        when(reverseGeocodeClient.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(new GeoLocation("대한민국", "제주", "서귀포", "성산읍", "성산일출봉",
                        List.of("tourist_attraction")));

        List<PreprocessedImage> result = preprocessor().preprocess(List.of(
                new ImageMetadata("img-1", "2026-07-14T10:00:00+09:00", 33.45, 126.94)));

        assertThat(result).hasSize(1);
        PreprocessedImage image = result.getFirst();
        assertThat(image.sourceImageId()).isEqualTo("img-1");
        assertThat(image.location().placeName()).isEqualTo("성산일출봉");
        assertThat(image.timeContext().dayOfWeek()).isEqualTo(Weekday.TUE);
        assertThat(image.timeContext().timeBucket()).isEqualTo(TimeBucket.MORNING);
    }

    @Test
    void 빈_목록이면_INSUFFICIENT_IMAGE_METADATA를_던진다() {
        assertThatThrownBy(() -> preprocessor().preprocess(List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_IMAGE_METADATA);
    }

    /**
     * AI의 {@code LocationSchema}는 placeTypes를 뺀 전 필드가 non-nullable이라, 좌표에 결과가 없는
     * 사진 한 장이 섞이면 요청 <b>전체</b>가 400으로 반려된다. 그 한 장만 빼고 나머지는 분석한다.
     */
    @Test
    void 위치가_불완전한_사진은_제외하고_나머지로_분석한다() {
        when(reverseGeocodeClient.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(new GeoLocation(null, null, null, null, null, List.of()))
                .thenReturn(new GeoLocation("대한민국", "마포구", "서울특별시", "공덕동", "프릳츠 도화",
                        List.of("cafe")));

        List<PreprocessedImage> result = preprocessor().preprocess(List.of(
                new ImageMetadata("img-1", "2026-07-14T10:00:00+09:00", 0.0, 0.0),
                new ImageMetadata("img-2", "2026-07-14T10:00:00+09:00", 37.54, 126.95)));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().sourceImageId()).isEqualTo("img-2");
    }

    @Test
    void 위치가_불완전한_사진만_있으면_INSUFFICIENT_IMAGE_METADATA를_던진다() {
        when(reverseGeocodeClient.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(new GeoLocation("대한민국", null, "서울특별시", "마포구", "프릳츠 도화",
                        List.of("cafe")));

        assertThatThrownBy(() -> preprocessor().preprocess(List.of(
                new ImageMetadata("img-1", "2026-07-14T10:00:00+09:00", 37.54, 126.95))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_IMAGE_METADATA);
    }

    @Test
    void capturedAt_형식이_잘못되면_INSUFFICIENT_IMAGE_METADATA를_던진다() {
        when(reverseGeocodeClient.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(new GeoLocation("대한민국", null, null, null, null, List.of()));

        assertThatThrownBy(() -> preprocessor().preprocess(List.of(
                new ImageMetadata("img-1", "not-a-date", 33.45, 126.94))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_IMAGE_METADATA);
    }
}
