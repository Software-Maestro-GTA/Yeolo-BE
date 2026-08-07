package com.soma.yeolo.preference.service;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.preference.domain.Mbti;
import com.soma.yeolo.preference.dto.UserPreferenceRequest;
import com.soma.yeolo.preference.entity.UserPreference;
import com.soma.yeolo.preference.repository.UserPreferenceRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 선호 입력값 저장·조회 (API-PREF-1 / FUN-8).
 *
 * <p>사용자당 1행을 upsert 한다 — MBTI는 이력이 아니라 "현재 값"만 의미가 있기 때문이다
 * (동의 이력과 대비된다). {@link UserPreference}는 "엔티티=도메인" 병합형이라 도메인 객체로
 * 그대로 주고받는다 (docs/architecture.md §1-1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceService implements UserMbtiReader {

    private final UserPreferenceRepository userPreferenceRepository;

    /**
     * MBTI를 저장하거나 갱신한다 (API-PREF-1).
     * 16유형이 아닌 값은 명세 문구 그대로 400으로 거절한다.
     */
    @Transactional
    public Mbti updateMbti(UUID userId, UserPreferenceRequest request) {
        Mbti mbti = Mbti.fromValue(request.mbti())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_MBTI));
        upsert(userId, mbti);
        return mbti;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Mbti> findMbti(UUID userId) {
        return userPreferenceRepository.findByUserId(userId).map(UserPreference::getMbti);
    }

    /**
     * 기존 행이 있으면 갱신, 없으면 생성한다.
     *
     * <p>두 요청이 동시에 "없음"을 확인하면 둘 다 INSERT를 시도해 {@code uk_user_preferences_user_id}에
     * 걸린다 — 유니크 제약이 최종 방어선이고, 진 쪽은 500을 받는다. 같은 트랜잭션 안에서 잡아
     * 재시도하지 않는 이유는 그게 동작하지 않기 때문이다: 제약 위반이 나면 Hibernate가 세션을
     * rollback-only로 표시해, 이어지는 조회·갱신은 커밋 시점에 다시 터진다(원인만 가려진다).
     * 사용자가 같은 값을 두 번 눌렀을 때만 생기는 드문 경합이고 재요청하면 정상 처리되므로,
     * 재시도는 클라이언트에 맡긴다.
     */
    private void upsert(UUID userId, Mbti mbti) {
        userPreferenceRepository.findByUserId(userId)
                .ifPresentOrElse(
                        preference -> preference.updateMbti(mbti),
                        () -> userPreferenceRepository.save(UserPreference.of(userId, mbti)));
    }
}
