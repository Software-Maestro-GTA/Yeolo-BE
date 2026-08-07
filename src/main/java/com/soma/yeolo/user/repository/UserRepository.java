package com.soma.yeolo.user.repository;

import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByProviderAndProviderUserId(Provider provider, String providerUserId);

    /**
     * 다른 사용자가 이미 쓰고 있는 이메일인지 (API-USER-1 §"409: 이미 사용 중인 이메일").
     * 탈퇴한 계정({@code deletedAt != null})이 이메일을 영구히 붙잡고 있지 않도록 제외한다.
     */
    boolean existsByEmailAndDeletedAtIsNullAndIdNot(String email, UUID id);
}
