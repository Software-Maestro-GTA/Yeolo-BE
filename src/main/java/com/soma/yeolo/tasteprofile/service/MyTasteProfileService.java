package com.soma.yeolo.tasteprofile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.domain.SavedTasteProfile;
import com.soma.yeolo.tasteprofile.dto.MyTasteProfileResponse;
import com.soma.yeolo.tasteprofile.service.port.TasteProfileRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 내 성향 프로필 조회 (API-FB-8 / FUN-4). 저장된 최신 성향 프로필을 읽어 명세의 tasteProfile
 * 응답으로 조립한다.
 *
 * <p>저장된 AI 원본 JSON에 세부 지표(travelPurpose 등)가 모두 보존되어 있으므로 이를 기반으로,
 * 영속 이후 확정된 권위 값({@code tasteProfileId}·{@code userId}·{@code sourceType}·{@code updatedAt})만
 * 덮어써 반환한다. 저장된 프로필이 없으면 404({@code TASTE_PROFILE_NOT_FOUND})로 응답한다.
 */
@Service
@RequiredArgsConstructor
public class MyTasteProfileService {

    /** 갱신일 표기 기준 시간대. 명세의 updatedAt(YYYY-MM-DD)은 한국 로컬 날짜로 표기한다. */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TasteProfileRepository tasteProfileRepository;

    /** 사용자의 최신 성향 프로필을 조회해 명세 응답으로 반환한다. 없으면 404 예외. */
    public MyTasteProfileResponse getMyTasteProfile(UUID userId) {
        SavedTasteProfile saved = tasteProfileRepository.findLatestByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASTE_PROFILE_NOT_FOUND));
        return new MyTasteProfileResponse(toTasteProfileNode(saved));
    }

    /** 저장된 원본 JSON에 권위 식별자·갱신일을 덮어써 응답용 tasteProfile 노드를 만든다. */
    private JsonNode toTasteProfileNode(SavedTasteProfile saved) {
        ObjectNode node = parseProfile(saved.profileJson());
        node.put("tasteProfileId", saved.id().toString());
        node.put("userId", saved.userId().toString());
        node.put("sourceType", saved.sourceType().getValue());
        node.put("updatedAt", LocalDate.ofInstant(saved.updatedAt(), DISPLAY_ZONE).toString());
        return node;
    }

    private ObjectNode parseProfile(String profileJson) {
        try {
JsonNode parsed = OBJECT_MAPPER.readTree(profileJson);
if (!(parsed instanceof ObjectNode object)) {
    throw new IllegalStateException("tasteProfile must be a JSON object");
}
return object;
        } catch (Exception e) {
            // 저장된 성향 JSON이 손상된 예외적 상황 — 500으로 노출한다.
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
    }
}
