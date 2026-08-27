package com.soma.yeolo.consent.controller;

import com.soma.yeolo.consent.dto.PhotoConsentRequest;
import com.soma.yeolo.consent.dto.PhotoConsentResponse;
import com.soma.yeolo.consent.service.PhotoAnalysisConsentService;
import com.soma.yeolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사진 데이터 분석 동의 API (API-PREF-2). 인증된 사용자의 동의·철회를 기록한다.
 */
@RestController
@RequiredArgsConstructor
public class PhotoAnalysisConsentController {

    private final PhotoAnalysisConsentService photoAnalysisConsentService;

    /**
     * 사진 데이터 분석 동의 저장 (API-PREF-2).
     * 입력값 위반은 전역 핸들러가 명세 문구 그대로 400으로 응답한다.
     */
    @PostMapping("/api/users/me/consents/photo")
    public ApiResponse<PhotoConsentResponse> savePhotoConsent(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody PhotoConsentRequest request) {
        return ApiResponse.success("사진 데이터 분석 동의 저장 성공",
                PhotoConsentResponse.from(photoAnalysisConsentService.save(userId, request)));
    }
}
