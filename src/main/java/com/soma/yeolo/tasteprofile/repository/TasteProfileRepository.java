package com.soma.yeolo.tasteprofile.repository;

import com.soma.yeolo.tasteprofile.entity.TasteProfileEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TasteProfileRepository extends JpaRepository<TasteProfileEntity, UUID> {
}
