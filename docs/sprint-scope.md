# 스프린트 스코프 — BE (peter001019)

> 이 문서는 **이번 스프린트에 구현할 범위**를 고정합니다. 여기에 없는 기능은 요청받기
> 전까지 구현하지 않습니다. 출처: GitHub Projects
> (Software-Maestro-GTA/projects/2) 中 `peter001019` 담당 BE 이슈.

## In Scope — 구현 대상

각 작업의 상세는 GitHub 이슈 본문과 `docs/spec-index.md`의 명세 링크를 참조하세요.

| 이슈 | TSK | 작업 | API | FUN | REQ | DOM | 상태 |
| :--- | :-- | :--- | :-- | :-- | :-- | :-- | :--- |
| [#7](https://github.com/Software-Maestro-GTA/Yeolo-BE/issues/7) | — | 환경 설정 및 하네스 구축 | — | — | — | — | In progress |
| [#3](https://github.com/Software-Maestro-GTA/Yeolo-BE/issues/3) | TSK-32 | Google OAuth 로그인 및 사용자 생성/조회 | API-FB-1 | FUN-1 | REQ-11 | DOM-3 | Backlog |
| [#5](https://github.com/Software-Maestro-GTA/Yeolo-BE/issues/5) | TSK-16 | 성향 분석 결과 저장/조회 | API-FB-8 | FUN-4 | REQ-11 | DOM-1, DOM-3 | Backlog |
| [#6](https://github.com/Software-Maestro-GTA/Yeolo-BE/issues/6) | TSK-23 | 지역/날짜/예산 조건 코스 생성 요청 (SSE) | API-FB-4 | FUN-6 | REQ-7 | DOM-2 | Backlog |
| [#4](https://github.com/Software-Maestro-GTA/Yeolo-BE/issues/4) | TSK-7 | 성향 프로필 기반 AI 코스 생성 연동 (SSE) | API-BA-1 | FUN-2 | REQ-7 | DOM-2 | Backlog |
| [#2](https://github.com/Software-Maestro-GTA/Yeolo-BE/issues/2) | TSK-34 | 이미지 메타데이터 성향 분석 + Reverse Geocode 전처리 | API-FB-2, API-BA-6 | FUN-1 | REQ-11 | DOM-3, DOM-5 | Backlog |
| [#1](https://github.com/Software-Maestro-GTA/Yeolo-BE/issues/1) | TSK-36 | 이전 생성 코스 목록/상세 조회 | API-FB-10, API-FB-7 | FUN-7 | — | DOM-2 | Backlog |
| [#51](https://github.com/Software-Maestro-GTA/Yeolo-BE/issues/51) | TSK-25 | 사용자 프로필·MBTI 선호 입력값 저장 | API-PREF-1, API-USER-1 | FUN-2, FUN-8 | REQ-2, REQ-9 | DOM-1 | In progress |
| [#46](https://github.com/Software-Maestro-GTA/Yeolo-BE/issues/46) | TSK-32 | 국가·도시 자동완성 | API-LOC-1, API-LOC-2 | FUN-5, FUN-8 | REQ-4, REQ-9 | — | In progress |
| [#78](https://github.com/Software-Maestro-GTA/Yeolo-BE/issues/78) | — | 회원탈퇴·코스 삭제 복구 + 토큰 재발급 | API-USER-2, API-AUTH-3, API-COURSE-4 | FUN-1, FUN-9 | REQ-11 | DOM-1, DOM-3 | In progress |
| [#49](https://github.com/Software-Maestro-GTA/Yeolo-BE/issues/49) | TSK-40 | 친구 초대 공유 링크 생성·조회·수락 | API-SHARE-1, API-SHARE-2, API-SHARE-3 | FUN-7 | REQ-6 | DOM-6 | In progress |

> ⚠️ **명세 ID 재편:** SPEC 저장소가 갱신되며 API·DOM ID 체계가 바뀌었다
> (`API-FB-*` → `API-AUTH-*`/`API-COURSE-*`/`API-PREF-*`/`API-USER-*`, DOM 번호도 이동 —
> 예전 DOM-3(사용자)는 현재 **DOM-1**, 예전 DOM-2(코스)는 현재 **DOM-3**). 위 표의 #1~#7 행은
> 옛 ID이고, #51 행부터가 현행 ID다. 작업 전 `specs/api-specs/api.md`·`specs/domain-specs/domain.md`
> 인덱스를 먼저 확인할 것. (`docs/spec-index.md`도 옛 ID 기준이라 갱신이 필요하다.)

### 스코프 상의 핵심 포인트
- **인증:** Google OAuth 인가코드 → Google 토큰 교환 → 사용자 upsert → JWT(Access/Refresh) 발급. (#3)
- **성향 분석:** 이미지 EXIF(좌표·시간) 메타데이터만 수집 → Reverse Geocode로 한글 주소화 →
  AI 서버(API-BA-6)로 분석 요청. 개인정보 동의 사전 검증 필수. (#2)
- **코스 생성:** `POST /api/courses`(SSE)로 조건 입력 → 성향 프로필 로딩 → AI 서버(API-BA-1, SSE)
  연동 → 진행 이벤트(`LOADING_TASTE_PREFERENCE` → `GENERATING_COURSE` → `complete`) 스트리밍 →
  결과를 코스 정보(DOM-2)로 저장. (#6, #4)
- **조회:** 코스 목록(최신순·페이지네이션) 및 상세, 소유자 권한 검증. 성향 프로필 조회. (#1, #5)
- **지역 선택:** 코스 생성 화면의 국가·도시 자동완성(한글 초성 검색 포함). 명세에 국가·도시 도메인
  정의가 없어 기준 데이터 출처를 BE에서 정했다(CLDR + GeoNames) — `docs/location-dataset.md`. (#46)
- **세션 수명주기:** 회원탈퇴(API-USER-2)는 소프트 삭제 + 개인정보 파기 + 세션 무효화를 한
  트랜잭션으로 처리한다. 토큰 재발급(API-AUTH-3)은 Access/Refresh를 함께 회전시켜 쓴 토큰을 즉시
  무효화한다. (#78)
- **탈퇴 파기 범위(명세에 규정이 없어 BE에서 정함):** 명세는 요청·응답만 정의하고 무엇을 지울지
  말하지 않는다. 아래를 파기 대상으로 확정했다 — 계정 식별정보(이메일·이름·프로필 이미지 URL·
  OAuth 식별자 → `deleted:<id>`), **저장소의 프로필 이미지 파일 전부**(사용자 프리픽스 단위라
  교체된 옛 이미지까지), **취향 프로필**(사진 EXIF에서 파생된 이동 이력), Refresh Token.
  `users` 행 자체와 **코스·MBTI·사진분석 동의 이력은 남긴다**(코스가 사용자 식별자를 참조한다).
  이미지 파기에 실패하면 예외를 전파해 탈퇴 전체를 롤백한다 — 사진이 남았는데 "탈퇴 성공"으로
  응답하지 않기 위함이며, 대가로 저장소 장애 중에는 탈퇴가 500이 된다(재시도 가능·멱등).
  탈퇴자의 남은 Access Token은 인증 필터가 계정 상태를 확인해 401로 막는다. (#78)
- **롤백 복구:** 회원탈퇴(#42/#44)와 코스 삭제(#52)는 한 번 구현됐다가 롤백 커밋 `024afc8`
  ("main을 스프린트1 + Apple OAuth 시제품")에 휩쓸려 사라진 것을 #78에서 되살린 것이다. 같은
  롤백의 다른 피해(장소 정규화, `UserPreference`)는 이후 `place`·`preference` 패키지로 재구축되어
  복구가 끝났다 — **이로써 `024afc8` 유실분은 모두 회수됐다.** 코스 삭제를 되살리며 목록 조회
  메시지도 명세(API-COURSE-3 "여행 코스 목록 조회 성공")에 맞춰 정정했다. (#78)

- **친구 초대 공유:** 코스 소유자가 링크를 발급(API-SHARE-1)하고, 초대받은 사용자가 **비로그인
  상태로 미리보기**(API-SHARE-2)한 뒤 로그인해 수락(API-SHARE-3)하면 코스가 목록에 추가된다.
  초대 식별자는 실제 `courseId`를 노출하지 않는 랜덤 토큰이며 **해시로만 저장**한다(Refresh Token과
  같은 정책) — 그래서 발급 API는 호출할 때마다 새 토큰을 내고, 앞서 발급된 링크도 만료 전까지
  함께 유효하다. (#49)
- **공유 정책(명세에 규정이 없어 BE에서 정함):** 명세는 요청·응답만 정의하고 테이블 스펙·만료
  정책을 말하지 않는다(이슈 #49 "확인 필요"). 아래를 확정했다 —
  **① 테이블**은 `course_share_links`(링크)와 `course_accesses`(접근 권한) 둘로 나눈다
  (`docs/ddl/`). 접근 권한에 `(course_id, user_id)` 유니크를 걸어 중복 수락을 DB에서 막는다.
  **② 만료**는 기본 7일(`share-link.ttl`, 0이면 무기한). 만료·회수된 링크는 410이다.
  **③ 공유받은 사용자의 삭제**는 원본 삭제가 아니라 자기 목록에서만 제거한다(DOM-6 그대로).
  즉 `DELETE /api/courses/{id}`는 소유자면 원본 삭제, 공유받은 사용자면 접근 권한 제거, 그 외 403이다.
  **④ 소유자가 원본을 삭제**하면 접근 권한을 지우고 링크를 회수한다. 회수된 링크를 열면
  "코스 없음"(404)으로 안내한다 — 링크 상태(410)보다 코스 존재를 먼저 보기 때문이며, DOM-6이
  "공유된 여행 코스를 찾을 수 없습니다"를 별도 안내로 규정하기 때문이다.
  **⑤ 목록에서 소유 코스와 공유 코스를 구분하지 않는다**(DOM-6이 이번 스프린트엔 불필요하다고
  명시, API-COURSE-3에 구분 필드 없음). 정렬은 원본 코스 생성 시각 기준 최신순이다. (#49)
- **미해소 명세 모순(#49):** 이미 수락한 링크·자기 코스 수락은 API-SHARE-3의 400을 따랐다.
  DOM-6이 권하는 "코스 상세로 이동" UX와 어긋나며 Notion 원본 확인이 필요하다 —
  경위는 `docs/spec-index.md`.

## Out of Scope — 이번 스프린트에서 손대지 않음

아래는 명세엔 있으나 이번 스프린트 대상이 **아닙니다**. 필요하면 먼저 확인 후 진행.

- **API:** API-FB-3(최소 설문 성향분석), API-FB-11(로그아웃), API-FB-12(회원탈퇴)
- **기능/요구사항:** 그룹 톡방·일정조율(REQ-1), 그룹 교차분석(REQ-2), AI 앨범 정리(REQ-3),
  예약/제휴 링크(REQ-4), 마이페이지 통합(REQ-5), 추천 서버 운영 안정성(REQ-6),
  동행자 커뮤니티(REQ-10), 공동 앨범(REQ-12), 추천 일정 상세 UI(REQ-9, FE 영역)
- **타 파트:** FE 구현, AI 엔진 내부 로직, 인프라/배포

## 담당 외
- FE·AI·인프라는 다른 담당자 몫입니다. BE는 AI 내부 API를 **호출**만 합니다.
