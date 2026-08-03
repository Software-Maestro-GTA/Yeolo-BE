package com.soma.yeolo.user.repository;

import com.soma.yeolo.user.entity.UserPreferenceEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 선호(MBTI) 영속 저장소. 코스 생성이 소비할 읽기 조회만 사용한다.
 * (쓰기 API-PREF-1은 별도 작업 담당 — TSK-25/29)
 */
public interface UserPreferenceJpaRepository extends JpaRepository<UserPreferenceEntity, UUID> {

    Optional<UserPreferenceEntity> findByUserId(UUID userId);
}
