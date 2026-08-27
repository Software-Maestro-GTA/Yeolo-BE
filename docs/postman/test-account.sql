-- dev DB 조회·재시드용 계정 행.
--
-- ⚠️ **이 행으로는 로그인할 수 없다.** 원래는 폐기된 `tokenMode=mint`(jwtSecret 으로 토큰을 직접
--    서명)와 짝이었는데, 그 방식을 버리면서(docs/postman/README.md §3) 쓸모가 그만큼 줄었다.
--    토큰은 실제 Google 로그인으로만 나오고 이 행의 provider_user_id 는 어떤 Google sub 와도
--    대응하지 않으므로, OAuth 흐름이 이 계정의 토큰을 만들어 낼 방법이 없다.
--    남은 용도는 둘이다 — DB 초기화 후 "사용자 행이 있는 상태"를 만들거나, 조인·조회 쿼리를
--    확인할 때 고정 UUID 대상이 필요할 때.
--
-- 사용자는 OAuth 로그인으로만 생성되므로(가입 API 없음) 계정 행을 직접 넣는다.
-- provider_user_id 를 실제 OAuth 계정과 겹치지 않는 값으로 둬서
-- 나중에 같은 사람이 진짜 Google 로그인을 해도 이 행과 충돌하지 않는다.
--
-- 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
-- ⚠️ dev 전용이다. prod 에는 넣지 않는다.
--
-- 📌 dev(yeolo-dev-postgres)에는 2026-08-07 에 이미 적용했다.
--    userId = 00000000-0000-4000-8000-000000000001
--    재실행해도 안전하다(ON CONFLICT). prod 이관이나 DB 초기화 후 재적용용으로 남겨둔다.
--
-- bastion 에서 실행하는 예:
--   SECRET=$(aws secretsmanager get-secret-value --secret-id yeolo/dev/app-db \
--              --query SecretString --output text --region ap-northeast-2)
--   export PGHOST=$(jq -r .host <<<"$SECRET")     PGPORT=$(jq -r .port <<<"$SECRET")
--   export PGDATABASE=$(jq -r .dbname <<<"$SECRET") PGUSER=$(jq -r .username <<<"$SECRET")
--   export PGPASSWORD=$(jq -r .password <<<"$SECRET") PGCLIENTENCODING=UTF8
--   psql -v ON_ERROR_STOP=1 -f test-account.sql

-- ===== 1. 계정 생성 =====
-- id 를 고정 UUID 로 박아 Postman 환경변수(userId)에 그대로 쓸 수 있게 한다.
-- 이미 있으면 갱신만 하고 실제 id 를 돌려준다(고정 UUID 와 다를 수 있으니 반환값을 쓸 것).
INSERT INTO users (id, provider, provider_user_id, email, display_name, profile_image_url,
                   status, last_login_at, created_at, updated_at)
VALUES ('00000000-0000-4000-8000-000000000001',
        'google',
        'postman-test-account',
        'postman-test@yeolo.invalid',
        'Postman 테스트',
        NULL,
        'active',
        now(),
        now(),
        now())
ON CONFLICT (provider, provider_user_id)
    DO UPDATE SET last_login_at = now(),
                  updated_at    = now()
RETURNING id;

-- ===== 2. 확인 =====
-- 위 RETURNING 값을 Postman 환경의 userId 에 넣는다.
SELECT id, provider, email, display_name, status, deleted_at
FROM users
WHERE provider = 'google'
  AND provider_user_id = 'postman-test-account';

-- ===== 3. (선택) 정리 =====
-- 코스·성향 프로필·동의 이력 등 이 계정이 만든 데이터가 FK 없이 user_id 로만 묶여 있으므로,
-- 계정만 지우면 고아 데이터가 남는다. 계정을 정리할 때는 파생 데이터부터 지운다.
--
-- 계정 id 가 고정 UUID 라 재삽입 시 같은 값으로 돌아온다. 아래를 빠뜨리면 남은 행이 새로 만든
-- 계정에 그대로 다시 붙는다(특히 공유 테이블 둘은 FK 가 없어 아무 오류도 나지 않는다).
-- DELETE FROM course_accesses         WHERE user_id    = '<userId>';
-- DELETE FROM course_share_links      WHERE inviter_id = '<userId>';
-- DELETE FROM photo_analysis_consents WHERE user_id = '<userId>';
-- DELETE FROM user_preferences        WHERE user_id = '<userId>';
-- DELETE FROM taste_profiles          WHERE user_id = '<userId>';
-- DELETE FROM courses                 WHERE user_id = '<userId>';
-- DELETE FROM refresh_tokens          WHERE user_id = '<userId>';
-- DELETE FROM users                   WHERE id      = '<userId>';
--
-- 계정을 남겨두되 비활성화만 하려면:
-- UPDATE users SET status = 'inactive', updated_at = now() WHERE id = '<userId>';

-- ===== MBTI · 사진 분석 동의는 SQL 로 넣지 말 것 =====
-- 계정을 만든 뒤 Postman `03. User / Preference / Consent` 폴더를 실행해 API 로 넣는다.
-- 그래야 그 두 엔드포인트도 함께 검증되고, 동의 이력의 agreed_at 이 서버 시각으로 기록된다.
