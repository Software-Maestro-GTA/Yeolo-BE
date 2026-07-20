# 아키텍처 규약 — BE

> 이 문서는 **"어떻게 구현할지"**(구조·규약)를 고정합니다. "무엇을/어디까지"는
> `docs/sprint-scope.md`, 근거 명세는 `docs/spec-index.md`를 보세요.
> 여기서 정하지 않은 세부는 명세(`specs/`) → 기존 코드 컨벤션 순으로 따릅니다.

## 1. 아키텍처 스타일 — 레이어드 + 도메인 순수화

전통 레이어드(`controller → service → repository`)를 기본으로 하되,
**JPA `@Entity`와 순수 도메인 모델을 분리**하고, **AI 내부 API 호출부만 어댑터**로 격리합니다.

```
com.soma.yeolo.<domain>/          예) auth, user, tasteprofile, course
  ├─ controller/   웹 어댑터 (얇게). 요청 검증·DTO 변환만.
  ├─ dto/          요청/응답 record. 엔티티/도메인 직접 노출 금지.
  ├─ service/      응용 로직. 트랜잭션 경계(@Transactional)는 여기.
  ├─ domain/       순수 도메인 모델 + 비즈니스 로직 (프레임워크 무관).
  ├─ entity/       JPA @Entity (영속성 모델). 도메인 ↔ 엔티티 매핑 담당.
  └─ repository/   Spring Data JPA Repository 인터페이스.

com.soma.yeolo.<domain>.client/   AI 내부 API(/internal/ai/*) 호출 어댑터.
com.soma.yeolo.global/            공통: 예외/응답/설정/보안/BaseEntity 등.
```

**매핑 규칙**
- 계층 간 객체 변환은 정적 팩토리 메서드로 수동 매핑: `Entity.from(domain)`,
  `entity.toDomain()`, `Response.from(domain)`. (MapStruct 등 별도 라이브러리 미도입)

### 1-2. 의존성 역전 (DIP) — service ↔ repository/외부 격리

**원칙: 서비스(응용 계층)는 프레임워크·인프라 세부를 몰라야 한다.** 서비스는 자신이
**소유한 포트(인터페이스)** 에만 의존하고, 영속성·외부 호출은 그 포트를 구현한 어댑터가 담당한다.
의존 방향은 항상 바깥(web·JPA·외부 API) → 안(service·domain)으로 향한다.

- **영속 포트 — 애그리거트당 하나로 합친다:** 서비스는 `JpaRepository`나 `@Entity`를 직접
  주입/import하지 않는다. 대신 서비스 쪽이 소유한 **영속 포트**에 의존하고, 순수 도메인만
  주고받는다. **저장·조회를 연산별 포트로 쪼개지 않고(CQRS 분리 지양)** 애그리거트당 **포트 하나**에
  `save`/`find…` 메서드를 함께 둔다. 포트를 나누는 건 정말 **불가피한 경우**(예: 읽기 모델이
  전혀 다른 저장소를 쓰는 경우)에 한한다.
- **네이밍(고정):** 완전 분리 도메인의 영속 계층은 아래 3종으로 통일한다.

  | 역할 | 이름 | 위치 | 비고 |
  | :--- | :--- | :--- | :--- |
  | 포트(인터페이스) | `<Aggregate>Repository` | `service/port/` | 서비스가 의존. 순수 도메인만 주고받음 |
  | 구현 어댑터 | `<Aggregate>RepositoryImpl` | `repository/` | `@Component`, 도메인↔엔티티 매핑을 격리 |
  | Spring Data | `<Aggregate>JpaRepository` | `repository/` | `JpaRepository` 상속. **어댑터 내부에서만** 사용 |

  예: 포트 `TasteProfileRepository`(`save`/`findLatestByUserId`) ← 어댑터 `TasteProfileRepositoryImpl`
  → Spring Data `TasteProfileJpaRepository`. 어댑터가 `Entity.from(domain)`·`entity.toDomain()`
  매핑을 담당하고, 서비스는 포트만 안다.
- **외부 호출 포트:** AI 내부 API·지오코딩 등 외부 연동은 `<domain>.client/`에 **포트 인터페이스**를
  두고(예: `ReverseGeocodeClient`), 구현 어댑터를 교체 가능하게 한다. (§5)
- **표현 계층은 안으로 내려가지 않는다:** 컨트롤러가 소유한 웹 객체(`SseEmitter` 등)를 서비스로
  넘기는 방식은 지양하되, SSE처럼 스트리밍 수명주기를 서비스가 책임져야 하는 경우는 예외로
  허용한다(이때도 저장·외부호출은 포트 뒤에 둔다).
