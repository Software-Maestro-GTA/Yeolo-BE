-- 코스 대표 이미지 (API-COURSE-2/3, API-AI-2 명세 개정) — prod 배포 전 선적용 DDL
--
-- dev(ddl-auto=update)는 배포 시 Hibernate가 이 컬럼을 자동 추가하므로 수동 적용이 필요 없다.
-- prod(ddl-auto=validate)는 자동 추가하지 않으므로, 이 파일을 배포 전에 직접 적용해야
-- 파드가 기동한다. (docs/architecture.md §2)
-- 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
--
-- courses 테이블 자체는 이 규칙(docs/ddl)이 생기기 전에 만들어져 CREATE 문이 없다. 이 파일은
-- 그 이후의 변경분만 누적한다.
--
-- NULL 허용이다. 대표 이미지는 AI가 코스와 함께 주는 값이고(API-AI-2 complete 이벤트의
-- coverImageUrl), 못 고를 수도 있다 — 없다고 코스 생성을 실패시키지 않는다. 명세 개정 전에
-- 저장된 코스도 전부 NULL로 남는다(소급해 채울 출처가 없다).
ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS cover_image_url TEXT;

-- 사용자별 코스 조회 전부(목록 API-COURSE-3, 로그인 recentCourseId, existsByUserId)가
-- user_id 필터 + created_at DESC 정렬인데 인덱스가 없었다. 행이 itinerary JSON으로 넓어
-- 코스가 쌓일수록 seq scan 비용이 선형으로 는다. dev는 엔티티 @Index 추가로 ddl-auto가
-- 만들어 주고, prod는 이 문장이 만든다.
CREATE INDEX IF NOT EXISTS idx_courses_user_id_created_at
    ON courses (user_id, created_at DESC);
