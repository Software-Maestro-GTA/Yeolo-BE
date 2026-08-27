package com.soma.yeolo.consent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.soma.yeolo.consent.dto.PhotoConsentRequest;
import com.soma.yeolo.consent.entity.PhotoAnalysisConsent;
import com.soma.yeolo.consent.repository.PhotoAnalysisConsentRepository;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PhotoAnalysisConsentServiceTest {

    @Mock
    private PhotoAnalysisConsentRepository consentRepository;

    @InjectMocks
    private PhotoAnalysisConsentService consentService;

    private final UUID userId = UUID.randomUUID();

    private PhotoAnalysisConsent history(boolean agreed) {
        return PhotoAnalysisConsent.record(userId, agreed, "v1.0", Instant.now());
    }

    @Test
    void 동의를_저장하면_입력값과_서버시각이_기록된다() {
        Instant before = Instant.now();
        when(consentRepository.saveAndFlush(any(PhotoAnalysisConsent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PhotoAnalysisConsent saved =
                consentService.save(userId, new PhotoConsentRequest(true, "v1.0"));

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.isAgreed()).isTrue();
        assertThat(saved.getConsentVersion()).isEqualTo("v1.0");
        // 클라이언트가 보낸 시각이 아니라 서버 시각으로 찍혀야 증빙으로 쓸 수 있다.
        assertThat(saved.getAgreedAt()).isBetween(before, Instant.now());
    }

    @Test
    void 철회도_같은_API로_agreed_false_이력을_쌓는다() {
        when(consentRepository.saveAndFlush(any(PhotoAnalysisConsent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PhotoAnalysisConsent saved =
                consentService.save(userId, new PhotoConsentRequest(false, "v1.0"));

        assertThat(saved.isAgreed()).isFalse();
    }

    @Test
    void 저장에_실패하면_500_에러코드로_변환한다() {
        when(consentRepository.saveAndFlush(any(PhotoAnalysisConsent.class)))
                .thenThrow(new DataIntegrityViolationException("db down"));

        assertThatThrownBy(() -> consentService.save(userId, new PhotoConsentRequest(true, "v1.0")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PHOTO_CONSENT_SAVE_FAILED);
    }

    @Test
    void 동의_이력이_없으면_미동의로_본다() {
        when(consentRepository.findTopByUserIdOrderByAgreedAtDescCreatedAtDesc(userId))
                .thenReturn(Optional.empty());

        assertThat(consentService.hasAgreed(userId)).isFalse();
    }

    @Test
    void 최신_이력이_동의면_동의로_본다() {
        when(consentRepository.findTopByUserIdOrderByAgreedAtDescCreatedAtDesc(userId))
                .thenReturn(Optional.of(history(true)));

        assertThat(consentService.hasAgreed(userId)).isTrue();
    }

    @Test
    void 최신_이력이_철회면_이전에_동의했더라도_미동의로_본다() {
        // append-only 이력이므로 과거 동의 행은 남아 있다. 판정 기준은 항상 최신 1건이다.
        when(consentRepository.findTopByUserIdOrderByAgreedAtDescCreatedAtDesc(userId))
                .thenReturn(Optional.of(history(false)));

        assertThat(consentService.hasAgreed(userId)).isFalse();
    }
}
