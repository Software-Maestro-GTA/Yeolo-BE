package com.soma.yeolo.share.controller;

import com.soma.yeolo.global.response.ApiResponse;
import com.soma.yeolo.share.dto.ShareAcceptResponse;
import com.soma.yeolo.share.dto.ShareLinkCreateResponse;
import com.soma.yeolo.share.dto.SharePreviewResponse;
import com.soma.yeolo.share.service.ShareLinkService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 코스 친구 초대 공유 링크 API (API-SHARE-1 생성 / API-SHARE-2 조회 / API-SHARE-3 수락, DOM-6).
 *
 * <p>경로가 {@code /api/courses/...}와 {@code /api/share-links/...}로 나뉘어 클래스 단위
 * {@code @RequestMapping}을 두지 않고 메서드마다 전체 경로를 적는다. 조회만 비인증 공개이며
 * (DOM-6 §비로그인 사용자 처리) 인가 설정은 {@code SecurityConfig}에 있다.
 */
@RestController
@RequiredArgsConstructor
public class ShareLinkController {

    private final ShareLinkService shareLinkService;

    /**
     * 공유 링크 생성 (API-SHARE-1). 코스 소유자만 발급할 수 있다.
     * 코스가 없으면 404, 타인 코스면 403으로 전역 핸들러가 응답한다.
     */
    @PostMapping("/api/courses/{courseId}/share-links")
    public ApiResponse<ShareLinkCreateResponse> createShareLink(@AuthenticationPrincipal UUID userId,
                                                                @PathVariable UUID courseId) {
        return ApiResponse.success("여행 코스 공유 링크 생성 성공",
                shareLinkService.createShareLink(userId, courseId));
    }

    /**
     * 공유 링크 조회 (API-SHARE-2). 인증 없이 호출할 수 있는 미리보기다.
     * 유효하지 않은 링크·삭제된 코스는 404, 만료·회수된 링크는 410이다.
     */
    @GetMapping("/api/share-links/{shareToken}")
    public ApiResponse<SharePreviewResponse> getPreview(@PathVariable String shareToken) {
        return ApiResponse.success("여행 코스 공유 링크 조회 성공",
                shareLinkService.getPreview(shareToken));
    }

    /**
     * 공유 링크 수락 (API-SHARE-3). 수락하면 코스가 내 목록(API-COURSE-3)에 나타난다.
     * 이미 수락했거나 자기 자신의 코스면 400이다.
     */
    @PostMapping("/api/share-links/{shareToken}/accept")
    public ApiResponse<ShareAcceptResponse> accept(@AuthenticationPrincipal UUID userId,
                                                   @PathVariable String shareToken) {
        return ApiResponse.success("여행 코스 공유 수락 성공",
                shareLinkService.accept(userId, shareToken));
    }
}
