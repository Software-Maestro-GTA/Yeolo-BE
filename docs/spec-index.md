# 명세 인덱스 (BE 관점)

`specs/`(submodule, `Yeolo-SPEC`)의 문서 중 **이번 스프린트 BE 작업에 필요한 것**을 정리한
빠른 참조표입니다. 코드 작성 전 해당 명세를 반드시 읽으세요.

> ⚠️ `specs/domain-specs/domain.md` 인덱스의 링크는 ID↔파일명이 어긋나 있습니다.
> 아래 표의 **파일 경로가 정확**합니다.

## API 명세 (`specs/api-specs/`)

### FE ↔ BE (구현 대상)
| API ID | 파일 | Method · Endpoint | 이슈 |
| :--- | :--- | :--- | :--- |
| API-FB-1 | `API-FB-1.md` | `POST /api/auth/google` — Google OAuth 로그인 | #3 |
| API-FB-2 | `API-FB-2.md` | `POST /api/taste-profile/behavior` — 이미지 메타데이터 성향분석 | #2 |
| API-FB-4 | `API-FB-4.md` | `POST /api/courses` — 코스 생성(SSE) | #6 |
| API-FB-7 | `API-FB-7.md` | `GET /api/courses/{courseId}` — 코스 상세 | #1 |
| API-FB-8 | `API-FB-8.md` | `GET /api/me/taste-profile` — 내 성향 프로필 조회 | #5 |
| API-FB-10 | `API-FB-10.md` | `GET /api/courses` — 이전 코스 목록 | #1 |

### BE ↔ AI 내부 API (BE가 호출)
| API ID | 파일 | Method · Endpoint | 이슈 |
| :--- | :--- | :--- | :--- |
| API-BA-1 | `API-BA-1.md` | `POST /internal/ai/courses` — 코스 생성(SSE) | #4 |
| API-BA-6 | `API-BA-6.md` | `POST /internal/ai/taste-profile/behavior` — 성향 분석 | #2 |

### 스코프 밖 API (참고)
`API-FB-3`(설문 성향분석), `API-FB-11`(로그아웃), `API-FB-12`(회원탈퇴) — 이번 스프린트 대상 아님.

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
