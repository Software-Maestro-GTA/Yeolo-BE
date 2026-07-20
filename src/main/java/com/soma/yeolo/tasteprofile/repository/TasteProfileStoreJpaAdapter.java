package com.soma.yeolo.tasteprofile.repository;

import com.soma.yeolo.tasteprofile.domain.TasteProfile;
import com.soma.yeolo.tasteprofile.entity.TasteProfileEntity;
import com.soma.yeolo.tasteprofile.service.port.TasteProfileStore;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link TasteProfileStore} 포트의 JPA 어댑터. 순수 도메인을 {@link TasteProfileEntity}로 매핑해
 * Spring Data 리포지토리에 저장하고, 부여된 식별자만 도메인 세계로 되돌려준다.
 * 도메인↔엔티티 매핑을 이 경계에 격리한다. (docs/architecture.md §1)
 */
@Component
@RequiredArgsConstructor
class TasteProfileStoreJpaAdapter implements TasteProfileStore {

    private final TasteProfileRepository repository;

    @Override
    public UUID save(TasteProfile profile) {
        return repository.save(TasteProfileEntity.from(profile)).getId();
    }
}
