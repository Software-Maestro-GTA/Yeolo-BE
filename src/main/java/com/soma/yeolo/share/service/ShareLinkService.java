package com.soma.yeolo.share.service;

import com.soma.yeolo.course.domain.SavedCourse;
import com.soma.yeolo.course.service.port.CourseRepository;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.share.domain.CourseAccess;
import com.soma.yeolo.share.domain.SavedShareLink;
import com.soma.yeolo.share.domain.ShareLink;
import com.soma.yeolo.share.domain.ShareToken;
import com.soma.yeolo.share.dto.ShareAcceptResponse;
import com.soma.yeolo.share.dto.ShareLinkCreateResponse;
import com.soma.yeolo.share.dto.SharePreviewResponse;
import com.soma.yeolo.share.service.port.CourseAccessRepository;
import com.soma.yeolo.share.service.port.ShareLinkRepository;
import com.soma.yeolo.user.domain.UserDisplayProfile;
import com.soma.yeolo.user.service.UserDisplayProfileReader;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행 코스 친구 초대 공유 링크 (API-SHARE-1 생성 / API-SHARE-2 조회 / API-SHARE-3 수락, DOM-6).
 *
 * <p>세 API가 공통으로 지키는 규칙이 둘 있다.
 * <ul>
 *   <li><b>링크 → 코스 → 링크 상태</b> 순으로 검증한다. 없는 토큰은 404, 원본 코스가 지워졌으면
 *       404(코스 없음), 만료·회수된 링크는 410이다. 코스 존재를 만료보다 먼저 보는 이유는
 *       DOM-6이 "공유된 여행 코스를 찾을 수 없습니다"를 별도 안내로 규정하기 때문이다 —
 *       코스를 지우면 링크도 회수되므로, 순서를 뒤집으면 이 안내가 영영 나오지 않는다.</li>
 *   <li><b>courseId는 수락 전까지 노출하지 않는다</b> (DOM-6 §보안 및 운영 정책). 미리보기 응답에는
 *       코스 요약과 초대자만 담는다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareLinkService {

    private final ShareLinkRepository shareLinkRepository;
    private final CourseAccessRepository courseAccessRepository;
    private final CourseRepository courseRepository;
    private final UserDisplayProfileReader userDisplayProfileReader;
    private final ShareLinkProperties properties;

    /**
     * 코스 소유자에게 공유 링크를 발급한다 (API-SHARE-1). 코스가 없으면 404, 소유자가 아니면 403.
     *
     * <p>호출할 때마다 <b>새 토큰</b>을 발급한다. 초대 식별자를 해시로만 저장하기 때문에 기존 링크의
     * 평문을 되돌려줄 방법이 없어서다({@link ShareToken}). 앞서 발급된 링크도 만료 전까지 함께
     * 유효하며, 원본 코스를 삭제하면 모두 회수된다.
     */
    @Transactional
    public ShareLinkCreateResponse createShareLink(UUID userId, UUID courseId) {
        SavedCourse course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (!course.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.COURSE_SHARE_ACCESS_DENIED);
        }

        ShareToken token = ShareToken.issue();
        Instant expiresAt = properties.expiresAtFrom(Instant.now());
        shareLinkRepository.save(new ShareLink(courseId, userId, token.hash(), expiresAt));

        return new ShareLinkCreateResponse(properties.shareUrl(token.value()), token.value(), expiresAt);
    }

    /**
     * 공유 링크 미리보기 (API-SHARE-2). 인증 없이 호출할 수 있다 — 초대받은 사용자가 로그인 전에도
     * 어떤 코스를 받았는지 확인할 수 있어야 하기 때문이다 (DOM-6 §비로그인 사용자 처리).
     */
    @Transactional(readOnly = true)
    public SharePreviewResponse getPreview(String shareToken) {
        SharedInvitation invitation = resolve(shareToken);
        UserDisplayProfile inviter = userDisplayProfileReader
                .findDisplayProfile(invitation.link().inviterId())
                .orElse(null);
        return SharePreviewResponse.from(invitation.course(), inviter, invitation.link());
    }

    /**
     * 공유 링크를 수락해 코스를 내 목록에 추가한다 (API-SHARE-3).
     *
     * <p>이미 수락했거나 자기 자신의 코스면 400이다. 명세가 두 경우를 400으로 규정하고 있어
     * 그대로 따랐다 — DOM-6은 그 상황에서 "기존 코스 상세로 이동"을 권하지만, 그러려면 응답에
     * courseId가 실려야 해 명세의 Error Code와 어긋난다. (docs/spec-index.md에 기록)
     */
    @Transactional
    public ShareAcceptResponse accept(UUID userId, String shareToken) {
        SharedInvitation invitation = resolve(shareToken);
        SavedShareLink link = invitation.link();
        SavedCourse course = invitation.course();

        if (course.isOwnedBy(userId)) {
            log.warn("자기 자신의 코스를 수락하려 했다: courseId={}", course.courseId());
            throw new BusinessException(ErrorCode.SHARE_LINK_NOT_ACCEPTABLE);
        }
        // 사전 검사와 저장 시 판정을 모두 둔다 — 앞의 것은 흔한 경우를 값싸게 걸러 내고, 뒤의 것은
        // 수락 버튼 연타로 두 요청이 동시에 "없음"을 본 경합에서 유니크 제약 위반이 500으로 새는
        // 것을 막는다. 어느 쪽이든 명세(API-SHARE-3)가 정한 400으로 응답한다.
        if (courseAccessRepository.exists(course.courseId(), userId)
                || !courseAccessRepository.saveIfAbsent(
                        new CourseAccess(course.courseId(), userId, link.id()))) {
            log.warn("이미 수락한 공유 링크다: courseId={}", course.courseId());
            throw new BusinessException(ErrorCode.SHARE_LINK_NOT_ACCEPTABLE);
        }
        return ShareAcceptResponse.of(course.courseId());
    }

    /**
     * 토큰을 링크·코스로 풀어내며 조회(API-SHARE-2)와 수락(API-SHARE-3)이 공유하는 검증을 수행한다.
     *
     * <p>순서는 <b>링크 존재 → 코스 존재 → 링크 상태</b>다. 코스를 지우면 그 코스의 링크는 함께
     * 회수되므로(={@code revokedAt} 설정), 상태를 먼저 보면 삭제된 코스가 모두 410으로 뭉뚱그려져
     * "공유된 여행 코스를 찾을 수 없습니다"(DOM-6) 안내를 낼 수 없다.
     *
     * @throws BusinessException 없는 토큰이면 404, 코스가 없으면 404(코스 없음), 만료·회수면 410
     */
    private SharedInvitation resolve(String shareToken) {
        SavedShareLink link = shareLinkRepository.findByTokenHash(ShareToken.of(shareToken).hash())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SHARE_LINK));
        SavedCourse course = courseRepository.findById(link.courseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (!link.isUsable(Instant.now())) {
            throw new BusinessException(ErrorCode.SHARE_LINK_GONE);
        }
        return new SharedInvitation(link, course);
    }

    /** 검증을 통과한 초대 — 링크와 그 링크가 가리키는 원본 코스. */
    private record SharedInvitation(SavedShareLink link, SavedCourse course) {
    }
}
