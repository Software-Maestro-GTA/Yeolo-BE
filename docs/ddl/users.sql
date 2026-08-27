-- TSK-25 (#82) 사용자가 직접 고친 프로필 항목 표시 — prod 배포 전 선적용 DDL
--
-- dev(ddl-auto=update)는 배포 시 Hibernate가 이 컬럼들을 자동 추가하므로 수동 적용이 필요 없다.
-- prod(ddl-auto=validate)는 자동 추가하지 않으므로, 이 파일을 배포 전에 직접 적용해야
-- 파드가 기동한다. (docs/architecture.md §2)
-- 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
--
-- users 테이블 자체는 이 규칙(docs/ddl)이 생기기 전에 만들어져 CREATE 문이 없다. 이 파일은
-- 그 이후의 변경분만 누적한다.
--
-- 왜 필요한가: 재로그인은 사용자가 손대지 않은 항목만 OAuth 제공자 값으로 갱신한다.
-- "손댔는지"를 값만 보고는 알 수 없어(제공자와 같은 값으로 고쳤을 수도 있다) 항목별로 표시를 남긴다.
-- 항목별로 나눈 이유는 프로필 수정이 부분 수정(PATCH)이기 때문이다 — 이름만 고친 사용자의
-- 이메일까지 제공자 추종을 끊으면, 제공자가 이메일을 바꿔도 옛 주소가 남는다.
--
-- DEFAULT false 는 필수다. 기존 행이 있는 테이블에 기본값 없는 NOT NULL 컬럼은 추가할 수 없다.
-- 기존 사용자는 전부 false(=안 고침)로 시작하는데, 이 규칙이 없던 시절 그들의 프로필은 이미
-- 로그인마다 제공자 값으로 덮여 있었으므로 제공자 추종을 이어가는 것이 맞다.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_customized         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS display_name_customized  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS profile_image_customized BOOLEAN NOT NULL DEFAULT FALSE;


-- ---------------------------------------------------------------------------
-- 탈퇴 식별자가 회수되지 않은 옛 행 정리 — dev·prod 모두 수동 적용 대상
--
-- 스키마 변경이 아니라 데이터 정정이라 dev(ddl-auto=update)도 자동 적용되지 않는다.
-- 두 환경 모두 이 UPDATE를 직접 실행해야 한다.
--
-- 왜 필요한가: 탈퇴 최초 구현(#42)은 status·deleted_at만 남기고 provider_user_id 치환과
-- 개인정보 파기를 하지 않았다(다음 개정 #44에서 추가). 그 사이(2026-07-29~07-30)에 탈퇴한 계정은
-- 원본 sub를 그대로 들고 있어 재가입 로그인 조회에 그대로 걸린다 — 새 사용자가 아니라 탈퇴 계정이
-- 되살아나, 로그인 응답의 recentCourseId는 옛 코스를 가리키는데 이후 모든 API는 인증 필터의
-- 탈퇴자 차단으로 401이 된다(코스 목록이 비어 보이는 이유). 파기했던 이메일·이름·프로필 이미지도
-- 재로그인의 updateOnLogin으로 복원된다.
--
-- 코드(UserService.upsertOnOAuthLogin)는 이런 행을 만나면 로그인 시점에 스스로 회수하도록
-- 고쳤지만, 그 사용자가 다시 로그인하지 않으면 파기했어야 할 개인정보가 DB에 계속 남는다.
-- 그래서 한 번은 직접 정리한다.
--
-- taste_profiles도 함께 지운다. 그 시절 탈퇴는 Refresh Token만 무효화했고 취향 프로필 파기는
-- #44에서 추가됐는데, 취향 프로필은 사진 EXIF에서 파생된 개인의 이동 이력이다(2026-07-20 도입,
-- 영향 구간보다 앞선다). users만 정리하면 파기가 반쪽이 된다.
-- 반대로 대상이 아닌 것: 프로필 이미지 파일(저장소 업로드는 #78/2026-08 도입이라 그 시절 계정엔
-- 우리 저장소의 파일이 없다. profile_image_url은 제공자 URL이었다), photo_analysis_consents
-- (2026-08-06 도입으로 영향 구간 이후이고, 현재 탈퇴도 동의 이력은 파기 대상으로 보지 않는다).
--
-- 순서가 중요하다. UPDATE가 먼저 돌면 "회수되지 않은 행"이라는 표식이 사라져 대상 id를 다시는
-- 특정할 수 없다. 그래서 taste_profiles 삭제를 앞에 두고, 한 트랜잭션으로 묶는다.
--
-- WHERE 절이 "탈퇴 표시는 있는데 식별자는 회수되지 않은 행"만 정확히 집는다. 정상 처리된 탈퇴 행
-- (deleted:로 시작)과 살아 있는 계정은 건드리지 않으므로 여러 번 실행해도 안전하다.
-- 적용 전 대상 확인:
--   SELECT id, provider, provider_user_id, deleted_at FROM users
--    WHERE deleted_at IS NOT NULL AND provider_user_id NOT LIKE 'deleted:%';
BEGIN;

DELETE FROM taste_profiles
WHERE user_id IN (SELECT id
                  FROM users
                  WHERE deleted_at IS NOT NULL
                    AND provider_user_id NOT LIKE 'deleted:%');

UPDATE users
SET provider_user_id  = 'deleted:' || id,
    email             = NULL,
    display_name      = NULL,
    profile_image_url = NULL
WHERE deleted_at IS NOT NULL
  AND provider_user_id NOT LIKE 'deleted:%';

COMMIT;
