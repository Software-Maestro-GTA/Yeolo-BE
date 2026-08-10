package com.soma.yeolo.share.dto;

import java.time.Instant;

/**
 * 여행 코스 공유 링크 생성 응답의 {@code data} 페이로드 (API-SHARE-1). 필드명은 명세 그대로다.
 *
 * @param shareUrl   친구에게 전달할 링크 (OS 공유 시트에 그대로 싣는다)
 * @param shareToken 초대 식별자. 링크에 담긴 값과 같으며, 앱이 딥링크에서 뽑아 쓸 수 있게 함께 준다
 * @param expiresAt  만료 시각. 무기한 설정이면 {@code null}
 */
public record ShareLinkCreateResponse(
        String shareUrl,
        String shareToken,
        Instant expiresAt
) {
}