- **효과:** 서비스는 web·JPA import 0 → 순수 자바 단위 테스트가 가능해진다(§8).
- **병합형 예외:** §1-1에서 "엔티티=도메인" 병합을 택한 얇은 CRUD 도메인(예: User)은 별도 포트·
  어댑터 없이 Spring Data `JpaRepository`를 서비스에서 직접 써도 된다. 이때 인터페이스 이름은
  `<Aggregate>Repository`(예: `UserRepository`, `RefreshTokenRepository`)를 그대로 쓴다(별도 포트가
  없으므로 `JpaRepository` 접미사 없이). 완전 분리 도메인(TasteProfile·Course)은 위 영속 포트·네이밍
  규칙을 따른다.

### 1-1. 도메인 분리 정책 (완전 분리 vs 병합)

- **원칙:** DTO ↔ Domain ↔ Entity 3계층 분리 (필요 시 리포지토리 포트/어댑터 포함).
  순수 `domain/` 모델은 JPA를 모른다.
- **예외 — "엔티티=도메인" 병합 허용 기준:** 아래를 **모두** 만족하는 얇은 도메인은 `@Entity`에
  가벼운 도메인 메서드를 두는 병합형을 허용한다(보일러플레이트 방지).
  - 식별/저장 위주이고, 보호할 **불변식·상태 전이 규칙이 거의 없음**
  - 행위가 생성·프로필 갱신 수준의 단순 CRUD에 가까움
- **반대로** 소유권 검증·상태 전이(예: 코스 생성 라이프사이클)·불변식 등 **보호할 규칙이 있는
  도메인은 반드시 완전 분리**한다.
- **이번 스프린트 도메인 적용(고정 — 도메인마다 재판단하지 않는다):**

  | 도메인 | 방식 | 근거 |
  | :--- | :--- | :--- |
  | User (DOM-3) | **병합** (엔티티=도메인) | 식별 위주, 행위는 생성·로그인 갱신 수준 |
  | TasteProfile (DOM-1) | 완전 분리 | 분석 결과 구조·유효성 규칙 |
  | Course (DOM-2) | 완전 분리 | 소유권 검증·생성 상태 전이(SSE) |
  | Image metadata (DOM-5) | DTO/파이프라인 | 영속 최소, 전처리 성격 |

- 새 도메인이 표에 없으면 위 기준으로 판단하되, **병합을 택할 경우 PR 설명에 근거를 남긴다.**

## 2. 영속성 (JPA)

- **스키마 관리:** 마이그레이션 도구 없이 `ddl-auto` 사용. **엔티티가 스키마의 근거.**
  - `local`: `spring.jpa.hibernate.ddl-auto=update`
  - `dev`/`prod`: `validate` (자동 변경 금지, 스키마 드리프트 감지용)
- **엔티티 관례**
  - `@NoArgsConstructor(access = PROTECTED)`, 세터 지양(도메인 메서드로 상태 변경).
  - PK: `@GeneratedValue(strategy = IDENTITY)` (MySQL auto-increment).
  - Enum: `@Enumerated(EnumType.STRING)` 강제. 값·라벨은 도메인 명세(DOM) 그대로.
  - 컬럼/테이블명·타입은 **DOM 명세를 근거**로 매핑.
- **BaseEntity:** `@MappedSuperclass` + JPA Auditing(`@EnableJpaAuditing`)으로
  `createdAt`/`updatedAt`(`@CreatedDate`/`@LastModifiedDate`) 공통 상속.

## 3. 인증 (OAuth2 + JWT)

- Google OAuth 인가코드 → 토큰 교환 → 사용자 upsert → **Access/Refresh JWT 발급**. (명세 API-FB-1)
- **Refresh Token은 DB 테이블에 저장** (`RefreshToken` 엔티티, `auth` 도메인).
  - 저장 값: `userId`, 토큰 **해시**(평문 저장 금지), `expiresAt`.
  - 재발급 시 회전(rotation), 로그아웃/탈취 시 revoke 가능하도록 설계.
  - (추후 필요 시 Redis로 이전 가능한 구조로 추상화 여지 남길 것.)
- Access Token은 무상태 검증(서명+만료). 인증 필터는 `global/security`에 배치.
- 비밀값(client id/secret, JWT secret)은 커밋 금지 — 환경변수/`application-local.properties`.

## 4. API 응답 · 예외

- **응답 포맷은 엔드포인트별 명세를 근거로 결정.** 코드 작성 전 해당 `specs/api-specs/API-*.md`의
  Response 스키마를 읽고 그대로 맞춘다. **임의 공통 래퍼를 강제하지 않음** (FE 계약과 어긋날 위험).
