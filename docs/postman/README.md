# Postman 컬렉션 — Yeolo BE

서버의 API를 손으로 찔러보기 위한 Postman 컬렉션입니다. **dev용과 prod용 두 개**가 있습니다.

> ## 세 가지 전제
>
> **① 환경(Environment) 파일은 만들지 않습니다.** 변수는 **컬렉션 변수**(Variables 탭)에 들어 있어
> 임포트만 하면 바로 동작합니다. 환경 파일은 애초에 요청을 담지 못해(`_postman_variable_scope`)
> 컬렉션과 짝을 맞춰 관리해야 하는 부담만 늘립니다.
>
> **② 컬렉션은 환경별로 하나씩입니다.** `dev`와 `main`은 배포 시점이 달라 **API 계약이 서로 다를 수
> 있습니다** — dev가 앞서 나간 기간에는 dev 컬렉션의 요청이 prod에 없습니다. 한 파일에 `baseUrl`만
> 바꿔 쓰면 그 차이가 드러나지 않고 "prod에서 왜 404가 나지?"가 됩니다. 그래서 **파일을 나누되,
> prod 파일은 dev 파일에서 생성**합니다(아래 10절) — 손으로 두 벌을 맞추지 않기 위해서입니다.
>
> **③ 대상은 배포된 서버입니다.** 로컬(`localhost:8080`)을 대상으로는 테스트하지 않습니다.
> 그래서 아래 2절(서버 띄우기)·6절(stub provider)은 **평소에는 쓰지 않는 참고용**입니다.

| 파일 | 용도 |
| :--- | :--- |
| `Yeolo-BE-Dev.postman_collection.json` | **dev 대상.** `baseUrl = https://api-dev.yeolo.app`. 여기가 원본이며, 새 엔드포인트는 이 파일에 추가합니다 |
| `Yeolo-BE-Prod.postman_collection.json` | **prod 대상.** `baseUrl = https://api.yeolo.app`. dev 파일에서 **생성**되므로 직접 고치지 않습니다(10절) |
| `make-prod-collection.py` | 위 prod 파일 생성기. 릴리스(dev→main) 때 돌립니다 |
| `test-account.sql` | dev DB에 조회용 계정 행 하나를 넣는 SQL. 로그인 토큰은 나오지 않습니다(아래 주의) |

> ## ⚠️ prod 컬렉션을 쓸 때
>
> **실사용자 데이터를 건드립니다.** 쓰기 API(프로필 수정·MBTI·동의 저장·코스 생성)는 즉시 영구
> 반영됩니다. 파괴적 요청 둘(회원탈퇴·코스 삭제)은 prod 컬렉션에서 **기본 차단**돼 있습니다 —
> 컬렉션 변수 `allowDestructive`가 `yes`가 아니면 pre-request 스크립트가 **세 겹으로** 막습니다 —
> 요청 목적지를 도달 불가 주소(`blocked.invalid`)로 바꾸고, 러너에서는 `skipRequest()`로
> 건너뛰며, 마지막에 오류를 던져 이유를 보여줍니다. 스크립트 중단이 전송을 막지 못하는 Postman
> 버전에서도 요청이 prod 에 닿지 않습니다. 정말 필요할 때만 `yes`로 바꾸고 **끝나면 반드시
> `no`로 되돌리세요.**
>
> 가드는 실수를 막는 장치일 뿐 권한 통제가 아닙니다. 스크립트를 지우거나 `yes`로 둔 채 잊으면
> 그대로 나갑니다.

### 시크릿은 파일에 넣지 않습니다

`internalApiKey` · `jwtSecret` 은 **빈 값으로 커밋**됩니다. Postman 에서 채울 때는 반드시
**`Current value` 칸에만** 입력하세요 — `Initial value` 에 넣으면 export 시 파일에 박혀 커밋됩니다.
`baseUrl` · `googleClientId` · `googleRedirectUri` 는 비밀이 아니라 값이 채워진 채로 관리합니다
(client_id 는 어차피 브라우저 주소창까지 노출되는 값입니다).

> `.gitignore` 에 `docs` 가 있어 이 파일을 커밋하려면 `git add -f` 가 필요합니다.

> **파일은 위 표의 것들로 유지합니다.** 로컬을 찔러야 할 일이 생기더라도 새 컬렉션이나 환경을
> 만들지 말고 dev 컬렉션의 `baseUrl` 만 `http://localhost:8080` 으로 바꿔 쓰고 되돌리세요.
> 환경별 컬렉션을 늘리는 것은 **배포 대상이 실제로 다른 계약을 서빙할 때**만입니다(dev·prod).

## 1. 임포트

Postman → **Import** → `Yeolo-BE-Dev.postman_collection.json` 드래그. 환경 선택은 필요 없습니다
(변수가 컬렉션 안에 있습니다). 같은 이름의 컬렉션이 이미 있으면 Postman 이 교체할지 물어봅니다.

