-- #50 사진 데이터 분석 동의 이력 — prod 배포 전 선적용 DDL
--
-- ★ 예외: 이 파일은 dev 에도 한 번 적용해야 한다 — 옛 이름 인덱스 정리(아래 DROP INDEX 참고).
--   테이블·새 인덱스 생성 자체는 dev(ddl-auto=update)가 해 주지만, 인덱스 이름 교체의
--   옛 인덱스 삭제는 ddl-auto 가 하지 않는다.
-- prod(ddl-auto=validate)는 자동 생성하지 않으므로, 이 파일을 배포 전에 직접 적용해야
-- 파드가 기동한다. (docs/architecture.md §2)
-- 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
--
-- API-PREF-2 / FUN-3 / REQ-8. append-only 이력이다 — 동의·철회가 발생할 때마다 새 행을 쌓고
-- 기존 행은 수정하지 않는다. 법적 증빙 목적상 "언제 어떤 버전의 동의서에 동의/철회했는지"가
-- 모두 남아야 하기 때문이다. 그래서 user_id 에 UNIQUE 를 걸지 않는다.
-- 현재 동의 여부는 user_id 기준 가장 최근 agreed_at 행으로 판정한다.
--
-- 회원탈퇴(API-USER-2) 파기 대상이 아니다 — 동의 이력은 파기하지 않는다(docs/sprint-scope.md).
CREATE TABLE IF NOT EXISTS photo_analysis_consents (
    id              UUID                        NOT NULL,
    user_id         UUID                        NOT NULL,
    agreed          BOOLEAN                     NOT NULL,
    consent_version VARCHAR(255)                NOT NULL,
    agreed_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

-- 분석 요청 전 검증이 "이 사용자의 가장 최근 동의 행"을 매번 조회한다. 그 쿼리는
-- ORDER BY agreed_at DESC, created_at DESC LIMIT 1 이므로(동시 요청이 같은 agreed_at 을
-- 가질 수 있어 created_at 을 보조 정렬로 둔다) 인덱스도 같은 순서여야 정렬 단계가 붙지 않는다.
-- 선두 컬럼만 맞추면 agreed_at 이 같은 행들에 대해 Postgres 가 다시 정렬한다.
--
-- 이름이 옛 2컬럼 인덱스(…_user_id_agreed_at)와 다른 것은 의도다. ddl-auto=update 도
-- CREATE INDEX IF NOT EXISTS 도 이름으로만 대조하므로, 같은 이름을 쓰면 이미 옛 인덱스가
-- 있는 DB(dev)에서 재정의가 조용히 무시된다. 옛 이름 인덱스는 아래에서 지운다 —
-- 새로 만드는 환경(prod)에는 애초에 없으므로 IF EXISTS 로 무해하다. **dev 에도 이 파일을
-- 한 번 적용해야 옛 인덱스가 정리된다** (새 인덱스 생성은 dev 는 ddl-auto 가 해 준다).
DROP INDEX IF EXISTS idx_photo_analysis_consents_user_id_agreed_at;
CREATE INDEX IF NOT EXISTS idx_photo_analysis_consents_latest
    ON photo_analysis_consents (user_id, agreed_at DESC, created_at DESC);
