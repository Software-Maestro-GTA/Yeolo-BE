package com.soma.yeolo.user.repository;

import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByProviderAndProviderUserId(Provider provider, String providerUserId);

    /**
     * 탈퇴 처리된 사용자인지 (API-USER-2). 인증 필터가 남은 Access Token을 걸러내는 데 쓴다.
     * {@code deletedAt}은 탈퇴 시에만 채워지므로 이 값 하나로 판정한다.
     */
    boolean existsByIdAndDeletedAtIsNotNull(UUID id);
}
