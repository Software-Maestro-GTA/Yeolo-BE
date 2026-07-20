package com.soma.yeolo.tasteprofile.service.port;

import com.soma.yeolo.tasteprofile.domain.TasteProfile;
import java.util.UUID;

/**
 * 성향 프로필 저장 포트. 서비스(응용 계층)가 소유하는 아웃바운드 인터페이스로, 순수 도메인
 * {@link TasteProfile}만 주고받는다. JPA·Spring Data 등 영속성 세부는 알지 못한다(DIP).
 *
 * <p>구현체(어댑터)는 {@code repository/} 계층에 두어 도메인↔엔티티 매핑을 담당한다.
 * (docs/architecture.md §1)
 */
public interface TasteProfileStore {

    /**
     * 성향 프로필을 저장하고 부여된 식별자를 반환한다.
     *
     * @return 저장된 프로필의 id
     */
    UUID save(TasteProfile profile);
}
