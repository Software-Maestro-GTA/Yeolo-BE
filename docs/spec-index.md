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
| API-LOC-1 | `API-LOC-1.md` | `GET /api/locations/countries/autocomplete` — 국가 자동완성 | #46 |
| API-LOC-2 | `API-LOC-2.md` | `GET /api/locations/cities/autocomplete` — 도시 자동완성 | #46 |
| API-PLACE-1 | `API-PLACE-1.md` | `GET /api/places/{placeId}` — 여행 장소 조회 | #48 |
| API-PREF-1 | `API-PREF-1.md` | `PATCH /api/users/me/preferences` — 사용자 MBTI 등록/수정 | #51 |
| API-PREF-2 | `API-PREF-2.md` | `POST /api/users/me/consents/photo` — 사진 데이터 분석 동의 | #50 |
| API-PREF-3 | `API-PREF-3.md` | `POST /api/users/me/taste-profile/analysis` — 취향 분석(SSE) | #2, #55, #78 |
| API-PREF-4 | `API-PREF-4.md` | `GET /api/users/me/taste-profile` — 취향 조회 | #5, #78 |
| API-USER-1 | `API-USER-1.md` | `PATCH /api/users/me/profile` — 사용자 프로필 등록/수정 | #51 |
| API-USER-2 | `API-USER-2.md` | `DELETE /api/users/me` — 회원탈퇴 | #78 |

미구현(스코프 밖): `API-BOOKING-1`(예약 제휴 링크), `API-SHARE-1/2/3`(코스 공유 링크).

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
> 마지막 동기화 커밋은 `eedd7ba`(2026-08-03)로 **Notion의 LOC-2 수정보다 이전**이라 401이 남아
> 있다. 따라서 "동기화 커밋 = 최신 Notion"이 항상 성립하지는 않는다. 현재 pin은 수정이 반영된
> `7d5d6b4`다. 다음 동기화가 돌면 그 결과가 정본이 되므로, 그때 pin을 옮기고 이 절을 정리한다.

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