prod 도 쓰려면 `Yeolo-BE-Prod.postman_collection.json` 을 같은 방식으로 임포트합니다. 사이드바에
**`Yeolo BE`(dev)** 와 **`Yeolo BE (prod)`** 두 개가 뜹니다 — 이름으로 구분하세요. 두 컬렉션의
`accessToken` 은 서로 다른 변수라 **환경별로 각각 로그인**해야 합니다(계정 DB가 다릅니다).

## 2. 서버 띄우기 (로컬을 쓸 때만 — 평소엔 불필요)

평소에는 dev 서버를 그대로 부르므로 이 절을 건너뜁니다. 로컬을 대상으로 바꿨을 때만:

```bash
./gradlew bootRun
```

`00. Health`의 `GET /actuator/health`가 `{"status":"UP"}`이면 준비 완료.

## 3. 토큰 얻기 — 로그인만 씁니다 (`tokenMode = login`)

> **`tokenMode = mint`는 쓰지 않기로 했습니다.** 컬렉션에 기능은 남아 있지만 사용하지 않습니다.
> 이유는 mint가 **실제 인증 경로를 건너뛰기** 때문입니다 — `jwtSecret`으로 토큰을 직접 서명하므로
> 로그인·토큰 발급·Refresh Token 저장이 한 번도 실행되지 않고, 그래서 `POST /api/auth/refresh`나
> 로그아웃처럼 **DB의 세션 행에 의존하는 API를 테스트할 수 없습니다.** 서버 키가 바뀌면 원인이
> 불분명한 401만 남는 것도 겪었습니다. 앞으로 `tokenMode`는 항상 `login`으로 둡니다.

`01. Auth`의 Google/Apple 로그인을 실제로 호출합니다. 성공하면 테스트 스크립트가
`accessToken` · `refreshToken` · `userId`를 환경변수에 자동 저장하고, 이후 모든 요청이
컬렉션 레벨 Bearer Auth로 그 토큰을 씁니다.

- `googleAuthCode`에 넣을 값은 FE OAuth 리다이렉트에서 받은 **1회용 인가 코드**입니다.
  한 번 쓰면 소모되므로 재실행할 때마다 새로 받아야 합니다. 받는 절차는 7절.
- 서버에 `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` / `GOOGLE_REDIRECT_URI`가 설정돼 있어야 합니다.
- Access Token은 **1시간**입니다. 만료되면 로그인을 반복하지 말고 `01. Auth`의
  `POST /api/auth/refresh`를 보내세요(Refresh Token 2주).

`jwtSecret` 환경변수는 이제 **진단용으로만** 남겨 둡니다(7절의 401 판별). 요청 서명에는 쓰지
않습니다.

## 4. 실행 순서

폴더 번호 순서대로 하면 ID를 손으로 복사할 일이 없습니다.

```
00. Health          서버 확인
01. Auth            로그인 → accessToken·refreshToken·userId 자동 저장
03. User            MBTI 등록 → 사진 분석 동의     ← 04·05의 전제조건
04. Taste Profile   성향 분석(SSE) → 조회
05. Course          코스 생성(SSE) → 목록 → 상세   ← courseId·placeId 자동 저장
06. Place           장소 상세                      ← 저장된 placeId 사용
08. Share           공유 링크 생성 → 조회 → 수락    ← 저장된 courseId 사용, shareToken 자동 저장
```

- `03`의 **사진 분석 동의**를 안 하면 `04`의 성향 분석이 403으로 막힙니다.
- `05` 코스 생성은 MBTI 또는 성향 프로필 중 하나만 있어도 됩니다.
- `08`은 `05`가 저장한 `courseId`가 있어야 합니다. `05` 끝의 코스 삭제를 이미 돌렸다면 코스를
  다시 만들어야 합니다.
- `02. Location`은 인증이 필요 없어 아무 때나 호출 가능합니다.

### `08. Share` — 친구 초대 공유 링크 (API-SHARE-1/2/3)

| # | 요청 | 메모 |
| :-- | :--- | :--- |
| 1 | `POST {{baseUrl}}/api/courses/{{courseId}}/share-links` | 소유자만 가능. Test 스크립트가 응답의 `data.shareToken`을 환경변수 `shareToken`에 저장 |
| 2 | `GET {{baseUrl}}/api/share-links/{{shareToken}}` | **인증 불필요.** 이 요청만 Auth 를 `No Auth`로 두세요(컬렉션 상속을 끊음) — 토큰을 붙여도 200이지만, 비로그인 흐름을 그대로 재현하려면 끄는 편이 낫습니다 |
| 3 | `POST {{baseUrl}}/api/share-links/{{shareToken}}/accept` | 인증 필요. **자기 코스는 400입니다** — 아래 참고 |

세 요청 모두 **Body 없음**(명세상 `{}`)이라 `Content-Type`을 붙이지 않아도 됩니다.

