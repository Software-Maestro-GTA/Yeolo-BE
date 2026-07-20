package com.soma.yeolo.tasteprofile.service.port;

import com.soma.yeolo.tasteprofile.domain.SavedTasteProfile;
import com.soma.yeolo.tasteprofile.domain.TasteProfile;
import java.util.Optional;
import java.util.UUID;

/**
 * 성향 프로필 영속 포트. 서비스(응용 계층)가 소유하는 아웃바운드 인터페이스로, 순수 도메인
 * {@link TasteProfile}·{@link SavedTasteProfile}만 주고받는다. JPA·Spring Data 등 영속성
 * 세부는 알지 못한다(DIP). 애그리거트당 포트 하나로 저장·조회를 함께 둔다(CQRS 분리 지양).
 *
 * <p>구현체 {@code TasteProfileRepositoryImpl}({@code repository/})가 Spring Data
 * {@code TasteProfileJpaRepository}에 위임하며 도메인↔엔티티 매핑을 담당한다. (docs/architecture.md §1-2)
 */
public interface TasteProfileRepository {

    /**
     * 성향 프로필을 저장하고 부여된 식별자를 반환한다.
     *
     * @return 저장된 프로필의 id
     */
    UUID save(TasteProfile profile);

    /**
     * 사용자의 최신 성향 프로필을 조회한다. 저장된 프로필이 없으면 빈 값을 반환한다.
     * (사용자는 재분석 시마다 새 프로필을 저장하므로 가장 최근 갱신본을 "내 성향 프로필"로 본다.)
     */
    Optional<SavedTasteProfile> findLatestByUserId(UUID userId);
}
