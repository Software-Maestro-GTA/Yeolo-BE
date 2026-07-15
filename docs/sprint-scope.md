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

### 스코프 상의 핵심 포인트
- **인증:** Google OAuth 인가코드 → Google 토큰 교환 → 사용자 upsert → JWT(Access/Refresh) 발급. (#3)
- **성향 분석:** 이미지 EXIF(좌표·시간) 메타데이터만 수집 → Reverse Geocode로 한글 주소화 →
  AI 서버(API-BA-6)로 분석 요청. 개인정보 동의 사전 검증 필수. (#2)
- **코스 생성:** `POST /api/courses`(SSE)로 조건 입력 → 성향 프로필 로딩 → AI 서버(API-BA-1, SSE)
  연동 → 진행 이벤트(`LOADING_TASTE_PROFILE` → `GENERATING_COURSE` → `complete`) 스트리밍 →
  결과를 코스 정보(DOM-2)로 저장. (#6, #4)
- **조회:** 코스 목록(최신순·페이지네이션) 및 상세, 소유자 권한 검증. 성향 프로필 조회. (#1, #5)

## Out of Scope — 이번 스프린트에서 손대지 않음

아래는 명세엔 있으나 이번 스프린트 대상이 **아닙니다**. 필요하면 먼저 확인 후 진행.

- **API:** API-FB-3(최소 설문 성향분석), API-FB-11(로그아웃), API-FB-12(회원탈퇴)
- **기능/요구사항:** 그룹 톡방·일정조율(REQ-1), 그룹 교차분석(REQ-2), AI 앨범 정리(REQ-3),
  예약/제휴 링크(REQ-4), 마이페이지 통합(REQ-5), 추천 서버 운영 안정성(REQ-6),
  동행자 커뮤니티(REQ-10), 공동 앨범(REQ-12), 추천 일정 상세 UI(REQ-9, FE 영역)
- **타 파트:** FE 구현, AI 엔진 내부 로직, 인프라/배포

## 담당 외
- FE·AI·인프라는 다른 담당자 몫입니다. BE는 AI 내부 API를 **호출**만 합니다.
