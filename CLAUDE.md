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
| 영속성 | Spring Data JPA + PostgreSQL (`postgresql`) — local·dev·prod 공통 |
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
- DB 스키마: 마이그레이션 도구 없이 `ddl-auto`(엔티티=스키마). local·dev=`update`, prod=`validate`.
  `update`는 추가만 하므로, 스키마 변경 시 **prod 적용용 DDL을 `docs/ddl/<table>.sql`에 남긴다.**
- 인증: Refresh Token은 DB 테이블(`RefreshToken` 엔티티)에 **해시로** 저장.
- 응답 포맷: 임의 공통 래퍼 강제 없이 **엔드포인트별 명세의 Response 스키마를 그대로** 따름.
- Controller는 얇게, 비즈니스 로직은 Service에. DB 접근은 Repository로.
- 요청/응답은 별도 DTO로 매핑하고 JPA 엔티티를 API로 직접 노출하지 않습니다.
- **FE 응답에 `JsonNode`(`ObjectNode`/`ArrayNode`)를 필드 타입으로 노출하지 않습니다.** AI 원본
  JSON이라도 FE로 나가는 응답은 명세의 스키마대로 **DTO(record)로 타입을 명시**합니다. `JsonNode`를
  그대로 반환하면 응답 스키마가 불투명해지고(FE 타입 생성 시 `array`·`bigDecimal` 등 `JsonNode`의 내부
  getter가 필드로 새어 나옴) 계약이 무너집니다. 저장된 JSON은 `ObjectMapper.readValue(..., Dto.class)`로
  역직렬화해 반환하되(미지 필드는 `FAIL_ON_UNKNOWN_PROPERTIES=false`로 무시), AI 파싱·저장 등 **내부
  파이프라인은 무손실 보존을 위해 `JsonNode` 유지** 가능. 경계는 "FE로 나가는 응답"입니다.
- Enum·필드명·라벨은 **도메인 명세의 값을 그대로** 따릅니다(임의 변경 금지).
  **명세 개정으로 값이 바뀌면, 새 값 검증과 함께 "제거된 옛 값은 거부된다" 테스트를 반드시 남깁니다.**
  (예: `BudgetTypeTest.명세_개정으로_제거된_standard는_거부된다`) BE 자체 검증은 자기 enum 기준이라
  명세 위반을 못 잡고, 판정 주체가 AI 서버뿐이면 실패가 런타임까지 밀립니다. 실제로 `budgetType`이
  `moderate`→`standard`로 되돌아간 리버트를 이 테스트가 없어 놓쳤고(테스트도 함께 되돌아감),
  코스 생성이 AI 400으로 죽었습니다.
- Lombok 사용. 엔티티는 `@NoArgsConstructor(access = PROTECTED)` 등 JPA 관례 준수.
- 예외는 명세의 Error Code/HTTP status에 맞춰 처리(전역 예외 핸들러 권장).
- SSE 엔드포인트(`POST /api/courses`, AI 연동)는 명세의 이벤트 단계명을 그대로 사용.

## 환경 설정 · 검증 자산 규칙

- **`INTERNAL_API_KEY`는 BE와 AI가 공유하는 대칭 키다.** `JWT_SECRET`·`DB_URL`처럼 환경별로 새로
  발급하면 안 된다 — 한쪽만 바꾸는 순간 그 환경의 BE→AI 호출이 **전부 401**이 된다.
  배포 가드(`deploy.yml`)는 값이 **비었는지만** 검사하고, AI 디플로이먼트는 `envFrom ... optional: true`라
  키가 틀려도 파드는 정상 기동한다 — 즉 양쪽 다 초록불인데 런타임에만 터진다.
  값 대조는 노출 없이 해시로: `kubectl -n <ns> exec deploy/<was|ai> -- printenv INTERNAL_API_KEY | shasum -a 256`
