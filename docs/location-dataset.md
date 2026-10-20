# 국가·도시 기준 데이터셋 (API-LOC-1 / API-LOC-2 / API-LOC-3)

국가·도시 목록과 검색, 추천 도시 사진, 현지 통화 기호의 원천이다. **파일이 근거이고 DB는 파생물**이다 —
저장소에 커밋된 TSV를 앱이 기동 시 `countries`/`cities`/`city_images` 테이블로 적재한다.

> 2026-10 명세 개정(#97)으로 자동완성(부분 일치·초성·limit)은 폐기됐다. 남은 조회는 전체 목록 두 개와
> 접두어 검색 하나, 그리고 추천 도시 사진 조회다. `search_chosung` 컬럼은 조회에 쓰지 않지만 dev 의
> `ddl-auto=update` 가 컬럼을 지우지 않고 NOT NULL 이라 값은 계속 채운다(정리는 `docs/ddl`).
>
> 2026-10-19 **언어별 표시명** 도입 — 표시명이 한국어·영어 두 벌이 됐고, 한국어명이 없는 도시는 더 이상
> 영문 표기로 섞여 들어가지 않는다(§선정 규칙). 응답 언어는 `Accept-Language`가 정한다(§언어).

명세(`specs/`)에 국가·도시 도메인 정의가 없어 데이터 출처를 BE에서 정했다.

| 항목 | 출처 |
| :--- | :--- |
| 국가 목록·한국어명·영문명 | [CLDR](https://github.com/unicode-org/cldr-json) `cldr-localenames-full/main/{ko,en}/territories.json` |
| 국가 통화 코드·기호 | CLDR `cldr-core/supplemental/currencyData.json`(영토→통화) + `cldr-numbers-full/main/ko/currencies.json`(기호, 좁은 기호 우선: ¥·$·฿) |
| 국가 수도 | GeoNames `cities15000` 의 feature code `PPLC` (여럿이면 인구 최다) |
| 도시 목록·인구·소속 국가 | [GeoNames](https://download.geonames.org/export/dump/) `cities15000` (인구 15,000명 이상) 중 §선정 규칙 통과분 |
| 도시 한국어명 | 덮어쓰기 파일 > GeoNames `alternateNamesV2` (`isolanguage=ko`) > [Wikidata](https://www.wikidata.org) 한국어 라벨(`P1566` GeoNames ID 로 연결) |
| 도시 영문명 | 덮어쓰기 파일 > GeoNames `alternateNamesV2` (`isolanguage=en`) > GeoNames `name` > `asciiname` |
| 도시 대표 사진 | [Pixabay API](https://pixabay.com/api/docs/) — 아래 §도시 사진 |

## 파일

```
src/main/resources/data/locations/
  countries.tsv     countryId  nameKo  nameEn  capitalCityId  currencyCode  currencySymbol
  cities.tsv        cityId  nameKo  nameEn  countryId  population
  city_images.tsv   cityId  s3Key  source  sourceId
scripts/
  build-location-dataset.py       생성 스크립트
  location-name-overrides.tsv     표시명 수동 덮어쓰기·소도시 유지 목록 (스크립트 입력)
```

- `countries.tsv` 의 뒤 세 열은 **비어 있을 수 있다**(남극처럼 통화가 없거나 수도가 데이터셋에 없는 국가).
  파서는 앞 세 열만 필수로 본다(`LocationSeedFile.parse(in, 6, 3)`).
- 첫 줄은 `#version<TAB><버전문자열>` — **필수**. 이 값이 마지막 적재 버전과 다를 때만 재적재한다.
- `#`로 시작하는 줄은 주석, 빈 줄은 무시.
- 나머지는 탭 구분 데이터. 열 수가 다르면 기동 시 몇 번째 줄인지와 함께 실패한다.
- `countryId`는 ISO 3166-1 alpha-2, `cityId`는 GeoNames `geonameid`다. 명세 JSON 스키마에는 둘 다 없어
  응답으로 나가지는 않는다(`countryName`·`cityName`만).
- **검색 키(`search_name`·`search_name_en`·`search_chosung`)는 파일에 없다.** 적재 시점에 `SearchKeys`가
  계산한다 — 파일에 넣으면 앱의 검색 규칙과 파일이 어긋날 수 있다.
  - `search_name`(한국어명)·`search_name_en`(영문명): NFD 분해 → 발음 부호(`Mn`) 제거 → NFC 결합 →
    소문자화 → 공백·구분자 제거. `São Paulo` → `saopaulo`, `Xi'an` → `xian`.
  - `search_chosung`: `대한민국` → `ㄷㅎㅁㄱ`. NFD 결합 왕복 덕에 macOS에서 복사한 분해형 한글도 같은 키가 된다.
- ⚠️ **`SearchKeys` 규칙을 바꾸면 `#version`도 함께 올려야 한다.** 키는 DB에만 있고 파일에는 없어서,
  버전이 그대로면 이미 적재된 환경이 옛 규칙으로 만든 키를 계속 쓴다.

## 선정 규칙 (도시)

0. **도시의 일부(GeoNames feature code `PPLX`)는 뺀다** — 푸둥·코파카바나·두바이 마리나·시흥동 같은 구역은
   여행 목적지로 고르는 단위가 아니다. 덮어쓰기 파일에 적으면 살릴 수 있다.
1. **한국어명이 있어야 한다.** GeoNames → Wikidata → 덮어쓰기 파일 어디에도 없으면 뺀다. 빌드가
   `build/location-dataset/missing-ko.tsv` 에 뺀 도시를 인구순으로 남기므로, 여행지가 보이면 덮어쓰기
   파일에 적어 살린다(암스테르담·하이퐁·사이타마·포르투처럼 출처 연결이 어긋난 주요 도시가 실제로 있었다).
2. **인구 50,000 이상**(`--min-population`, 기본값). 각국 수도와 덮어쓰기 파일의 도시는 예외다 — 끄라비·
   안시·몽트뢰·이시가키처럼 인구는 적지만 여행지로 주요한 곳은 덮어쓰기 파일에 적는다.
3. **한 나라 안에서 한글 표기 또는 영문 표기가 같은 도시는 인구가 많은 하나만** 남긴다(프랑크푸르트
   암마인·오데르, 푸저우 福州·抚州, Sendai 仙台·川内). 명세에 `cityId`가 없어 FE 는 이름만 보내므로 둘을
   구분할 길이 없다 — 어느 언어로 목록을 보든 마찬가지다.

2026-10-19 기준 6,367개 도시(국가 257개). 전체 3만 4천 개 중 한국어명이 있는 것은 GeoNames 6천·Wikidata
보강 2천 6백이고, 인구 하한까지 걸면 이 수가 된다.

### 한국어 표기 정리

- **행정 접미사를 뗀다.** `서울특별시`→`서울`, `부산광역시`→`부산`, `선전 시`→`선전`, `후루다오시`→`후루다오`.
  띄어쓴 접미사(시·현·주)는 어느 나라든, 붙여 쓴 `시`·`현`은 한·북·중·일·대만·홍콩·마카오만 뗀다(남는
  글자가 둘 이상일 때만 — `우시`(無錫)가 `우`가 되면 안 된다). 마라케시·트빌리시처럼 `시`가 지명의
  일부인 다른 나라 도시는 건드리지 않는다.
- 한국의 `군`(郡)도 뗀다: `홍천군`→`홍천`. 일본·다른 나라의 `군`·`구`·`동`은 그대로다(도시 단위가 아니거나
  지명의 일부).
- 영문명도 한국·일본의 로마자 접미사를 뗀다: `Goyang-si`→`Goyang`, `Tottori-shi`→`Tottori`.
- **한국 도시의 GeoNames 영문명 일부는 매큔-라이샤워 표기**(`Wŏnju`·`Koch'ang`)다. 영어 사용자와 AI 는
  로마자 표기법(`Wonju`·`Gochang`)을 쓰므로 덮어쓰기 파일로 바로잡는다. 계약 테스트가 한국 도시 영문명이
  ASCII 인지 검사한다.
- 뗀 결과가 **국가명과 같아지면 떼지 않는다**(`이란 시`(宜蘭) vs 이란). 검색(API-LOC-3)이 국가명 완전 일치를
  먼저 보므로, 같아지면 그 도시는 어떤 키워드로도 닿지 않는다. 빌드가 두 언어 국가명과 대조한다.
- 영문명도 `Tongchuanshi`→`Tongchuan`(중국·대만의 붙여 쓴 shi), `Bandundu Province`→`Bandundu`를 뗀다.
  GeoNames 영어 별명이 동네 이름인 경우(`Honmachi`→`Yatsushiro`)는 덮어쓰기 파일로 바로잡는다.
- 표기 자체가 마음에 들지 않으면 덮어쓰기 파일에 적는다(규칙을 늘리는 것보다 안전하다).

### 수동 덮어쓰기 (`scripts/location-name-overrides.tsv`)

열은 `id  nameKo  nameEn  note`. `id`는 국가 코드 또는 `geonameid`. 이름 열을 비우면 출처 값을 쓴다.
도시 행이 있으면 인구 하한과 무관하게 남긴다(단 한국어명이 끝내 없으면 그래도 뺀다). 파일을 고치면
스크립트를 다시 돌려 `#version`이 바뀌게 한다.

## 언어

- 응답 표시명 언어는 `LocationLanguageResolver`가 **저장된 언어 설정(API-USER-4) → `Accept-Language` →
  서비스 기본 언어** 순으로 정한다. 인증이 필요 없는 API 지만 토큰이 오면 사용자가 식별되므로, 앱에서 언어를
  고른 사용자는 기기 로케일 헤더와 무관하게 그 언어로 본다. 설정을 하지 않은 사용자(`users.language` null)와
  비로그인은 헤더로(명세 §언어 정책), 헤더도 없으면 기본 언어. 후보는 데이터셋이 표시명을 가진 언어
  (`LocationLanguage`: ko·en)다.
- **검색·완전 일치는 언어와 무관**하다. 두 언어의 검색 키를 모두 보므로 `countryName=Japan`과
  `countryName=일본`은 같은 국가이고, 키워드 `to`는 `도쿄`를 찾아 응답 언어의 표시명으로 내린다.
- 정렬은 응답 언어의 표시명 열로 DB 가 한다(한국어는 코드포인트 = 가나다순, 영어는 콜레이션).
- **이름으로 묶인 하위 조회도 두 언어를 본다.** 코스의 목적지(`destinationCountry/City`)와 AI 의 추천 도시는
  사용자 언어로 들어오므로, 대표 사진(`LocationImageService`)·통화 기호(`LocationCurrencyService`) 조회가
  영문명으로도 같은 행을 찾는다. 안 그러면 영어 사용자의 코스는 사진과 `exRateIcon`이 비게 된다.
- **옛 표기로 저장된 목적지도 사진을 찾는다.** 접미사를 떼기 전에 만든 코스에는 `부산광역시`·`수원시`가
  저장돼 있다. 사진 조회는 완전 일치 → 접두어 일치에 실패하면 **그 나라의 행정 접미사를 뗀 키**
  (`SearchKeys.withoutAdministrativeSuffix`, 스크립트 규칙의 역방향: 한·북·중·일·대만·홍콩·마카오의
  시·현, 한국의 군)로 한 번 더 찾는다. DB 에 "저장명이 입력의 접두어" 질의를 거는 대신 이렇게 하는
  이유는 그 질의가 `빈`(Vinh)으로 `빈롱`을 잡는 과매칭이기 때문이다. 통화 조회는 국가명이라 해당 없음.
- **`language.supported` 기본값은 `ko,en`이다**(2026-10-19). API-USER-4 로 `en`을 저장할 수 있고, 그 값이
  표시명의 1순위이자 AI 요청(API-AI-1/2)의 `language`·`Accept-Language`다. AI 는 이 값으로 출력 언어를 정한다
  (DOM-4 §출력 언어: 본문 `language` → 헤더 → `ko`; DOM-5: `ko` 외 언어는 제목·추천 이유·팁을 번역하고 장소명은
  보존, 추천 도시명은 영어 고유명사). 그래서 영어 사용자의 `recommendCity`는 영문명으로 오고, 사진 조회가 두
  언어 키를 보는 이유가 이것이다. 어떤 환경의 AI 가 특정 언어를 못 낸다면 `LANGUAGE_SUPPORTED`로 좁힌다.
- `Vary: Accept-Language`를 모든 `/api/*` 응답에 붙인다 — 인증 없는 GET 이 언어별로 다른 본문을 내므로
  브라우저·프록시 캐시가 한국어 요청에 영어 응답을 돌려주지 않게. **엣지(CDN)는 `Vary`를 무시할 수 있다**
  (Cloudflare 는 `Accept-Encoding` 외 무시, CloudFront 는 캐시 정책에 넣은 헤더만). 인프라에서 `/api/*`를
  캐시하지 않거나, 캐시한다면 `Accept-Language`를 캐시 키·오리진 전달 헤더에 넣었는지 확인해야 한다 —
  전달하지 않으면 BE 는 헤더를 못 받아 항상 한국어로 응답한다.
- `Content-Language` 응답 헤더는 **메시지 언어**(`language.message-languages`, 지금은 `ko`)를 말한다.
  영어 표시명을 내려도 `message`는 한국어라 헤더는 `ko`다 — API-USER-3/4 §언어 헤더 정책("실제 메시지에
  적용한 언어")을 따른 것이고, 명세 담당에 "표시명 언어와 메시지 언어가 다를 수 있다"는 확인이 필요하다.

## 갱신 방법

```bash
python3 scripts/build-location-dataset.py                     # 국가+도시 전부 (표준 라이브러리만 사용)
python3 scripts/build-location-dataset.py --countries-only    # 국가만 (통화·수도 갱신, 200MB 다운로드 없음)
python3 scripts/build-location-dataset.py --min-population 100000   # 인구 하한 변경
python3 scripts/build-location-dataset.py --no-wikidata       # Wikidata 없이 (GeoNames·덮어쓰기만)
python3 scripts/build-location-dataset.py --version cldr-geonames-wikidata-20261101   # 버전 문자열 지정
```

- 원본은 `build/location-dataset/`에 캐시된다(`alternateNamesV2.zip`이 200MB 가량이라 최초 실행은 오래
  걸린다). Wikidata 조회 결과도 `wikidata-ko.json`에 캐시되며, 새로 받으려면 지운다.
- Wikidata 는 공식 SPARQL(`query.wikidata.org`)이 장애 시 분당 1회로 조이는 일이 있어 **QLever 미러**
  (`qlever.dev/api/wikidata`)를 기본으로 쓴다(3,000건 묶음 12회, 1분 안팎). `--wikidata-endpoint`로 바꾼다.
- 생성된 TSV를 커밋하면 다음 배포에서 자동 재적재된다 — `#version`이 바뀌기 때문이다.

## 도시 사진 (`city_images.tsv`)

API-PREF-4 의 `recommendCity.imageUrl` 과 API-COURSE-3 의 `coverImageUrl` 폴백에 쓰는 도시 대표 사진이다.
명세에 출처 규정이 없어 BE 에서 정했다(근거: `docs/spec-index.md` §2026-10-14).

- **출처는 Pixabay 하나다.** Content License 가 저작자 표시를 요구하지 않고, API 규칙이 핫링크를 금지하는
  대신 "쓰려면 서버에 내려받으라"고 하므로 **파일을 받아 우리 S3 에 올린다.** 출처 필드는 API 에 내보내지
  않는다(사용자 결정). Wikidata/Commons 처럼 저작자 표시가 필요한 출처는 **한 장도 섞지 않는다** —
  섞는 순간 출처 필드가 다시 필요해진다.
- **대상:** 인구 20만 이상 도시 + 각국 수도(약 3,100개). 실측(2026-10-14)으로 도시명이 태그에 있는 사진은
  관광 도시 100%, 수도 77%, 100만 이상 도시 77%, 10만 이상 41%다. 롱테일은 약하므로 앱이 **도시 → 수도 →
  사진 있는 최다 인구 도시 → 빈 값** 순으로 폴백한다(`LocationImageService`).
- **선정 규칙:** 도시명이 태그에 **단어 단위로 완전히** 있는 사진만(퍼지 매칭 오탐 방지), 경관 키워드 태그
  우선, 그다음 좋아요 순. 상세는 `scripts/build-location-images.py` 머리말.
- **수동 덮어쓰기:** 관광 상위 도시의 사진이 마음에 들지 않으면 TSV 의 그 행을 `source=manual` 로 바꾸고
  이미지를 직접 올린다. 스크립트는 `manual` 행을 다시 검색하지 않고 보존한다.
- **파일 위치:** 프로필 이미지와 같은 버킷의 `locations/cities/<cityId>.jpg`. TSV 에는 키만 있고 URL 은
  런타임에 `location.images.base-url`(기본값: 프로필 이미지 base-url) 과 합친다 — dev·prod 가 같은 TSV 를
  쓰기 위해서다. base-url 이 비면 사진은 전부 빈 값으로 나간다.
- 도시 데이터셋에서 빠진 도시의 사진은 `build-location-dataset.py`가 TSV 에서 지우고 버전에 `+pruned`를
  붙인다(적재기의 고아 필터는 그대로 두되, 기동마다 경고가 나지 않도록). S3 파일은 그대로 있어도 된다.
  인구 하한을 내려 도시가 늘면 `build-location-images.py`를 다시 돌려 사진을 받는다.

```bash
PIXABAY_API_KEY=... python3 scripts/build-location-images.py          # 검색·다운로드 → build/location-images/images/
aws s3 sync build/location-images/images s3://<bucket>/locations/cities --content-type image/jpeg
```

분당 100회 제한이라 약 1시간 걸린다. 검색 결과와 이미지는 작업 디렉터리에 캐시되어 중단해도 이어서 한다.
키는 환경변수로만 받고 커밋하지 않는다. `--upload s3://...` 를 주면 끝에 `aws s3 sync` 까지 한다.

## 적재 동작

- `LocationSeedLoader`가 `ApplicationRunner`로 돈다 → **readiness 이전에 끝난다.**
- 평시 부팅은 **각 파일의 `#version` 헤더 한 줄 + 상태 조회 두 번**으로 끝난다. 전체 파싱은 실제로
  재적재가 필요할 때만 한다.
- 버전이 같으면 건너뛴다. 다르면 해당 테이블을 **한 트랜잭션 안에서** 통째로 교체한다
  (롤링 배포 중 다른 파드에 중간 상태가 보이지 않는다).
- **국가가 재적재되면 도시도 함께 재적재한다.** 도시 행이 `country_name_ko`를 비정규화해 들고 있어,
  국가만 갱신하면 도시가 옛 국가명을 계속 광고한다.
- 파드 여러 대가 동시에 재적재를 시작하면 뒤늦게 커밋하는 쪽이 PK 충돌로 롤백된다. 앞선 적재로
  데이터는 이미 올바르므로 WARN으로만 남긴다(ERROR 헛경보 방지).
- 적재 실패는 ERROR 로그만 남기고 **기동을 막지 않는다** — 지역 API 만 비고 나머지 기능은 정상이다.
- `location.seed.enabled=false`로 끌 수 있다(테스트 기본값).
- 도시 사진(`city_images`)은 버전이 바뀐 경우에만 교체하며, 도시 데이터셋에 없는 도시의 사진은 버린다.
- prod는 `ddl-auto=validate`라 테이블·컬럼이 먼저 있어야 한다 —
  `docs/ddl/{countries,cities,city_images,location_seed_state}.sql`. 영문 표시명 컬럼(`name_en`·
  `search_name_en`)은 NOT NULL 없이 추가한다(기존 행이 있는 dev 에서 `ddl-auto=update`가 NOT NULL 컬럼
  추가에 실패하면 적재까지 막힌다). 값은 적재가 채우고, 비어 있으면 앱이 한국어명으로 보인다.

## 데이터셋 계약 테스트

`LocationDatasetContractTest`가 커밋된 TSV 를 DB 없이 읽어 §선정 규칙·§한국어 표기 정리를 검사한다 —
모든 한국어명에 한글이 있고 로마자 단어가 없는지, 영문명이 비지 않았는지, 접미사가 되살아나지 않았는지,
대표 여행지(도쿄·파리·암스테르담·끄라비…)가 들어 있는지. 스크립트나 출처가 바뀌어 계약이 깨지면 빌드에서
드러난다.

## 알려진 한계

- **한국어명이 없는 도시는 데이터셋에 없다.** 인구 5만 이상인데 빠진 도시가 5,800개쯤 된다(대부분 중국·
  인도·브라질의 비관광 도시). `missing-ko.tsv`에서 보이는 여행지는 덮어쓰기로 살린다.
- **인구 5만 미만 소도시**는 덮어쓰기 파일에 적은 것만 있다. 인터라켄·할슈타트처럼 15,000명 미만은
  `cities15000`에 아예 없어 더 촘촘한 목록(`cities5000`)이 필요하다.
- **검색은 접두어 일치다**(명세). "쿄"로 도쿄를 찾을 수 없고, 초성 검색도 없다.
- **국가별 전체 목록은 페이지네이션이 없다**(명세). 인구 하한 덕에 가장 큰 미국이 760개 안팎이다.
- 한 나라 안 동명 도시는 하나만 남아, 인구가 적은 쪽(프랑크푸르트안데어오데르)은 고를 수 없다.
- 코스에 저장된 목적지 이름은 입력 당시 언어·표기 그대로다. 사용자가 언어를 바꿔도 옛 코스의 목적지는
  바뀌지 않고, 접미사를 떼기 전의 `부산광역시`도 그대로 보인다(명세에 `cityId`가 없어 다시 그릴 근거가
  없다). 사진 조회만 접미사를 뗀 키로 이어 준다(띄어쓴 `주`만 예외 — 정규화 뒤 `광주`·`신의주`와 구분할 수 없다).
- 옛 표기 목적지의 사진 조회는 질의가 두 번 더 든다(접미사 뗀 키로 완전 일치·접두어). 코스 목록은 목적지마다
  한 번만 조회하므로(`CourseCoverImageResolver` 메모이즈) 두지 않았다.
- 영문 열이 비어 있는 퇴행 상태(DDL 은 적용됐는데 재적재가 실패)에서는 영문 요청이 한국어 표시명으로
  떨어지고 정렬(`order by name_en`)이 NULL 끼리라 보장되지 않는다. 적재기가 ERROR 로 남기므로 조회 쪽에서
  따로 메우지 않는다.
