-- TSK-40 (#49) 여행 코스 친구 초대 공유 링크 — prod 배포 전 선적용 DDL
--
-- dev(ddl-auto=update)는 배포 시 Hibernate가 이 테이블을 자동 생성하므로 수동 적용이 필요 없다.
-- prod(ddl-auto=validate)는 자동 생성하지 않으므로, 이 파일을 배포 전에 직접 적용해야
-- 파드가 기동한다. (docs/architecture.md §2)
-- 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
--
-- DOM-6에는 테이블 스펙이 없어(이슈 #49 "확인 필요") BE에서 정한 스키마다.
--
-- token_hash: 초대 식별자(shareToken)의 SHA-256 hex. 평문은 저장하지 않는다 —
--   Refresh Token과 같은 정책이며(docs/architecture.md §3), 그 대가로 발급된 토큰은
--   재조회할 수 없다(공유 링크 생성은 호출할 때마다 새 토큰을 발급한다).
--   UNIQUE 는 조회 인덱스이자 충돌 방어선이다.
-- expires_at: NULL 이면 무기한. 기본값은 발급 시각 + share-link.ttl(기본 P7D).
-- revoked_at: NULL 이면 유효. 원본 코스 삭제 시 채워진다. 조회·수락은 410으로 응답한다.
CREATE TABLE IF NOT EXISTS course_share_links (
    id         UUID                        NOT NULL,
    course_id  UUID                        NOT NULL,
    inviter_id UUID                        NOT NULL,
    token_hash VARCHAR(64)                 NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE,
    revoked_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_share_links_token_hash UNIQUE (token_hash)
);

-- 원본 코스 삭제 시 그 코스의 링크를 한 번에 회수한다.
CREATE INDEX IF NOT EXISTS idx_course_share_links_course_id ON course_share_links (course_id);
