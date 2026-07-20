package com.soma.yeolo.tasteprofile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.client.AiTasteProfileClient;
import com.soma.yeolo.tasteprofile.domain.GeoLocation;
import com.soma.yeolo.tasteprofile.domain.PreprocessedImage;
import com.soma.yeolo.tasteprofile.domain.SourceType;
import com.soma.yeolo.tasteprofile.domain.TasteProfile;
import com.soma.yeolo.tasteprofile.domain.TimeContext;
import com.soma.yeolo.tasteprofile.dto.BehaviorAnalysisRequest;
import com.soma.yeolo.tasteprofile.dto.BehaviorAnalysisRequest.ImageMetadata;
import com.soma.yeolo.tasteprofile.service.port.TasteProfileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

@ExtendWith(MockitoExtension.class)
class BehaviorTasteProfileServiceTest {

    @Mock
    private ImageMetadataPreprocessor preprocessor;
    @Mock
    private AiTasteProfileClient aiClient;
    @Mock
    private TasteProfileAssembler assembler;
    @Mock
    private SseEmitter emitter;

    /** 영속 포트 fake: 저장된 도메인을 기록하고 미리 정해둔 id를 돌려준다(JPA·리플렉션 불필요). */
    private static final class FakeTasteProfileRepository implements TasteProfileRepository {
        private final UUID assignedId = UUID.randomUUID();
        private final List<TasteProfile> saved = new ArrayList<>();

        @Override
        public UUID save(TasteProfile profile) {
            saved.add(profile);
            return assignedId;
        }

        @Override
        public java.util.Optional<com.soma.yeolo.tasteprofile.domain.SavedTasteProfile> findLatestByUserId(UUID userId) {
            throw new UnsupportedOperationException("행동 분석 테스트에서는 조회를 사용하지 않는다.");
        }
    }

    private final FakeTasteProfileRepository store = new FakeTasteProfileRepository();

    private BehaviorTasteProfileService service() {
        return new BehaviorTasteProfileService(preprocessor, aiClient, assembler, store);
    }

    private BehaviorAnalysisRequest request() {
        return new BehaviorAnalysisRequest(List.of(
                new ImageMetadata("img-1", "2026-07-14T10:00:00+09:00", 33.45, 126.94, "Asia/Seoul")));
    }

    private PreprocessedImage preprocessed() {
        return new PreprocessedImage("img-1",
                new GeoLocation("대한민국", "제주", "서귀포", "성산읍", "성산일출봉", List.of("tourist_attraction")),
                TimeContext.derive("2026-07-14T10:00:00+09:00", "Asia/Seoul"));
    }

    @Test
    void 성공하면_두_progress와_complete를_보내고_저장한다() throws Exception {
        UUID userId = UUID.randomUUID();
        TasteProfile domain = new TasteProfile(userId, SourceType.BEHAVIOR, "{}", null, null, null, List.of());

        when(preprocessor.preprocess(any())).thenReturn(List.of(preprocessed()));
        when(aiClient.analyzeBehavior(any()))
                .thenReturn(new ObjectMapper().readTree("{\"sourceType\":\"behavior\"}"));
        when(assembler.toDomain(any(), any())).thenReturn(domain);

        service().analyzeAndStream(userId, request(), emitter);

        // progress x2 + complete x1 = send 3회, 정상 종료
        verify(emitter, times(3)).send(any(SseEventBuilder.class));
        assertThat(store.saved).containsExactly(domain);
        verify(emitter).complete();
    }

    @Test
    void 전처리_실패시_error_이벤트를_보내고_AI를_호출하지_않는다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(preprocessor.preprocess(any()))
                .thenThrow(new BusinessException(ErrorCode.INSUFFICIENT_IMAGE_METADATA));

        service().analyzeAndStream(userId, request(), emitter);

        // 첫 progress(1) + error(1) = send 2회
        verify(emitter, times(2)).send(any(SseEventBuilder.class));
        verify(aiClient, never()).analyzeBehavior(any());
        assertThat(store.saved).isEmpty();
        verify(emitter).complete();
    }
}