#### 수락(3번)을 성공시키려면 다른 사용자가 필요합니다

공유는 "남에게 보내는" 기능이라, 링크를 만든 계정 그대로 수락하면 명세대로 **400
`수락할 수 없는 공유 링크입니다.`** 가 납니다(자기 자신의 코스). 정상 동작이며, 200을 보려면
수락만 다른 사용자로 보내야 합니다.

로그인 방식에서는 **다른 계정으로 한 번 더 로그인**해서 받은 `accessToken`으로 3번만 보내면
됩니다. 두 계정을 오갈 일이 많으면 로그인 응답의 토큰을 각각 메모해 두고 `accessToken`만
바꿔 끼우세요.

```sql
-- 수락용으로 쓸 다른 사용자 하나 꺼내기 (코스 소유자가 아닌 계정)
-- dev DB 접속은 SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md)
SELECT id, email, display_name FROM users WHERE id <> '<코스 소유자 userId>' LIMIT 5;
```

수락에 성공하면 그 사용자의 `GET /api/courses`(05 폴더) 목록에 공유받은 코스가 함께 나옵니다.
같은 사용자로 3번을 한 번 더 보내면 **400**(이미 수락) 입니다 — 중복 추가되지 않습니다.

#### 실패 응답이 명세대로 나오는지 보기

| 만들어 보는 법 | 기대 |
| :--- | :--- |
| `shareToken`을 아무 문자열로 바꾸고 2번 Send | `404 유효하지 않은 공유 링크입니다.` |
| 링크를 만든 뒤 `05`에서 그 코스를 삭제하고 2번 Send | `404 여행 코스를 찾을 수 없습니다.` (링크가 회수돼도 "코스 없음"이 먼저) |
| (410 만료는 dev에서 재현하기 어렵습니다 — TTL이 7일이라 `SHARE_LINK_TTL`을 짧게 바꿔 재배포해야 합니다. 만료·회수 규칙 자체는 `SavedShareLinkTest`·`ShareLinkServiceTest`가 검증합니다) | `410 만료되었거나 회수된 공유 링크입니다.` |
| 남의 코스 `courseId`로 1번 Send | `403 해당 여행 코스를 공유할 권한이 없습니다.` |

#### 링크는 매번 새로 발급됩니다

1번을 두 번 누르면 `shareToken`이 서로 다르게 나오고 **둘 다 유효합니다.** 서버가 토큰을
해시로만 저장해(Refresh Token과 같은 정책) 기존 링크의 평문을 돌려줄 수 없기 때문입니다.
`shareUrl`의 도메인(`share-link.base-url`, 기본 `https://yeolo.app/share`)은 아직 FE 딥링크
도메인이 확정되지 않은 자리표시자라, 브라우저로 열어도 페이지가 없습니다 — 정상입니다.

### 폴더를 통째로 실행할 때 조심할 것 — 파괴적 요청 2개

Postman **Run folder**는 폴더 안 요청을 위에서부터 전부 보냅니다. 아래 둘은 각 폴더 **맨 끝**에
있으니, 데이터를 남겨야 하면 실행 전에 체크를 끄세요.

| 요청 | 영향 |
| :--- | :--- |
| `05` 끝의 `DELETE /api/courses/:courseId` | 방금 목록 조회가 저장한 코스를 지웁니다 (`06. Place`가 쓸 `placeId`는 이미 저장돼 있어 조회 자체는 됩니다). **`08. Share`가 쓸 코스도 함께 사라지고**, 그 코스로 발급한 공유 링크는 회수됩니다 |
| `03` 끝의 `DELETE /api/users/me` | **되돌릴 수 없는 회원탈퇴.** 토큰이 무효가 되고 이후 요청이 전부 401 |

`POST /api/auth/refresh`(`01` 폴더)는 저장된 `refreshToken`으로 토큰을 재발급받아 환경변수를
갱신합니다. **로그인으로 받은 Refresh Token에서만 동작합니다** — 서버가 DB에 저장된 해시와
대조하기 때문에, 직접 서명해 만든 토큰은 서명이 맞아도 거부됩니다(`RefreshTokenService.matches`).

## 5. SSE 엔드포인트

`POST /api/courses`와 `POST /api/users/me/taste-profile/analysis`는 `text/event-stream`입니다.
Postman 버전에 따라 이벤트가 실시간으로 흐르지 않고 **스트림이 끝난 뒤 전체 본문이 한 번에**
보일 수 있습니다. 단계별 진행을 눈으로 확인하려면 curl을 쓰세요 (`-N` = 버퍼 끄기):

```bash
TOKEN=<accessToken>
curl -N -X POST http://localhost:8080/api/courses \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"destinationCountry":"일본","destinationCity":"도쿄","startDate":"2026-09-01","totalDays":3,"budgetType":"moderate"}'
```

