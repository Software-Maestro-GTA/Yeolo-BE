package com.soma.yeolo.preference.repository;

import com.soma.yeolo.preference.entity.UserPreference;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {

    Optional<UserPreference> findByUserId(UUID userId);
}
