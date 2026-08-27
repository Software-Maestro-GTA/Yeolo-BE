package com.soma.yeolo.preference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.preference.domain.Mbti;
import com.soma.yeolo.preference.dto.UserPreferenceRequest;
import com.soma.yeolo.preference.entity.UserPreference;
import com.soma.yeolo.preference.repository.UserPreferenceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MBTI 저장/수정 규칙(API-PREF-1 인수 기준). 영속 포트는 목으로 대체한다 — 리포지토리가
 * {@code JpaRepository} 인터페이스라 손수 짠 fake 로는 배관 메서드만 늘어난다.
 */
@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTest {

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @InjectMocks
    private UserPreferenceService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    void 선호가_없던_사용자는_새_행으로_저장한다() {
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        service.updateMbti(userId, new UserPreferenceRequest("ENFP"));

        ArgumentCaptor<UserPreference> saved = ArgumentCaptor.forClass(UserPreference.class);
        verify(userPreferenceRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getMbti()).isEqualTo(Mbti.ENFP);
    }

    /** 재입력은 이력이 아니라 현재 값이다 — 새 행을 쌓지 않고 기존 행을 덮어쓴다. */
    @Test
    void 이미_있으면_기존_행을_갱신한다() {
        UserPreference existing = UserPreference.of(userId, Mbti.ISTJ);
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        service.updateMbti(userId, new UserPreferenceRequest("ENFP"));

        assertThat(existing.getMbti()).isEqualTo(Mbti.ENFP);
        verify(userPreferenceRepository, never()).save(any());
    }

    @Test
    void 소문자_입력도_대문자_유형으로_저장한다() {
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(service.updateMbti(userId, new UserPreferenceRequest("enfp")))
                .isEqualTo(Mbti.ENFP);
    }

    @Test
    void 열여섯_유형이_아니면_400이고_저장하지_않는다() {
        assertThatThrownBy(() -> service.updateMbti(userId, new UserPreferenceRequest("XXXX")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_MBTI);

        verify(userPreferenceRepository, never()).saveAndFlush(any());
    }

    @Test
    void 저장된_MBTI를_조회한다() {
        when(userPreferenceRepository.findByUserId(userId))
                .thenReturn(Optional.of(UserPreference.of(userId, Mbti.INTJ)));

        assertThat(service.findMbti(userId)).contains(Mbti.INTJ);
    }

    @Test
    void 선호를_입력한_적이_없으면_빈_값을_돌려준다() {
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(service.findMbti(userId)).isEmpty();
    }
}
