# 명세 인덱스 (BE 관점)

`specs/`(submodule, `Yeolo-SPEC`)의 문서 중 **이번 스프린트 BE 작업에 필요한 것**을 정리한
빠른 참조표입니다. 코드 작성 전 해당 명세를 반드시 읽으세요.

> ⚠️ `specs/domain-specs/domain.md` 인덱스의 링크는 ID↔파일명이 어긋나 있습니다.
> 아래 표의 **파일 경로가 정확**합니다.

## API 명세 (`specs/api-specs/`)

### FE ↔ BE (구현 대상)
| API ID | 파일 | Method · Endpoint | 이슈 |
| :--- | :--- | :--- | :--- |
> ID는 SPEC 저장소 재편 후의 **현행 ID**다(옛 `API-FB-*`는 폐기). 경로는 구현과 1:1로 일치한다.

| API ID | 파일 | Method · Endpoint | 이슈 |
| :--- | :--- | :--- | :--- |
| API-AUTH-1 | `API-AUTH-1.md` | `POST /api/auth/google` — Google OAuth 로그인 | #3 |
| API-AUTH-2 | `API-AUTH-2.md` | `POST /api/auth/apple` — Apple OAuth 로그인 | #66 |
| API-AUTH-3 | `API-AUTH-3.md` | `POST /api/auth/refresh` — 토큰 재발급 | #78 |
| API-AUTH-4 | `API-AUTH-4.md` | `POST /api/auth/logout` — 로그아웃 | #26 |
| API-COURSE-1 | `API-COURSE-1.md` | `POST /api/courses` — 여행 코스 생성(SSE) | #6, #54 |
| API-COURSE-2 | `API-COURSE-2.md` | `GET /api/courses/{courseId}` — 여행 코스 조회 | #1 |
| API-COURSE-3 | `API-COURSE-3.md` | `GET /api/courses` — 여행 코스 목록 조회 | #1 |
| API-COURSE-4 | `API-COURSE-4.md` | `DELETE /api/courses/{courseId}` — 여행 코스 삭제 | #52, #78 |
| API-LOC-1 | `API-LOC-1.md` | `GET /api/locations/countries` — 국가 전체 목록 조회 | #46, #97 |
| API-LOC-2 | `API-LOC-2.md` | `GET /api/locations/cities` — 국가별 도시 전체 목록 조회 | #46, #97 |
| API-LOC-3 | `API-LOC-3.md` | `GET /api/locations/search` — 국가·도시 키워드 검색 | #97 |
| ~~API-PLACE-1~~ | `~~API-PLACE-1~~.md` | ~~`GET /api/places/{placeId}` — 여행 장소 조회~~ **폐기(2026-10)** | #48, #96 |
| API-PREF-1 | `API-PREF-1.md` | `PATCH /api/users/me/preferences` — 사용자 MBTI 등록/수정 | #51 |
| API-SHARE-1 | `API-SHARE-1.md` | `POST /api/courses/{courseId}/share-links` — 공유 링크 생성 | #49 |
| API-SHARE-2 | `API-SHARE-2.md` | `GET /api/share-links/{shareToken}` — 공유 링크 조회(미리보기) | #49 |
| API-SHARE-3 | `API-SHARE-3.md` | `POST /api/share-links/{shareToken}/accept` — 공유 링크 수락 | #49 |
| API-PREF-2 | `API-PREF-2.md` | `POST /api/users/me/consents/photo` — 사진 데이터 분석 동의 | #50 |
| API-PREF-3 | `API-PREF-3.md` | `POST /api/users/me/taste-profile/analysis` — 취향 분석(SSE) | #2, #55, #78 |
| API-PREF-4 | `API-PREF-4.md` | `GET /api/users/me/taste-profile` — 취향 조회 | #5, #78 |
| API-USER-1 | `API-USER-1.md` | `PATCH /api/users/me/profile` — 사용자 프로필 등록/수정 | #51 |
| API-USER-2 | `API-USER-2.md` | `DELETE /api/users/me` — 회원탈퇴 | #78 |
| API-USER-3 | `API-USER-3.md` | `GET /api/users/me/language` — 내 언어 설정 조회 | #99 |
| API-USER-4 | `API-USER-4.md` | `POST /api/users/me/language` — 내 언어 설정 변경 | #99 |

## 2026-10-14 명세 개정 반영 (pin `c0f1b29` → `3d7b0f9`)

