package com.soma.yeolo.tasteprofile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.location.service.LocationImageReader;
import com.soma.yeolo.tasteprofile.domain.SavedTasteProfile;
import com.soma.yeolo.tasteprofile.domain.SourceType;
import com.soma.yeolo.tasteprofile.domain.TasteProfile;
import com.soma.yeolo.tasteprofile.dto.TasteSummaryResponse;
import com.soma.yeolo.tasteprofile.service.port.TasteProfileRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** API-PREF-4 (2026-10 개정) — 응답은 요약 객체이고, 추천 도시 사진은 BE 가 채운다. */
class MyTasteProfileServiceTest {

    private static final String SUMMARY_JSON = """
            {
              "intro": "느긋한 미식 여행자",
              "hashtags": ["#미식", "#느긋"],
              "types": [
                {"typeCode": "PACE", "typeName": "여행 속도", "lowName": "느긋", "highName": "빡빡", "score": 2.5}
              ],
              "recommendCity": {"countryName": "일본", "cityName": "오사카", "reason": "먹거리가 풍부해요"},
              "extraFromAi": "명세 밖 필드 — 무시되어야 한다"
            }
            """;

    private static final class FakeTasteProfileRepository implements TasteProfileRepository {
        private SavedTasteProfile stored;

        @Override
        public UUID save(TasteProfile profile) {
            throw new UnsupportedOperationException("조회 테스트에서는 저장을 사용하지 않는다.");
        }

        @Override
        public Optional<SavedTasteProfile> findLatestByUserId(UUID userId) {
            return Optional.ofNullable(stored).filter(profile -> profile.userId().equals(userId));
        }

        @Override
        public boolean existsByUserId(UUID userId) {
            return findLatestByUserId(userId).isPresent();
        }

        @Override
        public void deleteByUserId(UUID userId) {
            stored = null;
        }
    }

    private static final class FakeLocationImageReader implements LocationImageReader {
        private final Map<String, String> images = new HashMap<>();

        @Override
        public Optional<String> findCityImageUrl(String countryName, String cityName) {
            return Optional.ofNullable(images.get(countryName + "/" + cityName));
        }
    }

    private final FakeTasteProfileRepository repository = new FakeTasteProfileRepository();
    private final FakeLocationImageReader cityImages = new FakeLocationImageReader();
    private final MyTasteProfileService service = new MyTasteProfileService(repository, cityImages);

    private SavedTasteProfile saved(UUID userId, String summaryJson) {
        return new SavedTasteProfile(UUID.randomUUID(), userId, SourceType.BEHAVIOR,
                Instant.parse("2026-10-01T00:00:00Z"), "{\"travelPaceDensity\":\"balanced\"}", summaryJson);
    }

    @Test
    void 저장된_프로필이_없으면_404_예외를_던진다() {
        assertThatThrownBy(() -> service.getMyTasteProfile(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TASTE_PROFILE_NOT_FOUND);
    }

    @Test
    void 저장된_요약을_명세_스키마로_돌려주고_추천_도시_사진을_채운다() {
        UUID userId = UUID.randomUUID();
        repository.stored = saved(userId, SUMMARY_JSON);
        cityImages.images.put("일본/오사카", "https://cdn.example.com/locations/cities/1853909.jpg");

        TasteSummaryResponse response = service.getMyTasteProfile(userId);

        assertThat(response.intro()).isEqualTo("느긋한 미식 여행자");
        assertThat(response.hashtags()).containsExactly("#미식", "#느긋");
        assertThat(response.types()).singleElement().satisfies(type -> {
            assertThat(type.typeCode()).isEqualTo("PACE");
            assertThat(type.lowName()).isEqualTo("느긋");
            assertThat(type.highName()).isEqualTo("빡빡");
            assertThat(type.score()).isEqualTo(2.5);
        });
        assertThat(response.recommendCity().countryName()).isEqualTo("일본");
        assertThat(response.recommendCity().cityName()).isEqualTo("오사카");
        assertThat(response.recommendCity().reason()).isEqualTo("먹거리가 풍부해요");
        assertThat(response.recommendCity().imageUrl())
                .isEqualTo("https://cdn.example.com/locations/cities/1853909.jpg");
    }

    /** 명세 타입이 string 이라 사진이 없으면 null 이 아니라 빈 문자열이다. */
    @Test
    void 추천_도시_사진이_없으면_빈_문자열이다() {
        UUID userId = UUID.randomUUID();
        repository.stored = saved(userId, SUMMARY_JSON);

        assertThat(service.getMyTasteProfile(userId).recommendCity().imageUrl()).isEmpty();
    }

    /** 개정 전 응답 형태(tasteProfile 래퍼·8개 영역 지표)는 더 이상 나가지 않는다 — 타입이 계약이다. */
    @Test
    void 응답에는_취향_지표와_tasteProfile_래퍼가_없다() {
        UUID userId = UUID.randomUUID();
        repository.stored = saved(userId, SUMMARY_JSON);

        TasteSummaryResponse response = service.getMyTasteProfile(userId);

        assertThat(response.toString()).doesNotContain("travelPaceDensity", "tasteProfile", "extraFromAi");
    }

    /**
     * 개정 전 AI 응답으로 저장된 프로필에는 요약이 없다. 보여줄 내용이 없으므로 명세의 404 로 응답해
     * FE 가 재분석을 유도하게 한다 — 지표만 있는 프로필을 빈 요약으로 내리면 화면이 비어 보인다.
     */
    @Test
    void 요약이_없는_프로필은_404다() {
        UUID userId = UUID.randomUUID();
        repository.stored = saved(userId, null);

        assertThatThrownBy(() -> service.getMyTasteProfile(userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TASTE_PROFILE_NOT_FOUND);
    }

    @Test
    void 요약의_배열이_비어_있어도_null이_아닌_빈_배열로_내린다() {
        UUID userId = UUID.randomUUID();
        repository.stored = saved(userId, "{\"intro\":\"요약\",\"recommendCity\":{\"countryName\":\"일본\",\"cityName\":\"도쿄\"}}");

        TasteSummaryResponse response = service.getMyTasteProfile(userId);

        assertThat(response.hashtags()).isEmpty();
        assertThat(response.types()).isEmpty();
        assertThat(response.recommendCity().reason()).isNull();
    }

    @Test
    void 손상된_요약_JSON은_500이다() {
        UUID userId = UUID.randomUUID();
        repository.stored = saved(userId, "{not json");

        assertThatThrownBy(() -> service.getMyTasteProfile(userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
