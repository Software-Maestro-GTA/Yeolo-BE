package com.soma.yeolo.share.service.port;

import com.soma.yeolo.share.domain.CourseAccess;
import java.util.List;
import java.util.UUID;

/**
 * 코스 접근 권한 영속 포트 (DOM-6 §권한 정책). 공유 수락으로 부여된 "내 목록에 추가된 남의 코스"를
 * 다룬다. 어댑터는 {@code CourseAccessRepositoryImpl}.
 */
public interface CourseAccessRepository {

    /**
     * 접근 권한을 저장한다. 이미 같은 (코스, 사용자) 권한이 있으면 저장하지 않는다.
     *
     * <p>호출자의 사전 검사와 별개로 <b>중복을 여기서도 판정</b>하는 이유는 경합 때문이다 —
     * 수락 버튼 연타로 두 트랜잭션이 동시에 "없음"을 확인하면 둘 다 INSERT를 시도하고, 진 쪽은
     * 유니크 제약에 걸린다. 그 제약 위반이 서비스 밖으로 새면 명세가 400으로 규정한 "이미 수락한
     * 링크"(API-SHARE-3)가 500이 된다. 어댑터가 위반을 잡아 이 반환값으로 번역한다.
     *
     * @return 실제로 저장했으면 {@code true}, 이미 있어 저장하지 않았으면 {@code false}
     */
    boolean saveIfAbsent(CourseAccess access);

    /** 이미 부여된 권한인지. 중복 수락(400) 판정에 쓴다. */
    boolean exists(UUID courseId, UUID userId);

    /** 사용자가 공유받은 코스 식별자 목록. 없으면 빈 목록. */
    List<UUID> findCourseIdsByUserId(UUID userId);

    /**
     * 사용자의 코스 접근 권한을 제거한다(내 목록에서만 제거 — 원본은 남는다).
     *
     * @return 실제로 제거된 권한이 있었으면 {@code true}
     */
    boolean delete(UUID courseId, UUID userId);

    /** 코스에 걸린 모든 접근 권한을 제거한다. 원본 코스 삭제 시 정리용. */
    void deleteAllByCourseId(UUID courseId);
}
