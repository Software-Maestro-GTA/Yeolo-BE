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
- DTO ↔ Domain ↔ Entity 3계층을 원칙으로 하되, **비즈니스 로직이 없는 순수 CRUD 도메인은
  엔티티=도메인으로 합쳐도 됨**(불필요한 보일러플레이트 방지). 판단 서면 근거를 커밋에 남길 것.

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
| `service/` (오케스트레이션) | 단위 테스트, 협력자 목킹 | 없음(repository·client mock) | JUnit 5 + Mockito |
| `repository/` | 슬라이스 | DB | `@DataJpaTest` |
| `controller/` | 슬라이스 | 없음(service mock) | `@WebMvcTest` |
| `client/` (AI 호출) | 어댑터 | 없음(실 AI 미호출) | `MockRestServiceServer` |

- 예: JWT 만료·refresh 회전 규칙, 코스 소유자 권한 검증, 성향 프로필 유효성 →
  도메인/서비스 단위 테스트에서 시간·DB·네트워크 없이 검증.
- 전 계층 통합이 꼭 필요할 때만 `@SpringBootTest`.
- 리포지토리 테스트 DB(H2 vs Testcontainers)는 첫 `@DataJpaTest` 작성 시 확정.
- 실행: `./gradlew test` (자세한 명령은 CLAUDE.md).