SPEC 커밋 `3d7b0f9`("UI/UX 변경에 따른 API 수정")의 변경과 BE 대응 이슈다. 모든 API 헤더에
`Accept-Language`가 추가됐고(언어 정책은 #99에서 한 번에 처리), 아래는 그 외의 계약 변경이다.
#94(코스 캐싱)는 이 개정으로 코스 계약 자체가 바뀌어 **머지 없이 폐기**했다(#95).

| 명세 | 변경 | BE 대응 |
| :--- | :--- | :--- |
| API-COURSE-1 / API-AI-2 | 요청 `budgetType` → `budget: number` | #96 — `BudgetType` 제거, 옛 값 거부 테스트 |
| API-AI-2 / API-COURSE-2 | `totalCost` 추가. `coverImageUrl`·`recommendationReason`·day `memo`·`arrivalTime`·`transportToNext.cost`·장소 상세(주소·평점·사진·운영시간) 삭제 | #96 — `courses.total_cost` 추가, DTO 재구성, 장소 정규화 축소 |
| API-COURSE-2 | `totalCostExRate`·`exRateIcon` 추가 | #100 — 현지 통화 기호 + 언어 설정 연동 기준 통화로 환산 |
| API-COURSE-3 | `recommendationReason` 삭제. `인증 필요: N`으로 바뀌었으나 내 코스 목록이므로 **오타로 보고 인증 유지** | #96 |
| API-PLACE-1 | 폐기(파일명 취소선) | #96 — 엔드포인트 삭제, `places` 테이블·등록은 정규화용으로 유지 |
| API-LOC-1/2/3 | 자동완성 2개 → 전체 목록 2개 + 접두어 검색 1개. `limit`·초성 검색 없음, LOC-2는 **페이지네이션 없이 전체**. 응답은 `countryName`·`cityName`만 | #97 |
| API-AI-1 / API-PREF-4 | AI complete에 `summary`(intro·hashtags·types·recommendCity) 추가, PREF-4 응답이 `tasteProfile` 래퍼 대신 summary 객체. `recommendCity.imageUrl`은 AI가 주지 않아 **BE가 채운다** | #98 (사진 데이터셋은 #97) |
| API-USER-3/4 | 신설 — 언어 설정 조회·변경, AI-1/2 요청에 `language` 필드 | #99 |
| DOM-6 | BE가 작성한 딥링크 계획(`docs/share-link-deeplink-plan.md`)이 그대로 편입 | 대응 없음 |
| API-SHARE-2 | 미리보기 `course`에 `coverImageUrl` 추가 (SPEC `5a3d421`) | #109 — 목록(API-COURSE-3)과 같은 규칙(저장값 → 도시 사진 → null)을 `CourseCoverImageResolver`로 공용화 |
| DOM-4 / DOM-5 | 취향 분석·코스 생성의 **AI 내부 동작** 기술 개정(LLM 2회 호출, 항공·숙박 견적 합산 등, SPEC `5a3d421`). BE가 따르는 API-AI-1/2·API-PREF-3·API-COURSE-2 계약은 그대로 | 대응 없음 (#109에서 대조) |
| DOM-7 | 서식 정리만 | 대응 없음 |

**명세 모순과 BE 판단:**
- **API-AI-2 §3 vs §4** — §4(2026-10-02)는 `photoAttribution`·`coverImageAttribution`을 BE가 보존해
  FE에 전달하라고 하지만, §3 스키마에는 사진 관련 필드가 전부 없다. 내부 저장은 무손실 JSON이라
  보존되고, **FE 응답은 §3 스키마를 따른다.**
- **DOM-5 vs API-AI-2** — DOM-5(AI 구현 기록, 2026-10-06)는 AI가 아직 `budgetType`을 받는다고 적는다.
  BE가 `budget`을 먼저 보내면 AI 400이 난다. #96 머지 전 Postman `07. Internal AI`로 dev AI의 계약을
  확인한다.
- **API-LOC-1/2/3 JSON vs 본문** — 본문은 `countryId`·`cityId`·`country`·`city`를 언급하지만 JSON
  스키마는 `countryName`·`cityName`만 있다. 기존 관례대로 **JSON 스키마를 계약**으로 본다.
- **`recommendCity.imageUrl` 출처(명세 미규정)** — Pixabay에서 빌드 스크립트로 내려받아 S3에 저장한다.
  Wikidata P18은 84%가 저작자 표시 필수라 출처 필드 없이는 쓸 수 없어 기각했다. 출처 필드는 API에
  두지 않는다(사용자 결정). 상세는 #97.
- **`totalCost`의 통화(명세 미규정)** — AI 는 목적지와 무관하게 **원화**로 준다(AI 팀 확인, 2026-10-14).
  설정 `exchange-rate.source-currency=KRW`로 드러내 두었다.
- **`exRateIcon`·`totalCostExRate`(명세 미규정)** — 원화 `totalCost`를 목적지 **현지 통화**로 환산한 금액이
  `totalCostExRate`, 그 현지 통화의 기호(CLDR 좁은 기호 우선: ¥·$·฿)가 `exRateIcon`이다. FE 가 "₩424,894
  (¥50,000)" 처럼 나란히 보여준다. 국내 코스는 환산값이 원화 금액 그대로. 기호·코드·수도는 #97에서
  `countries.tsv`에 적재했다(`docs/location-dataset.md`). 환율은 fawazahmed0/currency-api(jsDelivr CDN,
  Cloudflare Pages 폴백, 키·출처 표시 불필요, 통화 340개)에서 통화별로 받아 12시간 캐시하고, 갱신 실패 시
  지난 값을 쓰며 실패 후 1분은 재시도하지 않는다(ECB 기반은 VND·TWD 등이 없고, open.er-api 는 출처 링크를
  요구). 환산은 조회 시점에 반올림 정수로 한다. **명세는 `totalCostExRate`를 `number`로만 적지만 BE는
  `number|null`로 내린다** — 제공처 장애·미지원 통화·개정 전 코스(총비용 없음)에 정해진 값이 없고, 0 같은
  가짜 값은 더 해롭다. FE가 null 을 다뤄야 하며 명세에 `number|null` 반영을 요청한다. 언어 설정은 환산에
  관여하지 않는다(처음 계획했던 "언어별 기준 통화"는 AI 가 원화로 준다는 확인으로 철회). #100
- **API-PREF-4의 요약 없는 프로필(명세 미규정)** — 개정 전 AI 응답으로 저장된 프로필에는 `summary`가 없어
  보여줄 본문이 없다. 명세의 404(`저장된 성향 프로필이 없습니다.`)로 응답해 FE가 재분석을 유도하게 한다.
  AI가 `summary`를 빼고 보내도 분석 자체는 실패시키지 않는다(지표만으로 코스 생성은 되므로). #98
- **지원 언어(API-USER-3/4, 명세 "별도 확정")** — 설정값 `language.supported`(기본 `ko`)·`language.fallback`
  (기본 `ko`)로 두고 코드 변경 없이 넓힌다. 요청 `language`는 대소문자·지역 코드를 정리해 받고(`ko-KR`→`ko`),
  미지원이면 400. 지역 코드 매칭(`ko-KR`)은 명세가 "별도 확정"이라 받지 않는다(400). 응답
  `Content-Language`는 BE가 실제로 메시지를 낼 수 있는 언어(`language.message-languages`, 지금은 `ko`)
  중에서만 고른다 — 저장 가능 언어를 넓혀도 번역 리소스가 없으면 헤더가 거짓이 되기 때문. 저장값 단계는
  두 번째 메시지 언어를 열 때 추가한다. AI 요청(API-AI-1/2)에는 저장된 선호 언어를 `language`와
  `Accept-Language`로 싣는다. **깨진 JSON의 400 문구**는 명세 예시(`지원하는 언어 코드를 입력해주세요.`)가
  아니라 공통 문구(`요청이 유효하지 않습니다.`)다 — 본문 파싱 실패는 엔드포인트를 알기 전에 일어나
  전역 핸들러가 처리한다. #99
- **API-COURSE-3 `coverImageUrl`의 출처** — AI 계약(API-AI-2)에서 대표 이미지가 빠졌는데 목록 응답에는
  남아 있다. 저장값이 없으면 목적지 도시 사진(#97 데이터셋)으로 채우고, 그것도 없으면 null이다.
- **API-SHARE-2 `coverImageUrl`의 nullable 여부(명세 미표기)** — 명세는 `string`으로만 적고(같은 문서의
  `inviter` 필드는 `string|null`로 표기), API-COURSE-3도 같다. 사진 없는 목적지에는 돌려줄 값이 없어
  **두 API 모두 null을 낸다**(빈 문자열은 FE가 `<img src="">`로 잘못 그릴 수 있다). 명세 담당에
  `string|null` 표기 요청 필요. #109

**`API-BOOKING-1`(예약 제휴 링크)은 명세에서 삭제됐다.** 제휴처가 아고다 → Trip.com으로 바뀌면서
연동 방식이 **앱 클라이언트에 고정 제휴 URL을 상수로 심는 방식**으로 정해져(DOM-7 개정) 서버 역할이
사라졌다 — BE는 이 API를 구현한 적이 없으므로 지울 코드도 없다. 개정된 DOM-7의 `TRIP_HOTEL_URL`·
`TRIP_FLIGHT_URL`은 FE 몫이다.

#### 미해소 명세 모순 — API-SHARE-3의 400 vs DOM-6의 "코스 상세로 이동" (#49)

`API-SHARE-3`은 **이미 수락한 링크·자기 자신의 코스**를 모두 `400: 수락할 수 없는 공유 링크입니다.`로
규정한다. 반면 `DOM-6`은 같은 두 상황에서 "중복 추가 없이 **기존 코스 상세 화면 또는 목록으로
이동**"하는 것이 자연스럽다고 적는다. 그런데 미리보기(API-SHARE-2)는 `courseId`를 내려주지 않으므로
(DOM-6 §보안 및 운영 정책), 400을 받은 FE에는 이동할 코스 식별자가 없다 — 두 문서를 동시에 만족시킬
수 없다.

**API 명세(400)를 따랐다.** Error Code와 실패 응답 예시가 명시된 쪽이 FE 계약이고, DOM-6의 문장은
화면 흐름에 대한 권고(“~하는 것이 자연스럽다”)이기 때문이다. 중복 추가 금지라는 실질 요건은 400으로도
지켜진다. **Notion 원본 확인이 필요하다** — 만약 이동 UX를 살리기로 하면 수락을 멱등 200
(`data.courseId` 반환)으로 바꾸는 한 줄 변경이며, 그때 이 절을 정리한다.

#### 해소된 명세 모순 — API-LOC-2 인증 (#46, #78)

원래 명세는 자기모순이었다: 기본 정보는 `인증 필요: N`인데 Request Header에 `Authorization`이 있고
Error Codes에 `401: 인증 실패`가 있었다.

**공개(인증 불필요)로 확정**했다. 근거는 — 같은 공개 기준 데이터를 주는 형제 API(API-LOC-1)에는
401이 없어 둘을 같게 다루는 편이 FE에 단순하고, 응답이 사용자별로 달라지지 않으며, 토큰을 붙여
보내도 그대로 200이라 FE가 어느 쪽으로 구현했든 깨지지 않는다. 반대로 인증 필수로 만들면 로그인
전 화면에서 지역을 훑어볼 수 없다. (구현 근거는 `SecurityConfig`의 `/api/locations/**` 주석)

**Notion 원본에도 반영 완료**되어 모순은 해소됐다. `specs/api-specs/API-LOC-2.md`에서도
`Authorization` 헤더와 `401`이 빠져 있어(SPEC 저장소 PR #1) 원본·명세·구현 셋이 일치한다.

> **주의 — pin 선택 시:** `specs/`는 Notion에서 `sync_notion_specs.py`가 **생성**하는 산출물이다.
> 과거 동기화 커밋 `eedd7ba`(2026-08-03)는 **Notion의 LOC-2 수정보다 이전**이라 401이 남아 있었다.
> 따라서 "동기화 커밋 = 최신 Notion"이 항상 성립하지는 않는다. 현재 pin은 `3d7b0f9`(2026-10-14)이며 LOC-2
> 수정이 반영돼 있다.

## 2026-08-13 명세 개정 반영 (pin `7d5d6b4` → `e54d9bb`)

이번 갱신에서 바뀐 것과 BE 대응은 아래와 같다. 상세 근거는 각 코드의 주석에 남겼다.

| 명세 | 변경 | BE 대응 |
| :--- | :--- | :--- |
| API-AI-2 | stop이 `place`·`transportToNext` **객체**로 재구성되고, AI가 장소 정보를 통째로 준다(provider 식별자·주소·평점·사진·운영시간). 코스에 `coverImageUrl` 추가 | `ItineraryPlaceNormalizer`가 provider 재조회 대신 **AI가 준 장소를 등록**하고 `place.placeId`만 내부 UUID로 치환. 정보가 부족하면 예전 장소명 조회로 폴백 |
| API-COURSE-2 | stop 구조 동일 변경 + `coverImageUrl` | `Itinerary` DTO 재구성, `courses.cover_image_url` 컬럼 추가 |
| API-COURSE-3 | `coverImageUrl` 추가 | 목록 요약에 필드 추가 |
| API-AUTH-1/2 | `recentCourseId` 추가 | `RecentCourseReader` 포트로 최근 코스 1건 조회(소유+공유, 목록 최상단과 같은 코스) |
| API-LOC-2 | `country` 선택 쿼리 파라미터 추가 | 국가로 좁힌 질의 2종 추가. `country`는 ISO alpha-2와 국가 한국어명을 모두 받는다(명세 미규정 → BE 결정) |
| API-PLACE-1(현재 폐기) | `placeEngName` 추가, `photoUrls[]` → `photoUrl` | 장소 도메인·엔티티·응답 반영(`docs/ddl/places.sql`) |
| API-USER-1 | 요청에서 `email` 삭제 | 프로필 수정에서 이메일 제거. 이메일은 이제 OAuth 제공자 값만 따른다 |
| API-BOOKING-1 / DOM-7 | 예약 API 삭제, 제휴가 Trip.com **클라이언트 상수** 방식으로 변경 | BE 대응 없음(구현한 적 없음) |

**남은 명세 모순:** API-USER-1의 Error Codes에는 `409: 이미 사용 중인 이메일`이 그대로 있으나,
요청에서 `email`이 빠져 **발생시킬 입력이 없다**. 구현하지 않았고 `ErrorCode`에서도 지웠다.

### BE ↔ AI 내부 API (BE가 호출)
| API ID | 파일 | Method · Endpoint | 이슈 |
| :--- | :--- | :--- | :--- |
| API-AI-1 | `API-AI-1.md` | `POST /internal/ai/taste-profile/analysis` — 취향 분석(SSE) | #2, #55, #78 |
| API-AI-2 | `API-AI-2.md` | `POST /internal/ai/courses` — 여행 코스 생성(SSE) | #4, #54 |

### ID 재편 대응표 (옛 → 현행)
SPEC 저장소 갱신으로 ID 체계가 바뀌었다. 옛 문서·이슈에서 마주칠 ID는 아래로 읽는다.

| 옛 ID | 현행 ID |
| :--- | :--- |
| API-FB-1 | API-AUTH-1 |
| API-FB-2 | API-PREF-3 |
| API-FB-4 | API-COURSE-1 |
| API-FB-7 | API-COURSE-2 |
| API-FB-8 | API-PREF-4 |
| API-FB-10 | API-COURSE-3 |
| API-FB-11 | API-AUTH-4 |
| API-FB-12 | API-USER-2 |
| API-BA-1 | API-AI-2 |
| API-BA-6 | API-AI-1 |

`API-FB-3`(설문 성향분석)은 현행 명세에 대응 문서가 없다 — 스코프 밖.

## 도메인 명세 (`specs/domain-specs/`) — JPA 엔티티의 근거
| DOM ID | 파일 | 도메인 | 쓰이는 작업 |
| :--- | :--- | :--- | :--- |
| DOM-1 | `DOM-1.md` | 성향 정보 (Taste Profile) | #5 |
| DOM-2 | `DOM-2.md` | 코스 정보 (Course Recommendation) | #6, #4, #1 |
| DOM-3 | `DOM-3.md` | 사용자 정보 (User) | #3, #5, #2 |
| DOM-5 | `DOM-5.md` | 이미지 메타데이터 전처리 (Image Metadata Preprocessing) | #2 |
| DOM-6 | `DOM-6.md` | 여행 코스 친구 초대 프로세스 (공유 링크·접근 권한) | #49 |

## 기능 명세 (`specs/functional-specs/`)
| FUN ID | 파일 | 기능 |
| :--- | :--- | :--- |
| FUN-1 | `FUN-1.md` | 동의 기반 행동 데이터 연동 및 여행 성향 분석 |
| FUN-2 | `FUN-2.md` | AI 개인 맞춤형 여행 코스 생성 알고리즘 |
| FUN-4 | `FUN-4.md` | 성향 분석 결과 프로필 저장 및 재사용 |
| FUN-6 | `FUN-6.md` | 여행 조건 입력 및 코스 생성 요청 |
| FUN-7 | `FUN-7.md` | 이전 생성 코스 목록 확인 |

## 요구사항 (`specs/requirement-specs/`)
| REQ ID | 파일 | 요구사항 | 인수 기준 위치 |
| :--- | :--- | :--- | :--- |
| REQ-7 | `REQ-7.md` | AI 개인 맞춤형 여행 코스 생성 | 각 파일 내 Acceptance Criteria |
| REQ-11 | `REQ-11.md` | Zero-Touch 개인 성향 분석 | 〃 |

## 인덱스 원문
- API 전체 목록: `specs/api-specs/api.md`
- 도메인 전체 목록: `specs/domain-specs/domain.md`
- 기능 전체 목록: `specs/functional-specs/functional.md`
- 요구사항 전체 목록: `specs/requirement-specs/requirement.md`
