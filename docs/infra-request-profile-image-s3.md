# 인프라 요청 — 프로필 이미지 저장소(S3 + CloudFront + IRSA)

> **요청자:** BE (peter001019) · **관련:** [TSK-25 / BE #51](https://github.com/Software-Maestro-GTA/Yeolo-BE/issues/51), 명세 `API-USER-1`
> **상태:** BE 구현 완료(머지 대기). 아래 리소스가 없으면 **dev·prod 배포가 실패한다** (§4 참고).

## 0. 한 줄 요약

사용자 프로필 이미지 업로드(`PATCH /api/users/me/profile`)를 위해 **dev/prod 각각**
S3 버킷 + CloudFront 배포 + WAS 파드에 붙는 IRSA 권한이 필요하다. BE는 액세스 키를 쓰지 않고
SDK 기본 자격증명 체인(= IRSA)으로만 접근한다.

---

## 1. 만들어야 할 것

### 1-1. S3 버킷 (dev/prod 각 1개)

| 항목 | 값 |
| :--- | :--- |
| 리전 | EKS 클러스터와 동일 (`AWS_REGION`) |
| Block Public Access | **전부 ON** (버킷을 직접 공개하지 않는다) |
| 버킷 정책 | CloudFront **OAC**(Origin Access Control)에만 `s3:GetObject` 허용 |
| 버전 관리 | 불필요 (키가 업로드마다 유일해 버전이 생기지 않음) |
| 수명주기 규칙 | **넣지 말 것** — 사유는 §3 |
| 암호화 | SSE-S3 기본값이면 충분 (KMS 쓸 경우 IRSA 정책에 `kms:GenerateDataKey` 추가 필요) |

앱이 쓰는 오브젝트 키 형태:

```
profile-images/{userId}/{uuid}.{jpg|png|webp}
   └ 접두사는 PROFILE_IMAGE_KEY_PREFIX 로 바꿀 수 있음(기본 profile-images)
```

- 객체 최대 크기: **5MB** (앱이 강제, `profile-image.max-bytes`)
- 허용 형식: JPEG / PNG / WebP — 앱이 매직 바이트로 판정해 `Content-Type`을 직접 실어 PutObject 한다.
  **CloudFront가 원본의 Content-Type을 그대로 전달**하기만 하면 된다(덮어쓰지 말 것).

### 1-2. CloudFront 배포 (dev/prod 각 1개)

- Origin: 위 S3 버킷, **OAC** 사용
- Viewer protocol: HTTPS only
- 캐시: 기본 정책으로 충분. 같은 URL이 다른 이미지를 가리키는 일이 없다(업로드마다 키가 유일).
  따라서 **긴 TTL을 써도 안전**하고, 이미지 교체 시 무효화(invalidation)가 필요 없다.
- CORS: `<img>` 태그로만 표시하면 불필요. FE가 canvas/fetch로 읽어야 한다면 그때 요청하겠다.

### 1-3. IRSA — WAS 파드의 S3 쓰기 권한

WAS Deployment가 쓰는 ServiceAccount에 IAM Role을 연결하고, 아래 정책을 붙여 달라.
**필요한 건 PutObject 하나뿐이다** (앱은 삭제·목록 조회를 하지 않는다).

- 네임스페이스: dev = `app-dev`, prod = `vars.K8S_NAMESPACE` 값
- Deployment / ServiceAccount: `vars.DEPLOYMENT_NAME` 기준(현행 매니페스트 확인 필요)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::<버킷명>/profile-images/*"
    }
  ]
}
```

---

## 2. BE에 넘겨줄 값

아래 3개를 알려주면 BE가 GitHub Environments(dev/prod) Variables에 넣는다.
(앱 설정 키는 이미 `application.properties`에 있고, 배포 파이프라인도 전달하도록 수정돼 있다.)

| GitHub Variable | 값 | 예시 |
| :--- | :--- | :--- |
| `PROFILE_IMAGE_S3_BUCKET` | 버킷명 | `yeolo-profile-images-dev` |
| `PROFILE_IMAGE_BASE_URL` | CloudFront 도메인 (**끝 슬래시 없이**, 스킴 포함) | `https://dxxxx.cloudfront.net` |
| `PROFILE_IMAGE_S3_REGION` | 버킷 리전 (비우면 `AWS_REGION` 사용) | `ap-northeast-2` |

BE가 추가로 설정할 값: `PROFILE_IMAGE_PROVIDER=s3`

> 앱이 반환하는 URL = `{PROFILE_IMAGE_BASE_URL}/{오브젝트 키}` 이므로,
> base URL과 버킷/CloudFront origin path가 어긋나면 404가 난다.

---

## 3. 수명주기 규칙을 넣지 말아야 하는 이유

사용자가 이미지를 교체하면 옛 객체가 참조되지 않은 채 남는다. 이걸 "오래된 객체 만료" 규칙으로
지우려는 게 자연스러워 보이지만, **그러면 안 된다**: 한 번 올리고 계속 쓰는 현재 프로필 이미지도
시간이 지나면 같은 조건에 걸려 사라진다(사용자 화면에서 이미지가 깨진다).

고아 객체만 지우려면 `users.profile_image_url`과 버킷 키를 대조하는 정리 작업이 필요하고,
이건 인프라가 아니라 BE가 만들 배치다. 지금은 **방치**하기로 했다 — 사용자당 수 KB~MB 수준이라
비용이 문제가 되는 시점이 오면 그때 BE가 정리 작업을 만든다.

---

## 4. 이게 없으면 벌어지는 일 (배포 차단)

`.github/workflows/deploy.yml`의 **필수 변수 가드**에 `PROFILE_IMAGE_PROVIDER`,
`PROFILE_IMAGE_S3_BUCKET`, `PROFILE_IMAGE_BASE_URL`을 추가했다. 세 값이 비어 있으면
dev·prod 배포가 `was-secrets` 동기화 단계에서 즉시 중단된다.

일부러 그렇게 했다. 앱 기본값이 `provider=stub`이고, stub은 **열리지 않는 합성 URL**
(`https://stub.local/...`)을 돌려주는데 그게 `users.profile_image_url`에 그대로 저장된다.
`PLACE_PROVIDER`와 같은 성질의 문제다 — 설정 실수가 조용히 DB에 남고, provider를 고쳐도
사용자가 이미지를 다시 올리기 전까지 복구되지 않는다. 그래서 조용히 잘못 뜨는 대신 배포를 끊는다.

---

## 5. 확인 요청

1. dev부터 만들어 주면 BE가 dev 배포로 실제 업로드/표시까지 확인하겠다.
2. WAS ServiceAccount 이름과 prod 네임스페이스를 알려주면 IRSA 신뢰관계 대상이 정확한지 같이 보겠다.
3. KMS 키로 버킷을 암호화한다면 알려 달라 — IRSA 정책에 `kms:GenerateDataKey`가 더 필요하다.