- **Postman 컬렉션은 dev·prod 두 개다. 손으로 고치는 것은 dev 하나뿐이다.**
  `docs/postman/Yeolo-BE-Dev.postman_collection.json` 이 원본이고,
  `Yeolo-BE-Prod.postman_collection.json` 은 `make-prod-collection.py` 로 **생성**한다.
  환경(Environment) 파일은 여전히 두지 않고 **변수를 컬렉션 변수로** 관리한다 — 환경 파일은 애초에
  요청을 담지 못한다(`_postman_variable_scope`).
  - **왜 파일을 나눴나(2026-08-27).** `dev` 와 `main` 은 배포 시점이 달라 **API 계약이 다른 기간이
    있다.** 한 파일에서 `baseUrl` 만 바꿔 쓰면 dev 에만 있는 엔드포인트가 prod 에서 404 로 나오는데,
    그게 컬렉션이 틀린 건지 서버가 안 나간 건지 구분되지 않는다. 대신 **두 벌을 손으로 맞추지는
    않는다** — prod 파일은 항상 생성물이며, 직접 편집하거나 Postman 에서 export 하지 않는다.
  - **prod 파일은 dev→main 릴리스 직후에 재생성한다**(`python3 docs/postman/make-prod-collection.py`).
    평소 dev 작업에서는 돌리지 않는다 — 아직 배포되지 않은 기능이 prod 컬렉션에 들어가면 파일을
    나눈 의미가 없어진다.
  - **API 계약이 바뀌면 dev 파일을 같은 커밋에서 고친다** — 새 엔드포인트뿐 아니라 기존 요청·응답의
    필드 변경도 포함이다(판정 기준과 체크리스트는 §작업 흐름 5). 코드만 고치고 컬렉션을 빠뜨리면
    "Postman에 왜 없지?"가 된다. 새 변수가 필요하면(예: `shareToken`) 컬렉션 변수에도 추가한다.
  - **prod 컬렉션의 파괴적 요청(회원탈퇴·코스 삭제)은 `allowDestructive` 가드로 기본 차단**돼 있다.
    생성기가 pre-request 스크립트를 넣으므로 손으로 지우지 않는다 — 실사용자 데이터가 걸려 있다.
    차단의 실체는 **`throw`** 다. `pm.execution.skipRequest()` 는 Collection Runner·CLI 에서만
    듣고 **개별 Send 에는 효력이 없어**, 그걸로만 막으면 이 컬렉션의 실제 사용 경로(수동 Send)에서
    DELETE 가 그대로 나간다. 순서를 바꾸거나 throw 를 없애지 않는다.
  - **prod 전용 값은 생성물이 아니라 `PROD_OVERRIDES`(생성기)에 적는다.** 생성된 JSON 을 손으로
    고치면 다음 릴리스 재생성에서 조용히 되돌아가고, diff 에도 원인이 남지 않는다.
  - **이 파일이 `git ls-files docs/postman/` 에 잡히는지 확인한다.** `.gitignore` 의 `docs` 때문에
    `git add -f` 를 한 번이라도 빠뜨리면 파일이 **추적되지 않은 채** 로컬에만 남고, 그 뒤로는
    `git status` 에도 안 뜨고 diff에도 안 잡혀 아무도 눈치채지 못한다. 실제로 컬렉션을 최신으로
    맞춰 놓고도 저장소에는 한 번도 올라간 적이 없는 상태였다(2026-08). 출력이 비면 미추적이다.
  - **시크릿(`internalApiKey`·`jwtSecret`)은 빈 값으로 커밋한다.** Postman 에서는 `Current value`
    칸에만 입력한다 — `Initial value` 에 넣으면 export 시 파일에 박힌다.
  - 스크립트는 `pm.collectionVariables` 를 쓴다(`pm.environment` 는 환경이 없어 동작하지 않는다).
  - `.gitignore` 의 `docs` 때문에 커밋에는 `git add -f` 가 필요하다.
- **Postman 테스트 대상은 dev 서버 하나다.** `baseUrl`은 dev CloudFront를 가리키며 로컬(`localhost:8080`)
  대상 테스트는 현재 하지 않는다 — 환경 파일을 하나로 유지하는 이유이기도 하다. 로컬을 찔러야 할
  일이 생기면 새 환경 파일을 만들지 말고 그 환경의 `baseUrl`·`jwtSecret`만 바꿔 쓴다.
  상세는 `docs/postman/README.md`.
- **토큰은 로그인으로만 얻는다 — `tokenMode=mint`는 쓰지 않는다.** mint(`jwtSecret`으로 access token을
  직접 서명)는 실제 인증 경로를 건너뛴다: 로그인·토큰 발급·Refresh Token 저장이 실행되지 않아
  `POST /api/auth/refresh`·로그아웃처럼 **DB 세션 행에 의존하는 API를 테스트할 수 없다**
  (`RefreshTokenService.matches`가 저장된 해시와 대조하므로 직접 서명한 refresh는 언제나 거부된다).
  서버 키가 회전되면 원인이 불분명한 401만 남는 문제도 있다. `tokenMode`는 항상 `login`으로 둔다.
