-- taste_profiles 변경분 누적 DDL — prod 배포 전 선적용
--
-- dev(ddl-auto=update)는 배포 시 Hibernate가 컬럼을 자동 추가하므로 수동 적용이 필요 없다.
-- prod(ddl-auto=validate)는 자동 추가하지 않으므로, 이 파일을 배포 전에 직접 적용해야 파드가 기동한다.
-- (docs/architecture.md §2) 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
--
-- taste_profiles 테이블 자체는 이 규칙(docs/ddl)이 생기기 전에 만들어져 CREATE 문이 없다. 이 파일은
-- 그 이후의 변경분만 누적한다.

-- [2026-10] 명세 개정(#98) — 취향 요약 원본 JSON (API-AI-1 summary → API-PREF-4 응답 본문).
-- NULL 허용: 개정 전에 저장된 프로필과 요약을 주지 않은(개정 전) AI 응답은 값이 없고, 그 경우 취향
-- 조회(API-PREF-4)는 404 로 응답해 FE 가 재분석을 유도한다.
ALTER TABLE taste_profiles
    ADD COLUMN IF NOT EXISTS summary TEXT;
