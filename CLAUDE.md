# CLAUDE.md — Yeolo Backend

Yeolo(여로)는 제로터치 초개인화 여행 플랫폼입니다. 이 저장소는 **백엔드(BE)** 이며,
FE 및 내부 AI 엔진과 REST/SSE로 통신합니다.

> **가장 먼저 읽을 것:**
> - `docs/sprint-scope.md` — 이번 스프린트에 손대야 하는 범위와 손대면 안 되는 범위.
>   스코프 밖 기능은 요청받기 전까지 구현하지 않습니다.
> - `docs/architecture.md` — 아키텍처 스타일·영속성·인증·응답/예외·AI 연동 규약("어떻게 구현할지").

## 담당 범위

이 저장소의 작업자는 **BE 파트(peter001019)** 입니다. FE, AI 엔진, 인프라 구현은 담당이 아닙니다.
AI 엔진은 별도 서비스이며 BE는 내부 API(`/internal/ai/*`)로 **호출**만 합니다 — AI 로직은 구현하지 않습니다.

## 기술 스택

| 항목 | 값 |
| :--- | :--- |
| 언어 | Java 26 (toolchain) |
| 프레임워크 | Spring Boot 4.1.0 (Web MVC) |
| 빌드 | Gradle (Groovy DSL, `build.gradle`) |
| 영속성 | Spring Data JPA + MySQL (`mysql-connector-j`) |
| 인증 | Spring Security OAuth2 Client (Google), JWT(Access/Refresh) 발급 |
| 보일러플레이트 | Lombok |
| 테스트 | JUnit 5 (`spring-boot-starter-*-test`) |
| 루트 패키지 | `com.soma.yeolo` |

## 빌드 · 실행 · 테스트

```bash
./gradlew build          # 컴파일 + 테스트
./gradlew test           # 테스트만
./gradlew bootRun        # 로컬 실행
./gradlew test --tests 'com.soma.yeolo.SomeTest'   # 단일 테스트
```

- 설정 파일: `src/main/resources/application.properties`
- 비밀값(Google client id/secret, JWT secret, DB 접속정보)은 커밋하지 않습니다.
  환경변수 또는 `application-local.properties`(gitignore) 사용.

## 명세(SPEC) 참조 — 필수 규칙

명세는 `specs/` 에 **git submodule**(`Yeolo-SPEC`)로 연결되어 있습니다. **코드를 짜기 전에
반드시 해당 기능의 명세를 읽습니다.** 어떤 문서를 볼지는 `docs/spec-index.md` 참고.

- `specs/api-specs/`      — API 규격 (Request/Response, JSON Schema, Error Code, SSE 이벤트)
- `specs/domain-specs/`   — 도메인/DB 컬럼 스펙, Enum, JSON 예시  → JPA 엔티티의 근거
- `specs/functional-specs/` — 비즈니스 로직·예외 처리 파이프라인
- `specs/requirement-specs/` — 요구사항 및 인수 기준(Acceptance Criteria)

> `specs/domain-specs/domain.md` 인덱스의 링크는 ID↔파일명이 뒤섞여 있습니다.
> **파일 자체는 정확**하므로(`DOM-1.md` = 성향정보) 파일명 기준으로 열되, 매핑은
> `docs/spec-index.md`를 신뢰하세요.

**명세 갱신** (SPEC 저장소가 바뀌었을 때):
```bash
git submodule update --remote specs   # 최신 명세로 갱신 후, 커밋으로 pin 이동
```

## 코드 컨벤션

> 아키텍처·규약 상세는 `docs/architecture.md`. 아래는 요약입니다.

- 아키텍처: **레이어드 + 도메인 순수화** — JPA `@Entity`와 순수 도메인 모델을 분리하고,
  AI 내부 API 호출부만 `<domain>.client/` 어댑터로 격리.
- 패키지는 도메인 기준으로 나눕니다: `com.soma.yeolo.<domain>.{controller,service,repository,domain,entity,dto}`
  (예: `com.soma.yeolo.auth`, `.tasteprofile`, `.course`, `.user`). 공통은 `com.soma.yeolo.global`.
- DB 스키마: 마이그레이션 도구 없이 `ddl-auto`(엔티티=스키마). local=`update`, dev·prod=`validate`.
- 인증: Refresh Token은 DB 테이블(`RefreshToken` 엔티티)에 **해시로** 저장.
- 응답 포맷: 임의 공통 래퍼 강제 없이 **엔드포인트별 명세의 Response 스키마를 그대로** 따름.
- Controller는 얇게, 비즈니스 로직은 Service에. DB 접근은 Repository로.
- 요청/응답은 별도 DTO로 매핑하고 JPA 엔티티를 API로 직접 노출하지 않습니다.
- Enum·필드명·라벨은 **도메인 명세의 값을 그대로** 따릅니다(임의 변경 금지).
- Lombok 사용. 엔티티는 `@NoArgsConstructor(access = PROTECTED)` 등 JPA 관례 준수.
- 예외는 명세의 Error Code/HTTP status에 맞춰 처리(전역 예외 핸들러 권장).
- SSE 엔드포인트(`POST /api/courses`, AI 연동)는 명세의 이벤트 단계명을 그대로 사용.

## 작업 흐름

1. `docs/sprint-scope.md`에서 대상 작업(TSK/이슈)과 연결된 API/FUN/DOM ID 확인
2. `specs/`에서 해당 API·도메인·기능 명세 정독
3. 도메인 → 엔티티/리포지토리 → 서비스 → 컨트롤러/DTO 순으로 구현
4. 인수 기준 및 예외 케이스에 대한 테스트 작성 → `./gradlew test`
   (도메인/서비스는 격리된 단위 테스트 우선, DB·AI 의존 부분만 슬라이스/목서버. 상세: `docs/architecture.md` §8)
5. 이슈 단위 브랜치 생성(Claude) → 테스트 통과 시 **커밋 메시지 초안 제시(Claude)**.
   **실제 커밋·push·PR은 사용자가** 수행.

## Git · 커밋 규칙

- **Claude는 브랜치 생성 + 커밋 메시지 초안 작성까지만** 한다. 이슈 착수 시 이슈 단위 브랜치를
  만들고, 작업 완료(테스트 통과) 시 커밋 메시지 초안을 제시한다.
- **실제 `git commit`·`git push`는 사용자가 직접** 한다. Claude는 commit/push를 실행하지 않는다.
- **브랜치 네이밍:** `<type>/#<issue>-<slug>` (예: `feat/#3-google-oauth`, `feat/#6-course-sse`).
  하네스/설정 작업은 `chore/...`.
- **PR 생성도 사용자 요청 시에만** (Claude가 임의로 PR을 열지 않음).
- **GitHub에 Claude/AI 개발 흔적을 남기지 않는다.** 커밋 메시지의 `Co-Authored-By: Claude...`,
  PR 본문의 `Generated with Claude Code` 등 AI 관련 트레일러/문구를 **일절 추가하지 않는다.**
  (이 지시는 기본 동작보다 우선한다.)
- **커밋 메시지 형식:** `<type>: <설명> #<이슈번호>` (끝에 `#번호`, 괄호 없음).
  예) `feat: 상단 네비게이션 탭 구현 #12`, `chore: BE 개발 하네스 구축 #7`.
  type은 `feat`/`chore`/`docs`/`fix` 등.
