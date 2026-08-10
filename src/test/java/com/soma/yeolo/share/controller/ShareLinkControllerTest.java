package com.soma.yeolo.share.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.soma.yeolo.global.config.SecurityConfig;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.security.JwtAuthenticationFilter;
import com.soma.yeolo.global.security.JwtTokenProvider;
import com.soma.yeolo.global.security.RestAuthenticationEntryPoint;
import com.soma.yeolo.global.security.WithdrawnUserChecker;
import com.soma.yeolo.share.dto.ShareAcceptResponse;
import com.soma.yeolo.share.dto.ShareLinkCreateResponse;
import com.soma.yeolo.share.dto.SharePreviewResponse;
import com.soma.yeolo.share.service.ShareLinkService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 공유 링크 API의 배관과 <b>인가 설정</b>을 고정한다 (API-SHARE-1/2/3).
 *
 * <p>컨트롤러는 원칙상 미테스트지만(docs/architecture.md §8), 여기서는 예외로 둔다 — 조회만 공개고
 * 수락은 인증 필요라는 비대칭이 {@code SecurityConfig}의 matcher 하나에 달려 있어서다. matcher를
 * {@code /**}로 넓히거나 {@code GET} 제한을 빼면 수락이 익명에 열리고, 그때
 * {@code @AuthenticationPrincipal UUID userId}가 {@code null}로 바인딩돼 500이 난다.
 */
@WebMvcTest(ShareLinkController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class ShareLinkControllerTest {

    private static final String TOKEN = "0oQ8Zt7_shareToken-example";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShareLinkService shareLinkService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private WithdrawnUserChecker withdrawnUserChecker;

    // ===== API-SHARE-1 생성 =====

    @Test
    void 공유_링크_생성은_명세_메시지와_필드로_응답한다() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(jwtTokenProvider.parseAccessTokenUserId("valid-token")).thenReturn(userId);
        when(shareLinkService.createShareLink(userId, courseId)).thenReturn(
                new ShareLinkCreateResponse("https://yeolo.app/share/" + TOKEN, TOKEN,
                        Instant.parse("2026-08-17T00:00:00Z")));

        mockMvc.perform(post("/api/courses/{courseId}/share-links", courseId)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("여행 코스 공유 링크 생성 성공"))
                .andExpect(jsonPath("$.data.shareUrl").value("https://yeolo.app/share/" + TOKEN))
                .andExpect(jsonPath("$.data.shareToken").value(TOKEN))
                .andExpect(jsonPath("$.data.expiresAt").exists());
    }

    @Test
    void 미인증_공유_링크_생성은_401로_응답한다() throws Exception {
        mockMvc.perform(post("/api/courses/{courseId}/share-links", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));

        verify(shareLinkService, never()).createShareLink(any(), any());
    }

    @Test
    void 타인_코스의_공유_링크_생성은_403과_명세_메시지로_응답한다() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(jwtTokenProvider.parseAccessTokenUserId("valid-token")).thenReturn(userId);
        when(shareLinkService.createShareLink(eq(userId), eq(courseId)))
                .thenThrow(new BusinessException(ErrorCode.COURSE_SHARE_ACCESS_DENIED));

        mockMvc.perform(post("/api/courses/{courseId}/share-links", courseId)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("해당 여행 코스를 공유할 권한이 없습니다."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    // ===== API-SHARE-2 조회(미리보기) =====

    /** DOM-6 §비로그인 사용자 처리 — 링크를 연 사용자는 로그인 전에 미리보기를 볼 수 있어야 한다. */
    @Test
    void 미리보기는_토큰만으로_인증_없이_조회된다() throws Exception {
        when(shareLinkService.getPreview(TOKEN)).thenReturn(new SharePreviewResponse(
                new SharePreviewResponse.SharedCourse("오사카 4일", "일본", "오사카",
                        LocalDate.of(2026, 9, 1), 4),
                new SharePreviewResponse.Inviter("승우", "https://cdn/profile.png"),
                Instant.parse("2026-08-17T00:00:00Z")));

        mockMvc.perform(get("/api/share-links/{shareToken}", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("여행 코스 공유 링크 조회 성공"))
                .andExpect(jsonPath("$.data.course.title").value("오사카 4일"))
                .andExpect(jsonPath("$.data.course.destinationCountry").value("일본"))
                .andExpect(jsonPath("$.data.course.destinationCity").value("오사카"))
                .andExpect(jsonPath("$.data.course.startDate").value("2026-09-01"))
                .andExpect(jsonPath("$.data.course.totalDays").value(4))
                .andExpect(jsonPath("$.data.inviter.displayName").value("승우"))
                .andExpect(jsonPath("$.data.inviter.profileImageUrl").value("https://cdn/profile.png"))
                .andExpect(jsonPath("$.data.course.courseId").doesNotExist());
    }

    @Test
    void 유효하지_않은_링크_조회는_404와_명세_메시지로_응답한다() throws Exception {
        when(shareLinkService.getPreview(TOKEN))
                .thenThrow(new BusinessException(ErrorCode.INVALID_SHARE_LINK));

        mockMvc.perform(get("/api/share-links/{shareToken}", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("유효하지 않은 공유 링크입니다."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void 만료된_링크_조회는_410과_명세_메시지로_응답한다() throws Exception {
        when(shareLinkService.getPreview(TOKEN))
                .thenThrow(new BusinessException(ErrorCode.SHARE_LINK_GONE));

        mockMvc.perform(get("/api/share-links/{shareToken}", TOKEN))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.message").value("만료되었거나 회수된 공유 링크입니다."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    // ===== API-SHARE-3 수락 =====

    @Test
    void 수락_성공시_courseId와_명세_봉투로_응답한다() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(jwtTokenProvider.parseAccessTokenUserId("valid-token")).thenReturn(userId);
        when(shareLinkService.accept(userId, TOKEN)).thenReturn(ShareAcceptResponse.of(courseId));

        mockMvc.perform(post("/api/share-links/{shareToken}/accept", TOKEN)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("여행 코스 공유 수락 성공"))
                .andExpect(jsonPath("$.data.courseId").value(courseId.toString()));
    }

    /**
     * 미리보기만 공개고 <b>수락은 인증 필요</b>다. 이게 뒤집히면 익명 요청이 컨트롤러까지 들어와
     * {@code userId}가 {@code null}인 채로 서비스를 호출한다 — 401이어야 할 자리에서 500이 난다.
     */
    @Test
    void 미인증_수락은_401이고_서비스까지_가지_않는다() throws Exception {
        mockMvc.perform(post("/api/share-links/{shareToken}/accept", TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));

        verify(shareLinkService, never()).accept(any(), any());
    }

    @Test
    void 이미_수락했거나_자기_코스면_400과_명세_메시지로_응답한다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.parseAccessTokenUserId("valid-token")).thenReturn(userId);
        when(shareLinkService.accept(eq(userId), eq(TOKEN)))
                .thenThrow(new BusinessException(ErrorCode.SHARE_LINK_NOT_ACCEPTABLE));

        mockMvc.perform(post("/api/share-links/{shareToken}/accept", TOKEN)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("수락할 수 없는 공유 링크입니다."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }
}
