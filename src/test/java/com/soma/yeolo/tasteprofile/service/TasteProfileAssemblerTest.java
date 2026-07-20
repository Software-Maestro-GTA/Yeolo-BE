package com.soma.yeolo.tasteprofile.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.tasteprofile.domain.SourceType;
import com.soma.yeolo.tasteprofile.domain.TasteProfile;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TasteProfileAssemblerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TasteProfileAssembler assembler = new TasteProfileAssembler();

    private JsonNode node(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    @Test
    void AI_결과의_상위분류를_추출하고_원본JSON을_보존한다() throws Exception {
        UUID userId = UUID.randomUUID();
        JsonNode profile = node("""
                {
                  "sourceType": "behavior",
                  "travelPaceDensity": "balanced",
                  "spendingTendency": "cost_effective",
                  "companionType": "friends",
                  "seasonalEnvironmentPreference": ["warm_region", "off_season"],
                  "travelPurpose": {"relaxation": 4}
                }
                """);

        TasteProfile result = assembler.toDomain(userId, profile);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.sourceType()).isEqualTo(SourceType.BEHAVIOR);
        assertThat(result.travelPaceDensity()).isEqualTo("balanced");
        assertThat(result.spendingTendency()).isEqualTo("cost_effective");
        assertThat(result.companionType()).isEqualTo("friends");
        assertThat(result.seasonalEnvironmentPreference()).containsExactly("warm_region", "off_season");
        assertThat(result.profileJson()).contains("\"relaxation\":4");
    }

    @Test
    void sourceType이_없으면_behavior로_기본_설정한다() throws Exception {
        TasteProfile result = assembler.toDomain(UUID.randomUUID(), node("{}"));

        assertThat(result.sourceType()).isEqualTo(SourceType.BEHAVIOR);
        assertThat(result.travelPaceDensity()).isNull();
        assertThat(result.seasonalEnvironmentPreference()).isEmpty();
    }
}