- **예외 처리:** `@RestControllerAdvice` 전역 핸들러 + `ErrorCode` enum.
  - 에러 응답 바디·HTTP status·에러코드는 **명세의 Error Code 정의를 그대로** 따른다.
- Bean Validation(`@Valid`)으로 요청 검증, 위반 시 명세의 검증 에러 포맷으로 변환.

## 5. AI 내부 API 연동 (`/internal/ai/*`)

- BE는 AI 로직을 구현하지 않고 **호출만** 한다(명세 API-BA-*).
- HTTP 클라이언트: Spring `RestClient`(동기). 호출부는 `<domain>.client/` 어댑터로 격리.
- **타임아웃·재시도·실패 시 사용자 노출 에러**를 명시적으로 정의(무한 대기 금지).
- SSE: `POST /api/courses`는 AI(API-BA-1, SSE)를 중계 스트리밍.
  이벤트 단계명(`LOADING_TASTE_PROFILE` → `GENERATING_COURSE` → `complete`)은 **명세 그대로** 사용.

## 6. 설정 · 프로파일

- 프로파일: `local` / `dev` / `prod`. 공통은 `application.properties`,
  환경별은 `application-<profile>.properties`.
- 로컬 비밀값·오버라이드: `application-local.properties`(gitignore).
- 외부 주입 값은 환경변수 우선. 설정 키는 `application.properties`에 기본/문서화.

## 7. DTO · 코드 스타일

- 요청/응답 DTO는 Java `record` (불변). 엔티티를 API로 직접 노출 금지.
- Controller 얇게, 트랜잭션·비즈니스 로직은 Service, DB 접근은 Repository.
- Lombok 사용하되 엔티티에 `@Data`/무분별한 `@Setter` 지양.

## 8. 테스트

**원칙: 도메인 격리를 활용해 "외부 의존성 없는 단위 테스트"를 우선한다.**
비즈니스 규칙 대부분을 빠르고 격리된 단위 테스트로 검증하고, DB·AI 등 외부 의존이
필요한 부분만 좁게 슬라이스/목서버로 확인한다. 인수 기준(REQ Acceptance Criteria)·
예외 케이스 중심으로 작성.

| 계층 | 방식 | 외부 의존성 | 도구 |
| :--- | :--- | :--- | :--- |
| `domain/` (순수 모델·규칙) | 순수 단위 테스트 | 없음 | JUnit 5 |
| `service/` (오케스트레이션) | **순수 자바 단위 테스트** | 없음(포트는 손수 짠 fake/stub) | JUnit 5 (필요 시에만 Mockito) |
| `repository/` | **기본 미작성**(CRUD) | DB | (커스텀 쿼리만) `@DataJpaTest` |
| `controller/` | **기본 미작성**(배관) | — | (필요 시) `@WebMvcTest` |
| `client/` (외부 호출) | 어댑터 | 없음(실 호출 미발생) | `MockRestServiceServer` |

- 예: JWT 만료·refresh 회전 규칙, 코스 소유자 권한 검증, 성향 프로필 유효성 →
  도메인/서비스 단위 테스트에서 시간·DB·네트워크 없이 검증.
- 전 계층 통합이 꼭 필요할 때만 `@SpringBootTest`.
- **기본 정책 — 서비스에 집중한다:** 비즈니스 규칙은 **서비스 단위 테스트**로 검증한다.
  - **서비스=순수 자바 우선:** DIP 포트(§1-2)는 **손수 짠 fake/stub**으로 대체해 JUnit만으로 검증한다.
    Mockito·`ReflectionTestUtils` 등 프레임워크 더블은 **대체가 어려운 협력자에 한해** 쓴다
    (예: SSE 스트리밍 검증의 `SseEmitter`). 영속 포트(`<Aggregate>Repository`)는 fake로, 리플렉션 id 주입은 쓰지 않는다.
  - **컨트롤러·리포지토리=기본 미테스트:** 얇은 배관/단순 CRUD이므로 별도 테스트를 **작성하지 않는다.**
    `@WebMvcTest`(컨트롤러)·`@DataJpaTest`(리포지토리)는 **커스텀 검증·쿼리가 있을 때만** 예외로 작성한다.
  - 도메인=순수(더블 0).
- 리포지토리/컨텍스트 테스트 DB는 **H2(MySQL 호환 모드)**로 확정. 설정은
  `src/test/resources/application.properties`.
- 실행: `./gradlew test` (자세한 명령은 CLAUDE.md).