또한 LLM 분석이 실측 80초대라 Postman 기본 요청 타임아웃에 걸릴 수 있습니다 —
**Settings → General → Request timeout**을 `0`(무제한)으로 두세요.

## 6. 외부 의존 없이 돌려보기 (로컬을 쓸 때만 — 평소엔 불필요)

dev 서버는 provider가 이미 설정돼 있어 이 절은 로컬로 바꿨을 때만 씁니다.
AI 서버·지도 API 없이 흐름만 확인하려면 provider를 stub으로 띄웁니다.

```bash
AI_COURSE_PROVIDER=stub \
GEOCODE_PROVIDER=stub \
PLACE_PROVIDER=stub \
PROFILE_IMAGE_PROVIDER=stub \
./gradlew bootRun
```

stub은 외부 호출 없이 합성 결과를 돌려주므로, 프로필 이미지 URL은 열리지 않고 장소 좌표는
실제 위치가 아닙니다 — 정상입니다.

## 7. dev 서버 대상으로 테스트

dev 컬렉션은 `baseUrl = https://api-dev.yeolo.app` 로 이미 채워져 있어, 임포트하면 요청 전부가
dev를 가리킵니다. prod 컬렉션은 같은 자리에 `https://api.yeolo.app` 이 들어 있습니다.

### 환경 변수 현재 상태

값은 컬렉션의 **Variables** 탭에서 봅니다. 시크릿 두 개는 빈 값으로 커밋되므로 각자 채웁니다.

| 변수 | 채워진 값 |
| :--- | :--- |
| `tokenMode` | `login` — 로그인으로 받은 `accessToken`을 씁니다. `mint`는 사용하지 않습니다(3절) |
| `jwtSecret` | dev `was-secrets`의 `JWT_SECRET` |
| `userId` | 로그인 성공 시 자동 저장 |
| `accessToken` | 로그인 성공 시 자동 저장 (1시간). curl에 그대로 복붙용 |
| `refreshToken` | 로그인 성공 시 자동 저장 (2주). **로그인으로만 생깁니다** — 직접 서명해 넣을 수 없습니다 |
| `internalApiKey` | BE↔AI 공유 키 — 8절 |
| `shareToken` | 비어 있음. `08. Share`의 링크 생성 요청이 자동으로 채웁니다 |

`refreshToken`은 서명만 맞으면 되는 게 아니라 **DB에 저장된 sha256 해시와 대조**됩니다
(`RefreshTokenService.matches` — 로그아웃·회전된 옛 토큰을 막으려는 의도된 설계). 그래서 로그인을
거치지 않고 만든 토큰은 서명이 맞아도 통하지 않습니다 — `tokenMode=mint`를 버린 이유이기도 합니다(3절).

> **주의 — 한 번 쓰면 회전됩니다.** `POST /api/auth/refresh`는 성공 시 새 토큰 쌍을 발급하고
> 옛 토큰을 무효화합니다(사용자당 1개). 그 요청의 test 스크립트가 환경변수를 자동 갱신하므로
> Postman 안에서는 이어지지만, **파일에 적힌 값은 그 순간부터 무효**입니다. 만료(2주)됐거나
> 무효가 되면 `01. Auth`의 Google 로그인을 한 번 하면 다시 채워집니다.

#### 401이 날 때 — 키 문제인지 계정 문제인지 한 번에 가른다

로그인 방식에서 401이면 **Access Token 만료(1시간)가 가장 흔합니다** — 먼저
`POST /api/auth/refresh`를 보내 갱신해 보세요. 그래도 401이면 남는 후보는 둘이고,
**`sub`만 바꾼 토큰을 하나 더 보내면 즉시 갈립니다.**

```bash
# 지금 jwtSecret 으로, 존재하지 않는 임의 UUID 를 sub 로 하는 토큰을 만들어 호출
SECRET='<jwtSecret>' python3 -c "
import base64,hmac,hashlib,json,os,time,uuid
b=lambda x: base64.urlsafe_b64encode(x).rstrip(b'=').decode()
s=os.environ['SECRET'].encode(); n=int(time.time())
si=b(json.dumps({'alg':'HS256'},separators=(',',':')).encode())+'.'+b(json.dumps(
   {'sub':str(uuid.uuid4()),'type':'access','iat':n,'exp':n+600},separators=(',',':')).encode())
print(si+'.'+b(hmac.new(s,si.encode(),hashlib.sha256).digest()))"
```

| 임의 UUID 토큰의 결과 | 진단 |
| :--- | :--- |
| **200** | 키는 정확하다. 문제는 **그 `userId` 계정**이다 — 거의 확실히 **탈퇴 처리된 계정**이다. 사용자별로 401을 내는 경로는 `JwtAuthenticationFilter`의 탈퇴자 차단뿐이다(API-USER-2). `03` 폴더 끝의 `DELETE /api/users/me`를 돌렸다면 이렇게 된다 |
| **401** | `jwtSecret`이 서버의 `JWT_SECRET`과 다르다. 아래로 다시 받는다 |

