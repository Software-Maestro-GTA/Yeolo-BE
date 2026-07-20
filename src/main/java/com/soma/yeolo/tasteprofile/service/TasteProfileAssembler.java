package com.soma.yeolo.tasteprofile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.soma.yeolo.tasteprofile.domain.SourceType;
import com.soma.yeolo.tasteprofile.domain.TasteProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * AI가 산출한 {@code tasteProfile} JSON을 저장용 {@link TasteProfile} 도메인으로 조립한다.
 * 세부 점수 지표 전체는 원본 JSON으로 보존하고, DOM-1 §6의 상위 분류 컬럼만 승격 추출한다.
 */
@Component
public class TasteProfileAssembler {

    /** behavior 분석 결과 노드를 소유자에 연결된 성향 프로필 도메인으로 변환한다. */
    public TasteProfile toDomain(UUID userId, JsonNode tasteProfile) {
        return new TasteProfile(
                userId,
                resolveSourceType(tasteProfile),
                tasteProfile.toString(),
                text(tasteProfile, "travelPaceDensity"),
                text(tasteProfile, "spendingTendency"),
                text(tasteProfile, "companionType"),
                stringList(tasteProfile, "seasonalEnvironmentPreference")
        );
    }

    private SourceType resolveSourceType(JsonNode node) {
        String value = text(node, "sourceType");
        return value == null ? SourceType.BEHAVIOR : SourceType.fromValue(value);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private List<String> stringList(JsonNode node, String field) {
        JsonNode array = node.get(field);
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(array.size());
        array.forEach(item -> result.add(item.asText()));
        return result;
    }
}
