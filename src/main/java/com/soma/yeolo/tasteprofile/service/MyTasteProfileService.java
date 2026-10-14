package com.soma.yeolo.tasteprofile.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.location.service.LocationImageReader;
import com.soma.yeolo.tasteprofile.domain.SavedTasteProfile;
import com.soma.yeolo.tasteprofile.dto.TasteSummaryResponse;
import com.soma.yeolo.tasteprofile.dto.TasteSummaryResponse.RecommendCity;
import com.soma.yeolo.tasteprofile.service.port.TasteProfileRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 내 취향 조회 (API-PREF-4 / FUN-4, 2026-10 개정). 저장된 최신 프로필의 <b>요약</b>(API-AI-1 summary)을
 * 명세의 응답 스키마로 돌려준다. 8개 영역의 취향 지표는 더 이상 FE 로 나가지 않는다.
 *
 * <p>요약이 없는 프로필(개정 전 AI 응답으로 만들어진 것)은 사용자에게 보여줄 내용이 없으므로 명세의
 * 404("저장된 성향 프로필이 없습니다.")로 응답한다 — FE 는 이를 "분석 필요"로 다루고, 다시 분석하면
 * 요약이 있는 새 프로필이 저장된다.
 *
 * <p>{@code recommendCity.imageUrl}은 AI 가 주지 않아 도시 사진 데이터셋(#97)에서 조회 시점에 채운다.
 * 저장 시점에 채우지 않는 이유는 데이터셋이 갱신되면 바로 반영되게 하기 위해서다. 없으면 빈 문자열.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyTasteProfileService {

    // 저장된 AI 원본 JSON에는 명세에 없는 필드가 섞일 수 있으므로 미지 필드는 무시하고 역직렬화한다.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final TasteProfileRepository tasteProfileRepository;
    private final LocationImageReader locationImageReader;

    public TasteSummaryResponse getMyTasteProfile(UUID userId) {
        SavedTasteProfile saved = tasteProfileRepository.findLatestByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASTE_PROFILE_NOT_FOUND));
        if (saved.summaryJson() == null || saved.summaryJson().isBlank()) {
            log.info("요약이 없는 취향 프로필이라 404 로 응답한다(개정 전 분석 결과). userId={}, profileId={}",
                    userId, saved.id());
            throw new BusinessException(ErrorCode.TASTE_PROFILE_NOT_FOUND);
        }
        TasteSummaryResponse summary = parseSummary(saved.summaryJson());
        return summary.withRecommendCity(withImage(summary.recommendCity()));
    }

    private RecommendCity withImage(RecommendCity city) {
        if (city == null) {
            // AI 가 추천 도시를 비워 보낸 경우. 명세상 객체가 있어야 하므로 빈 값으로 채워 내린다.
            return new RecommendCity("", "", "", "");
        }
        String imageUrl = locationImageReader.findCityImageUrl(city.countryName(), city.cityName())
                .orElse("");
        return city.withImageUrl(imageUrl);
    }

    private TasteSummaryResponse parseSummary(String summaryJson) {
        try {
            TasteSummaryResponse parsed = OBJECT_MAPPER.readValue(summaryJson, TasteSummaryResponse.class);
            // 배열 필드는 null 대신 빈 배열로 — FE 가 length 를 바로 읽는다.
            return new TasteSummaryResponse(
                    parsed.intro(),
                    parsed.hashtags() == null ? List.of() : parsed.hashtags(),
                    parsed.types() == null ? List.of() : parsed.types(),
                    parsed.recommendCity());
        } catch (Exception e) {
            // 저장된 요약 JSON이 손상된 예외적 상황 — 500으로 노출한다.
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
    }
}