> **저장된 `refreshToken`의 서명으로는 키를 판정하지 마세요.** 그 토큰은 서버 키가 교체되기
> **전에** 발급됐을 수 있어, 키가 정확해도 서명이 안 맞습니다. 실제로 이 착각으로 멀쩡한 키를
> 의심한 적이 있습니다 — 판정은 위의 "지금 서명한 토큰"으로 하세요.

탈퇴 계정이었다면 `userId`만 살아 있는 값으로 바꾸면 됩니다. `users` 행이 아예 없는 **새 UUID를
써도 대부분 동작합니다** — 탈퇴자가 아니라 인증을 통과하고, `courses`·`user_preferences` 등이
`users`에 FK를 걸지 않기 때문입니다. 다만 사용자 행을 직접 읽는 `PATCH /api/users/me/profile` ·
`DELETE /api/users/me`는 404가 나고, 공유 미리보기의 `inviter`는 `null`로 나옵니다.

#### 새로 받은 사람이 채우는 법 (커밋되지 않으므로 각자 1회)

```bash
kubectl -n app-dev get secret was-secrets -o jsonpath='{.data.JWT_SECRET}'       | base64 -d; echo  # jwtSecret
kubectl -n app     get secret was-secrets -o jsonpath='{.data.INTERNAL_API_KEY}' | base64 -d; echo  # internalApiKey
```

`accessToken`·`refreshToken`·`userId`는 **로그인 한 번이면 자동으로 채워집니다**(아래 절차).
`jwtSecret`은 진단용으로만 쓰므로 급하지 않습니다. Postman에 직접 입력할 땐 **`Current value` 칸에만** 넣으세요 —
`Initial value`에 넣으면 export 시 파일에 박힙니다.

### 토큰 — Postman에서 Google 로그인하기

BE의 `POST /api/auth/google`은 **인가 코드(code)** 를 받습니다. 인가 코드는 브라우저 OAuth
리다이렉트에서만 나오므로 브라우저를 한 번 거쳐야 하지만, 나머지는 요청이 알아서 합니다.

**Send 두 번이면 끝납니다.**

#### 1. 그냥 Send → Console에서 로그인 URL 복사

`01. Auth / POST /api/auth/google`을 아무것도 채우지 않고 Send하면(400이 납니다, 정상)
Postman Console(`⌘⌥C`)에 로그인 URL이 찍힙니다.

```
[1] 이 URL 을 브라우저(시크릿 창)에서 열고 로그인하세요.
https://accounts.google.com/o/oauth2/v2/auth?client_id=...&redirect_uri=...&response_type=code&scope=openid%20email%20profile&prompt=consent
```

환경변수 `googleClientId` · `googleRedirectUri`로 조립되므로 **URL을 손으로 만들 필요가
없습니다.** `Yeolo-Dev`에는 dev 서버가 실제로 쓰는 값이 채워져 있습니다.

#### 2. 브라우저에서 로그인 → 주소창을 통째로 복사

로그인·동의하면 주소창이 이렇게 바뀝니다. **페이지가 404로 보여도 상관없습니다.**

```
https://d2hs98q0chb81r.cloudfront.net/?code=4%2F0AVGzR1B...&scope=openid+email+profile
```

이 **주소창 전체를 그대로** `googleAuthCode` 환경변수에 붙여넣으세요. Pre-request 스크립트가
`code`를 뽑아 URL 디코딩합니다 — 예전에 매번 걸리던 `%2F` → `/` 변환을 직접 할 필요가 없습니다.

| 붙여넣어도 되는 형태 | |
| :--- | :--- |
| 주소창 전체 | `https://.../?code=4%2F0A...&scope=...` |
| 인코딩된 코드 | `4%2F0A...` |
| 날 코드 | `4/0A...` |

#### 3. 다시 Send → 200

성공하면 Test 스크립트가 `accessToken`·`refreshToken`·`userId`를 환경변수에 저장하고, 이후
인증이 필요한 요청이 전부 열립니다.

```json
{ "status": 200, "message": "로그인 성공", "data": { "user": {...}, "doOnboarding": true, "accessToken": "...", "refreshToken": "..." } }
```

이후 순서는 4절과 같습니다 — `03` 폴더로 MBTI·사진 분석 동의를 넣고 `04`, `05`로 넘어갑니다.

> **인가 코드는 1회용입니다.** Send를 두 번 누르면 두 번째는 반드시 실패하니 1단계부터 다시
> 받으세요. Access Token(1시간)이 만료됐을 때는 로그인을 반복하는 대신 `01. Auth`의
> `POST /api/auth/refresh`를 보내면 갱신됩니다(refreshToken 2주).

#### 서버 설정이 바뀌었을 때

