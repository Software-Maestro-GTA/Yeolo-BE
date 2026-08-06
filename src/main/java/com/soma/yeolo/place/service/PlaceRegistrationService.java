package com.soma.yeolo.place.service;

import com.soma.yeolo.place.client.PlaceLookupClient;
import com.soma.yeolo.place.domain.PlaceQuery;
import com.soma.yeolo.place.domain.SavedPlace;
import com.soma.yeolo.place.service.port.PlaceRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 장소 정규화·등록 (DOM-3 §"장소 정보 처리 기준").
 *
 * <p>AI가 장소명·분류만 주므로(API-AI-2), 외부 provider로 좌표·주소를 채운 뒤 내부 장소로 저장하고
 * 내부 {@code placeId}를 부여한다. 같은 장소는 provider 식별자 기준으로 한 번만 저장되어, 저장된
 * 행이 곧 외부 조회 결과의 캐시가 된다.
 *
 * <p><b>캐시 갱신 정책:</b> 한 번 저장한 장소는 다시 조회하지 않고 기존 행을 그대로 돌려준다
 * (평점·운영시간·분류는 변할 수 있지만 갱신하지 않는다). 주기적 갱신·TTL은 운영 데이터를 보고 정할
 * 사항이라 이번 범위에 넣지 않았다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceRegistrationService implements PlaceRegistry {

    private final PlaceLookupClient placeLookupClient;
    private final PlaceRepository placeRepository;

    /**
     * 장소를 조회해 등록한다. provider가 찾지 못하면 빈 값을 돌려준다 — 호출부(코스 생성)는 해당
     * stop을 정규화하지 않은 채 진행한다.
     *
     * <p><b>트랜잭션을 걸지 않는다.</b> 외부 조회(HTTP + OSM의 레이트리밋 대기)가 이 메서드의
     * 대부분을 차지하는데, 트랜잭션을 열면 그 시간 내내 DB 커넥션을 붙잡아 코스 생성이 동시에
     * 여러 건 돌 때 풀이 마른다. 저장은 {@code saveIfAbsent} 내부의 Spring Data 트랜잭션으로 충분하며,
     * 오히려 이렇게 해야 중복 저장 경합 시 unique 위반을 잡아 재조회하는 복구 경로가 동작한다
     * (외부 트랜잭션 안에서 잡으면 이미 rollback-only로 표시되어 재조회가 실패한다).
     */
    @Override
    public Optional<SavedPlace> resolve(PlaceQuery query) {
        Optional<SavedPlace> place = placeLookupClient.lookup(query)
                .map(found -> placeRepository.saveIfAbsent(found.withCategory(query.category())));
        if (place.isEmpty()) {
            log.info("장소를 정규화하지 못했다(조회 결과 없음) - '{}'", query.searchText());
        }
        return place;
    }
}
