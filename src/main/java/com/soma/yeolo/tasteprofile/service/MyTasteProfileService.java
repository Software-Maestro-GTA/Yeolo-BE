package com.soma.yeolo.tasteprofile.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.domain.SavedTasteProfile;
import com.soma.yeolo.tasteprofile.dto.MyTasteProfileResponse;
import com.soma.yeolo.tasteprofile.dto.TasteProfilePayload;
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
 * <p>저장된 AI 원본 JSON을 {@link TasteProfilePayload} 타입으로 역직렬화해 세부 지표를 그대로 담고,
 * 영속 이후 확정된 권위 값({@code tasteProfileId}·{@code userId}·{@code sourceType}·{@code updatedAt})만
 * 덮어써 반환한다. 저장된 프로필이 없으면 404({@code TASTE_PROFILE_NOT_FOUND})로 응답한다.
 */
@Service
@RequiredArgsConstructor
public class MyTasteProfileService {

    /** 갱신일 표기 기준 시간대. 명세의 updatedAt(YYYY-MM-DD)은 한국 로컬 날짜로 표기한다. */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");
    // 저장된 AI 원본 JSON에는 명세에 없는 필드가 섞일 수 있으므로 미지 필드는 무시하고 역직렬화한다.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final TasteProfileRepository tasteProfileRepository;

    /** 사용자의 최신 성향 프로필을 조회해 명세 응답으로 반환한다. 없으면 404 예외. */
    public MyTasteProfileResponse getMyTasteProfile(UUID userId) {
        SavedTasteProfile saved = tasteProfileRepository.findLatestByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASTE_PROFILE_NOT_FOUND));
        return new MyTasteProfileResponse(toPayload(saved));
    }

    /** 저장된 원본 JSON을 응답 타입으로 역직렬화하고 권위 식별자·갱신일을 덮어쓴다. */
    private TasteProfilePayload toPayload(SavedTasteProfile saved) {
        String updatedAt = LocalDate.ofInstant(saved.updatedAt(), DISPLAY_ZONE).toString();
        return parseProfile(saved.profileJson()).withAuthority(
                saved.id().toString(),
                saved.userId().toString(),
                saved.sourceType().getValue(),
                updatedAt);
    }

    private TasteProfilePayload parseProfile(String profileJson) {
        try {
            return OBJECT_MAPPER.readValue(profileJson, TasteProfilePayload.class);
        } catch (Exception e) {
            // 저장된 성향 JSON이 손상된 예외적 상황 — 500으로 노출한다.
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
    }
}