`googleClientId`는 서버가 코드를 교환할 때 쓰는 값과 같아야 합니다. 아래로 확인해서 환경변수를
맞추세요 — **한 줄로** 실행해야 합니다(`\` 뒤에 공백이 있으면 명령이 쪼개집니다).

```bash
kubectl -n app-dev get secret was-secrets -o go-template='{{.data.GOOGLE_CLIENT_ID | base64decode}}{{"\n"}}'
```

`googleRedirectUri`는 **Google Cloud Console의 승인된 리디렉션 URI에 등록된 값**이어야 합니다.
FE가 쓰는 `https://d2hs98q0chb81r.cloudfront.net`은 이미 등록돼 있습니다. `scope`는
`openid email profile`이면 충분합니다 — BE가 userinfo에서 읽는 값이 `sub`·`email`·`name`·`picture`
뿐입니다(`GoogleUserInfo`).

#### 실패할 때 — 400 "인가 코드가 유효하지 않습니다."

이 메시지는 **두 군데**에서 나오고 응답만으로는 구분되지 않습니다.

| 발생 지점 | 조건 | 서버 로그 |
| :--- | :--- | :--- |
| Bean Validation | `code`가 비었거나 공백 | 없음 |
| `GoogleOAuthClient` | Google이 4xx로 거절(`invalid_grant` 등) | `Google token exchange rejected request: 400 ...` |

```bash
kubectl -n app-dev logs -l app=<was> --tail=300 | grep -i "Google token exchange"
```

로그가 없으면 Google까지 가지도 않은 것이므로 `googleAuthCode`가 비었는지부터 보세요. 로그가
있다면 원인은 넷 중 하나입니다.

| 원인 | 확인 |
| :--- | :--- |
| 코드를 이미 썼음 | **1회용입니다.** Send를 두 번 눌렀으면 두 번째는 무조건 실패 — 1단계부터 다시 |
| 코드 만료 | 발급 후 수 분. 받자마자 보내세요 |
| URL 디코딩 안 함 | `%2F`가 남아 있는지 확인 (2단계) |
| `redirectUri` 불일치 | 1단계 URL의 `redirect_uri`와 Postman `googleRedirectUri`가 **문자 단위로** 같은지 |

> dev의 `was-secrets`에는 `GOOGLE_REDIRECT_URI`가 **설정돼 있지 않습니다**(`GOOGLE_CLIENT_ID`와
> `GOOGLE_CLIENT_SECRET`만 있음). 서버는 요청 본문의 `redirectUri`를 그대로 쓰고, 본문이 비면
> 빈 문자열로 폴백합니다(`GoogleOAuthClient:44`). `redirectUri`가 `@NotBlank` 필수라 실제로 폴백에
> 닿지는 않지만, **redirect_uri의 유일한 출처가 클라이언트**라는 뜻이므로 값을 정확히 보내야 합니다.

`client_secret`이 틀린 경우는 400이 아니라 **401 "Google 인증에 실패했습니다."**로 나옵니다.

> **iOS 앱에서 받은 코드는 쓸 수 없습니다.** Google 프로젝트에 웹 클라이언트와 iOS 클라이언트가
> 따로 있는데, iOS 클라이언트는 client secret이 없어 서버 측 코드 교환 대상이 아닙니다. 반드시
> 위 1단계의 웹 플로우로 받은 코드를 쓰세요.

### 대안 — FE에서 받은 accessToken 붙여넣기

위 과정이 번거로우면, FE에서 Google 로그인 후 받은 `accessToken`을 환경의 `accessToken`에
붙여넣기만 해도 됩니다. `userId`는 서버가 토큰에서 꺼내 쓰므로 채울 필요 없습니다.

### ⚠️ dev 와 prod 가 같은 클러스터·같은 ALB 를 공유한다

`yeolo-dev` EKS 클러스터 하나에 두 환경이 있고, ALB 리스너가 CloudFront 가 붙이는 커스텀 헤더
`X-Yeolo-Env` 로 갈라 보냅니다. **네임스페이스 이름에 `dev` 가 안 붙은 쪽이 prod 라 헷갈립니다.**

| | dev | prod |
| :--- | :--- | :--- |
| CloudFront | `d1eicq4gephyts.cloudfront.net` | `d2hs98q0chb81r.cloudfront.net` |
| k8s 네임스페이스 | `app-dev` | **`app`** |
| DB (같은 RDS) | `yeolo_dev` | `yeolo` |
| 배포 브랜치 | `dev` | `main` |

- `kubectl` 은 **`-n app-dev`** 가 dev 입니다. `-n app` 은 prod 입니다.
- ALB 주소로 직접 호출하면 헤더가 없어 기본 규칙 → **prod** 로 갑니다. 오리진을 직접 찌를 때는
  `-H 'X-Yeolo-Env: dev'` 를 반드시 붙이세요.

### 로컬과 달라지는 점

**(1) provider를 마음대로 못 바꿉니다.** 로컬에서는 `AI_COURSE_PROVIDER=stub` 같은 걸 실행할 때
붙일 수 있지만, dev는 배포된 `was-secrets` 값을 따릅니다. 현재 값 확인:

```bash
kubectl -n app-dev get secret was-secrets \
  -o go-template='{{range $k,$v := .data}}{{$k}}={{$v | base64decode}}{{"\n"}}{{end}}'
```

`AI_COURSE_PROVIDER`가 `stub`이면 코스 생성 결과는 합성 데이터입니다 — API 계약 확인에는 쓸 수
있어도 AI 품질 검증에는 못 씁니다.

**(2) 쓰기 API는 실제 dev DB를 바꿉니다.** 프로필 수정·MBTI 등록·동의 저장·코스 생성은 모두
영구 반영됩니다. FE가 함께 보고 있는 데이터라면 본인 계정으로만 쓰세요. **코스 삭제와 회원탈퇴는
dev에서 되돌릴 수 없습니다** — 4절의 경고를 dev에서는 더 무겁게 받아들이세요.

**(3) 앞단이 CloudFront입니다.** 캐시는 꺼져 있고(`Managed-CachingDisabled`) 헤더는 전부
전달됩니다(`Managed-AllViewerExceptHostHeader`) — 확인했습니다. 남은 미지수는 SSE 하나입니다.

앱은 15초 간격 heartbeat(`SseHeartbeat`)로 프록시 idle 차단을 막고 있어 ALB 기본 60초는 넘깁니다.
CloudFront 오리진 응답 타임아웃도 60초로 잡혀 있어 heartbeat 간격보다 길지만, 코스 생성(80초대)이
실제로 CloudFront를 통과하는지는 **한 번 돌려봐야 압니다.** 504가 나면 앱이 아니라 배포 구성
쪽이니 인프라 담당에게 넘기세요.

## 8. AI 내부 API 직접 호출 — BE인지 AI인지 가르기

`07. Internal AI` 폴더는 **BE를 거치지 않고** AI 내부 API를 직접 부릅니다. `/api/...`가
500 `"성향 분석 처리 중 오류가 발생했습니다."`로 끝날 때 원인을 한 번에 가릅니다 —
여기서 정상이면 AI는 멀쩡한 것이고, 여기서도 깨지면 BE는 결백합니다.

### 준비 (2단계)

AI 서비스는 ClusterIP 전용이라(ingress 없음) 바깥에서 못 부릅니다. 터널부터 엽니다.

```bash
kubectl -n app-dev port-forward deploy/ai 8000:8000   # dev
kubectl -n app     port-forward deploy/ai 8000:8000   # prod (읽기만)
```

그다음 환경변수 `internalApiKey`에 WAS가 실제로 쓰는 값을 넣습니다.

```bash
kubectl -n app-dev get secret was-secrets -o jsonpath='{.data.INTERNAL_API_KEY}' | base64 -d; echo
```

`aiBaseUrl`은 기본값 `http://localhost:8000` 그대로 두면 됩니다. 로컬 AI를 직접 띄웠다면
포트포워딩 없이 그대로 붙습니다.

### 읽는 법

폴더의 **"인증만 확인 (빈 items → 400 기대)"** 요청이 제일 빠릅니다. `items`가 비어 있어
AI는 인증을 통과한 뒤 400을 돌려주므로, **400이 성공 신호**입니다.

| 응답 | 뜻 |
| :--- | :--- |
| `400 분석 가능한 전처리 메타데이터가 부족합니다.` | 키 정상 (인증 통과) |
| `401 내부 인증 실패` | `internalApiKey`가 AI가 기대하는 값과 다름 |
| 연결 거부 | 포트포워딩이 안 떠 있음 |

**단, 이 400은 "인증 통과" 신호로만 쓰세요 — 페이로드가 유효한지는 알 수 없습니다.** AI의
`RequestValidationError` 핸들러(`app/main.py`)가 `/taste-profile` 경로면 **스키마 검증 실패도
똑같은 문구의 400**으로 바꿔 내보내기 때문입니다. 즉 `items`를 아무리 채워 보내도 필드 하나가
어긋나면 이 요청과 구분되지 않는 응답이 옵니다. 그래서 **"유효 항목 1건 (200 기대)"** 요청을
같이 둡니다 — 이쪽이 200 + `complete` 이벤트면 스키마까지 정상입니다.

어느 필드가 걸렸는지는 AI 로그에 그대로 남습니다:

```bash
kubectl -n app-dev logs deploy/ai --since=1h | grep "Validation Error"
# ... [{'type': 'string_type', 'loc': ('body','items',1,'location','city'), 'input': None}, ...]
```

> **`location`은 `placeTypes`를 뺀 전 필드가 AI 쪽에서 non-nullable입니다.** 한 항목이라도
> `null`이면 요청 **전체**가 반려되므로, 좌표 한 장 때문에 수십 장의 분석이 통째로 죽습니다.
> 실제로 2026-08-18 dev에서 그렇게 터졌습니다 — Google Geocoding이 서울·부산 등 특별시·광역시에
> `locality`도 `administrative_area_level_2`도 안 내려주는 탓에 `city`가 91장 중 90장 비었습니다.
> 지금은 `GoogleReverseGeocodeClient`가 `sublocality_level_1`(구)까지 폴백하고,
> `ImageMetadataPreprocessor`가 그래도 빈 항목은 빼고 보냅니다.

취향 분석 요청의 본문은 `ImageMetadataPreprocessor`가 역지오코딩·시간맥락 파생까지 끝낸
**전처리 결과**입니다. FE가 올리는 EXIF 원본(`latitude`/`longitude`)이 아니라서 `04` 폴더와
본문 모양이 다른 게 정상입니다.

> ⚠️ `INTERNAL_API_KEY`는 **BE와 AI가 공유하는 대칭 키**입니다. JWT_SECRET·DB_URL과 달리
> 환경별로 새로 발급하면 안 됩니다 — 한쪽만 바꾸는 순간 그 환경의 BE→AI 호출이 전부 401이
> 됩니다. 실제로 2026-08-06 dev Environment를 만들면서 이 키만 새로 발급돼 dev의 취향 분석·
> 코스 생성이 전부 401로 죽은 적이 있습니다.

## 9. 컬렉션을 고쳤을 때

**고치는 대상은 언제나 dev 컬렉션입니다.** Postman에서 수정한 뒤 **Export**해서
`Yeolo-BE-Dev.postman_collection.json` 을 덮어쓰세요. 새 이름으로 만들지 않습니다.

| 파일 | 커밋 | 주의 |
| :--- | :--- | :--- |
| `Yeolo-BE-Dev.postman_collection.json` | **O (`git add -f`)** | 시크릿 변수는 빈 값으로만 커밋 |
| `Yeolo-BE-Prod.postman_collection.json` | **O (`git add -f`)** | **직접 고치지 않습니다** — 10절대로 생성 |

Postman 에서 prod 컬렉션을 만졌다면 export 하지 말고 버리세요. 다음 생성 때 덮어쓰여집니다.

## 10. prod 컬렉션 생성 — 릴리스(dev→main) 때 돌립니다

prod 컬렉션은 dev 파일에서 기계적으로 만듭니다. 두 벌을 손으로 맞추면 반드시 어긋나기 때문입니다.

```bash
python3 docs/postman/make-prod-collection.py
git add -f docs/postman/Yeolo-BE-Prod.postman_collection.json
```

스크립트가 하는 일은 여섯입니다 — `baseUrl` 을 prod 로 교체, 컬렉션 이름·`_postman_id` 를 별개로
변경(같으면 Postman 이 한 컬렉션으로 취급해 덮어씁니다), 컬렉션 설명을 prod 경고문으로 교체,
세션값·시크릿 변수 비우기, 설명문의 dev 전용 문구 치환(`-n app-dev`→`-n app` 등), 파괴적 요청
둘에 `allowDestructive` 가드 삽입.

**돌리는 시점은 dev→main 릴리스 직후입니다.** dev 에만 있는 기능이 prod 컬렉션에 들어가면
"있는데 404" 가 되어 파일을 나눈 의미가 없어집니다. dev 컬렉션만 고치는 평소 작업에서는
돌리지 않습니다.

### prod 에서 값을 따로 확인해야 하는 변수

생성기는 dev 값을 그대로 가져오므로, prod 서버 설정이 다르면 아래 둘은 손으로 맞춰야 합니다.

**생성물(JSON)을 손으로 고치지 마세요** — 다음 재생성에서 조용히 되돌아갑니다. 값이 달라야 하면
`make-prod-collection.py` 의 `PROD_OVERRIDES` 에 적습니다.

| 변수 | 확인 |
| :--- | :--- |
| `googleClientId` | prod 값이 dev와 다르면 `PROD_OVERRIDES` 에 넣습니다 (확인 명령은 아래) |
| `googleRedirectUri` | Google Cloud Console 의 **승인된 리디렉션 URI** 에 등록된 값이어야 합니다. 현재 값은 prod CloudFront 도메인인데, CloudFront 제거(Cloudflare 전환) 후에는 콘솔 등록값과 함께 바꿔야 합니다 |

```bash
# prod / dev 의 GOOGLE_CLIENT_ID 비교 (한 줄씩 실행)
kubectl -n app     get secret was-secrets -o go-template='{{.data.GOOGLE_CLIENT_ID | base64decode}}{{"\n"}}'
kubectl -n app-dev get secret was-secrets -o go-template='{{.data.GOOGLE_CLIENT_ID | base64decode}}{{"\n"}}'
```

`internalApiKey` · `jwtSecret` 은 빈 값으로 커밋되며 prod 용은 `-n app` 에서 각자 채웁니다(8절).
