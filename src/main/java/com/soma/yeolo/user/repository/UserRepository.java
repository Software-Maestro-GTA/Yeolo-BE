package com.soma.yeolo.user.repository;

import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByProviderAndProviderUserId(Provider provider, String providerUserId);
}
