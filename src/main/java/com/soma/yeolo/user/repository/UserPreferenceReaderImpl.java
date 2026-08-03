package com.soma.yeolo.user.repository;

import com.soma.yeolo.user.service.port.UserPreferenceReader;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UserPreferenceReader} 구현. Spring Data {@link UserPreferenceJpaRepository}에 위임해
 * 사용자 선호(MBTI)를 조회한다. 저장된 선호가 없거나 MBTI가 공백이면 빈 값으로 정규화한다.
 */
@Repository
@RequiredArgsConstructor
public class UserPreferenceReaderImpl implements UserPreferenceReader {

    private final UserPreferenceJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findMbtiByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId)
                .map(entity -> entity.getMbti())
                .filter(mbti -> mbti != null && !mbti.isBlank());
    }
}
