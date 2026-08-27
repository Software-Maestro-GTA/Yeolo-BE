-- TSK-40 (#49) 공유 수락으로 부여된 코스 접근 권한 — prod 배포 전 선적용 DDL
--
-- dev(ddl-auto=update)는 배포 시 Hibernate가 이 테이블을 자동 생성하므로 수동 적용이 필요 없다.
-- prod(ddl-auto=validate)는 자동 생성하지 않으므로, 이 파일을 배포 전에 직접 적용해야
-- 파드가 기동한다. (docs/architecture.md §2)
-- 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
--
-- DOM-6에는 테이블 스펙이 없어(이슈 #49 "확인 필요") BE에서 정한 스키마다.
--
-- 코스 소유권(courses.user_id)과 별개로 "내 목록에 추가된 남의 코스"를 나타낸다. 행을 지우는 것은
-- 원본 삭제가 아니라 접근 권한 제거다(DOM-6 §보안 및 운영 정책).
-- (course_id, user_id) UNIQUE 가 "같은 링크를 여러 번 수락해도 중복 추가되지 않는다"의
-- 최종 방어선이다 — 서비스의 사전 검사가 동시 요청에서 뚫려도 DB가 막는다.
-- share_link_id: 어떤 초대로 들어왔는지 추적용. 링크 행이 지워져도 권한은 남아야 하므로 FK를 걸지 않는다.
CREATE TABLE IF NOT EXISTS course_accesses (
    id            UUID                        NOT NULL,
    course_id     UUID                        NOT NULL,
    user_id       UUID                        NOT NULL,
    share_link_id UUID,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_accesses_course_id_user_id UNIQUE (course_id, user_id)
);

-- 코스 목록 조회(API-COURSE-3)가 사용자별로 공유받은 코스를 훑는다.
CREATE INDEX IF NOT EXISTS idx_course_accesses_user_id ON course_accesses (user_id);
