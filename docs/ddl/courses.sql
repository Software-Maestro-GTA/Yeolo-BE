-- courses 변경분 누적 DDL — prod 배포 전 선적용
--
-- dev(ddl-auto=update)는 배포 시 Hibernate가 컬럼을 자동 추가하므로 수동 적용이 필요 없다.
-- prod(ddl-auto=validate)는 자동 추가하지 않으므로, 이 파일을 배포 전에 직접 적용해야
-- 파드가 기동한다. (docs/architecture.md §2)
-- 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
--
-- courses 테이블 자체는 이 규칙(docs/ddl)이 생기기 전에 만들어져 CREATE 문이 없다. 이 파일은
-- 그 이후의 변경분만 누적한다.

-- [2026-08] 코스 대표 이미지 (API-COURSE-3). NULL 허용 — AI가 못 고를 수도 있고, 명세 개정 전에
-- 저장된 코스도 전부 NULL로 남는다. 2026-10 개정으로 AI 계약(API-AI-2)에서는 빠졌지만 목록
-- 응답에는 남아 있어 컬럼을 유지한다(#97에서 목적지 도시 사진으로 채우는 폴백 예정).
ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS cover_image_url TEXT;

-- [2026-08] 사용자별 코스 조회 전부(목록 API-COURSE-3, 로그인 recentCourseId, existsByUserId)가
-- user_id 필터 + created_at DESC 정렬인데 인덱스가 없었다. 행이 itinerary JSON으로 넓어
-- 코스가 쌓일수록 seq scan 비용이 선형으로 는다. dev는 엔티티 @Index 추가로 ddl-auto가
-- 만들어 주고, prod는 이 문장이 만든다.
CREATE INDEX IF NOT EXISTS idx_courses_user_id_created_at
    ON courses (user_id, created_at DESC);

-- [2026-10] 명세 개정(#96) — 코스 전체 예상 비용 (API-AI-2 → API-COURSE-2 totalCost).
-- NULL 허용: 계획용 예상치라 AI가 못 줄 수 있고, 개정 전 코스는 값이 없다.
ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS total_cost BIGINT;

-- [2026-10] 명세 개정(#96) — recommendationReason이 FE·AI 계약 모두에서 빠져 엔티티 매핑을 제거했다.
-- validate는 매핑되지 않은 컬럼이 남아 있어도 통과하므로 DROP은 필수가 아니며, **배포 전에 실행하면
-- 안 된다** — 롤링 배포 중 아직 옛 이미지로 도는 파드가 이 컬럼을 INSERT 하므로 코스 생성이 전부
-- 실패한다. 새 이미지로 롤아웃이 끝나고 롤백 가능성이 사라진 뒤(배포 후 단계)에만 아래를 풀어서
-- 실행한다. 되돌릴 수 없는 삭제이므로 그때까지는 주석으로 둔다.
-- ALTER TABLE courses
--     DROP COLUMN IF EXISTS recommendation_reason;
