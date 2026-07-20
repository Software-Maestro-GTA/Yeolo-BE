package com.soma.yeolo.tasteprofile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.domain.SavedTasteProfile;
import com.soma.yeolo.tasteprofile.domain.SourceType;
import com.soma.yeolo.tasteprofile.domain.TasteProfile;
import com.soma.yeolo.tasteprofile.dto.MyTasteProfileResponse;
import com.soma.yeolo.tasteprofile.service.port.TasteProfileRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MyTasteProfileServiceTest {

    /** 영속 포트 fake: 미리 넣어 둔 프로필을 userId로 돌려준다(DB·리플렉션 불필요). */
    private static final class FakeTasteProfileRepository implements TasteProfileRepository {
        private SavedTasteProfile stored;

        @Override
        public UUID save(TasteProfile profile) {
            throw new UnsupportedOperationException("조회 테스트에서는 저장을 사용하지 않는다.");
        }

        @Override
        public Optional<SavedTasteProfile> findLatestByUserId(UUID userId) {
            return Optional.ofNullable(stored)
                    .filter(profile -> profile.userId().equals(userId));
        }
    }

    private final FakeTasteProfileRepository repository = new FakeTasteProfileRepository();
    private final MyTasteProfileService service = new MyTasteProfileService(repository);

    @Test
    void 저장된_프로필이_없으면_404_예외를_던진다() {
        assertThatThrownBy(() -> service.getMyTasteProfile(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TASTE_PROFILE_NOT_FOUND);
    }

    @Test
    void 저장된_원본_JSON에_권위_식별자와_갱신일을_덮어써_반환한다() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        // 원본 JSON은 AI가 산출한 placeholder id/updatedAt과 세부 지표를 담고 있다.
        String profileJson = """
                {
                  "tasteProfileId": "ai-placeholder",
                  "userId": "ai-placeholder",
                  "sourceType": "survey",
                  "updatedAt": "2000-01-01",
                  "travelPurpose": {"relaxation": 4, "gourmet": 5},
                  "seasonalEnvironmentPreference": ["warm_region"]
                }
                """;
        // 2026-07-13T20:00Z == 2026-07-14 05:00 KST → 로컬 날짜는 07-14 로 표기되어야 한다.
        Instant updatedAt = Instant.parse("2026-07-13T20:00:00Z");
        repository.stored = new SavedTasteProfile(profileId, userId, SourceType.BEHAVIOR, updatedAt, profileJson);

        MyTasteProfileResponse response = service.getMyTasteProfile(userId);
        JsonNode node = response.tasteProfile();

        // 권위 값으로 덮어써짐
        assertThat(node.get("tasteProfileId").asText()).isEqualTo(profileId.toString());
        assertThat(node.get("userId").asText()).isEqualTo(userId.toString());
        assertThat(node.get("sourceType").asText()).isEqualTo("behavior");
        assertThat(node.get("updatedAt").asText()).isEqualTo("2026-07-14");
        // 세부 지표는 원본 그대로 보존
        assertThat(node.get("travelPurpose").get("gourmet").asInt()).isEqualTo(5);
        assertThat(node.get("seasonalEnvironmentPreference").get(0).asText()).isEqualTo("warm_region");
    }
}
