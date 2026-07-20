package com.soma.yeolo.tasteprofile.controller;

import com.soma.yeolo.global.response.ApiResponse;
import com.soma.yeolo.tasteprofile.dto.MyTasteProfileResponse;
import com.soma.yeolo.tasteprofile.service.MyTasteProfileService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 성향 프로필 조회 API (API-FB-8). 인증된 사용자가 저장해 둔 정규화 성향 프로필을 반환한다.
 */
@RestController
@RequiredArgsConstructor
public class MyTasteProfileController {

    private final MyTasteProfileService myTasteProfileService;

    /** 내 성향 프로필 조회 (API-FB-8). 저장된 프로필이 없으면 전역 핸들러가 404로 응답한다. */
    @GetMapping("/api/me/taste-profile")
    public ApiResponse<MyTasteProfileResponse> getMyTasteProfile(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.success("성향 프로필 조회 성공", myTasteProfileService.getMyTasteProfile(userId));
    }
}