- **Postman 401 진단은 "지금 서명한 토큰"으로 한다.** 저장된 옛 `refreshToken`의 서명으로 키를
  판정하면 안 된다 — 서버 키가 교체되기 전에 발급된 토큰이면 키가 정확해도 서명이 안 맞아,
  멀쩡한 `jwtSecret`을 의심하게 된다(실제로 겪었다). 임의 UUID를 `sub`로 지금 서명한 토큰이
  200이면 키는 정상이고 **그 계정이 탈퇴 처리된 것**이다(`JwtAuthenticationFilter`의 탈퇴자 차단).
- **BE인지 AI인지 가르기.** AI 내부 API(`/internal/ai/*`)는 ClusterIP 전용이라 클러스터 안에서만
  부를 수 있다. 500이 날 때는 BE를 거치지 않고 AI를 직접 호출해 경계를 가른다 — Postman은
  `kubectl -n app-dev port-forward deploy/ai 8000:8000` 후 `07. Internal AI` 폴더를 쓴다.
  AI 호출 실패 로그는 `AiTasteProfileClient` / `InternalAiCourseClient` 에 남는다.
- **dev·prod가 같은 클러스터를 쓴다.** `-n app-dev` 가 dev, **`-n app` 이 prod** 다(이름에 `dev`가
  안 붙은 쪽이 prod). 명령어를 붙여넣기 전에 네임스페이스를 확인한다.

## 작업 흐름

1. `docs/sprint-scope.md`에서 대상 작업(TSK/이슈)과 연결된 API/FUN/DOM ID 확인
2. `specs/`에서 해당 API·도메인·기능 명세 정독
3. 도메인 → 엔티티/리포지토리 → 서비스 → 컨트롤러/DTO 순으로 구현
4. 인수 기준 및 예외 케이스에 대한 테스트 작성 → `./gradlew test`
   (도메인/서비스는 격리된 단위 테스트 우선, DB·AI 의존 부분만 슬라이스/목서버. 상세: `docs/architecture.md` §8)
5. **API 계약을 건드렸으면 Postman 컬렉션에 반영한다 — 생략 불가.** 판정 기준은 "새 엔드포인트"가
   아니라 **"FE가 보내거나 받는 것이 달라졌는가"** 다. 아래 중 하나라도 해당하면 이 단계를 탄다:
   - 엔드포인트 추가·삭제, 경로·HTTP 메서드 변경
   - 요청 필드/쿼리 파라미터/헤더의 추가·삭제·이름 변경 (예: `email` 제거, `country` 추가)
   - 응답 필드의 추가·삭제·이름·타입 변경 (예: `photoUrls`→`photoUrl`, `recentCourseId` 추가)
   - Enum 허용값 변경, 에러 코드/HTTP status 변경
   - AI 내부 API(`/internal/ai/*`) 요청·응답 변경 → `07. Internal AI` 폴더도 같이 고친다

   반영 대상은 요청 본문·쿼리뿐 아니라 **요청 `description`과 테스트 스크립트**까지다 — 응답 필드가
   바뀌면 `pm.collectionVariables.set(...)`이 조용히 `undefined`를 저장한다. 새 변수가 필요하면
   컬렉션 변수에도 추가한다. **`docs`가 gitignore라 `git add -f` 로 함께 스테이징**하고, 커밋 메시지
   초안에 이 파일이 포함되어 있는지 확인한다 — 이 단계의 실패는 대부분 "고쳤는데 커밋이 안 됨"이다.
6. 커밋 메시지 초안 전, 변경 diff를 **별도 agent로 검증**: `/code-review high`
   (런타임 동작 확인이 필요하면 `/verify`). 지적사항 반영 후 재검증 → 통과 시 다음 단계.
7. 이슈 단위 브랜치 생성(Claude) → 테스트·리뷰 통과 시 **커밋 메시지 초안 제시(Claude)**.
   **실제 커밋·push·PR은 사용자가** 수행.

## Git · 커밋 규칙

- **브랜치 전략 — `main`(prod) / `dev`(dev):** 두 환경으로 나뉘어 있고 각각 CI/CD가 붙어 있다.
  `main` 머지 = **prod 배포**, `dev` 머지 = **dev 배포**.
- **커밋·푸쉬는 무조건 `dev` 기준으로 한다. `main`에 직접 커밋·푸쉬하지 않는다.**
  - 이슈 브랜치는 항상 **`dev`에서 분기**한다 (`git checkout dev && git pull` 후 브랜치 생성).
  - PR의 **base 브랜치는 `dev`**. prod 반영은 `dev` → `main` PR로만 한다(릴리스 시점, 사용자 판단).
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
