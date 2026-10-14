# 국가·도시 기준 데이터셋 (API-LOC-1 / API-LOC-2 / API-LOC-3)

국가·도시 목록과 검색, 추천 도시 사진, 현지 통화 기호의 원천이다. **파일이 근거이고 DB는 파생물**이다 —
저장소에 커밋된 TSV를 앱이 기동 시 `countries`/`cities`/`city_images` 테이블로 적재한다.

> 2026-10 명세 개정(#97)으로 자동완성(부분 일치·초성·limit)은 폐기됐다. 남은 조회는 전체 목록 두 개와
> 접두어 검색 하나, 그리고 추천 도시 사진 조회다. `search_chosung` 컬럼은 조회에 쓰지 않지만 dev 의
> `ddl-auto=update` 가 컬럼을 지우지 않고 NOT NULL 이라 값은 계속 채운다(정리는 `docs/ddl`).

명세(`specs/`)에 국가·도시 도메인 정의가 없어 데이터 출처를 BE에서 정했다.

| 항목 | 출처 |
| :--- | :--- |
| 국가 목록·한국어명 | [CLDR](https://github.com/unicode-org/cldr-json) `cldr-localenames-full/main/ko/territories.json` |
| 국가 통화 코드·기호 | CLDR `cldr-core/supplemental/currencyData.json`(영토→통화) + `cldr-numbers-full/main/ko/currencies.json`(기호, 좁은 기호 우선: ¥·$·฿) |
| 국가 수도 | GeoNames `cities15000` 의 feature code `PPLC` (여럿이면 인구 최다) |
| 도시 목록·인구·소속 국가 | [GeoNames](https://download.geonames.org/export/dump/) `cities15000` (인구 15,000명 이상) |
| 도시 한국어명 | GeoNames `alternateNamesV2` 중 `isolanguage=ko` |
| 도시 대표 사진 | [Pixabay API](https://pixabay.com/api/docs/) — 아래 §도시 사진 |

## 파일

```
src/main/resources/data/locations/
  countries.tsv     countryId  nameKo  capitalCityId  currencyCode  currencySymbol
  cities.tsv        cityId  nameKo  countryId  population
  city_images.tsv   cityId  s3Key  source  sourceId
```

- `countries.tsv` 의 뒤 세 열은 **비어 있을 수 있다**(남극처럼 통화가 없거나 수도가 `cities15000` 에
  없는 국가). 파서는 앞 두 열만 필수로 본다(`LocationSeedFile.parse(in, 5, 2)`).

- 첫 줄은 `#version<TAB><버전문자열>` — **필수**. 이 값이 마지막 적재 버전과 다를 때만 재적재한다.
- `#`로 시작하는 줄은 주석, 빈 줄은 무시.
- 나머지는 탭 구분 데이터. 열 수가 다르면 기동 시 몇 번째 줄인지와 함께 실패한다.
- `countryId`는 ISO 3166-1 alpha-2, `cityId`는 GeoNames `geonameid`다. 둘 다 명세의 `countryId`·`cityId`로
  그대로 나간다.
- **검색 키(`search_name`·`search_chosung`)는 파일에 없다.** 적재 시점에 `SearchKeys`가 계산한다 —
  파일에 넣으면 앱의 검색 규칙과 파일이 어긋날 수 있다.
  - `search_name`: NFD 분해 → 발음 부호(`Mn`) 제거 → NFC 결합 → 소문자화 → 공백·구분자 제거.
    `León` → `leon`, `Lüliang` → `luliang`. (라틴 표기 도시 34,078개 중 12,173개가 발음 부호를
    달고 있어, 이 처리가 없으면 `Leon`으로 쳐서는 찾을 수 없다.)
  - `search_chosung`: `대한민국` → `ㄷㅎㅁㄱ`. NFD 결합 왕복 덕에 macOS에서 복사한 분해형 한글도
    같은 키가 된다.
- ⚠️ **`SearchKeys` 규칙을 바꾸면 `#version`도 함께 올려야 한다.** 키는 DB에만 있고 파일에는 없어서,
  버전이 그대로면 이미 적재된 환경이 옛 규칙으로 만든 키를 계속 쓴다.

## 갱신 방법

```bash
python3 scripts/build-location-dataset.py                     # 국가+도시 전부 (표준 라이브러리만 사용)
python3 scripts/build-location-dataset.py --countries-only    # 국가만 (통화·수도 갱신, 200MB 다운로드 없음)
python3 scripts/build-location-dataset.py --version geonames-20260901   # 버전 문자열 지정
```

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

```bash
PIXABAY_API_KEY=... python3 scripts/build-location-images.py          # 검색·다운로드 → build/location-images/images/
aws s3 sync build/location-images/images s3://<bucket>/locations/cities --content-type image/jpeg
```

분당 100회 제한이라 약 1시간 걸린다. 검색 결과와 이미지는 작업 디렉터리에 캐시되어 중단해도 이어서 한다.
키는 환경변수로만 받고 커밋하지 않는다. `--upload s3://...` 를 주면 끝에 `aws s3 sync` 까지 한다.

원본은 `build/location-dataset/`에 캐시된다(`alternateNamesV2.zip`이 200MB 가량이라 최초 실행은
오래 걸린다). 생성된 TSV를 커밋하면 다음 배포에서 자동 재적재된다 — `#version`이 바뀌기 때문이다.

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
- 적재 실패는 ERROR 로그만 남기고 **기동을 막지 않는다** — 자동완성만 비고 나머지 기능은 정상이다.
- `location.seed.enabled=false`로 끌 수 있다(테스트 기본값).
- 도시 사진(`city_images`)은 버전이 바뀐 경우에만 교체하며, 도시 데이터셋에 없는 도시의 사진은 버린다.
- prod는 `ddl-auto=validate`라 테이블이 먼저 있어야 한다 — `docs/ddl/{countries,cities,city_images,location_seed_state}.sql`.

## 알려진 한계

- **한국어 이름이 있는 도시는 전체의 일부다** (34,078개 중 6,097개). 나머지는 원문 표기로 들어간다.
  라틴 표기는 발음 부호를 벗겨 `new york`·`leon`으로 찾힌다. 키릴·아랍 문자 등으로 남은 도시는 해당
  문자를 직접 입력해야 한다. 주요 여행지는 대부분 한국어명이 있다.
- **검색은 접두어 일치다**(명세). "쿄"로 도쿄를 찾을 수 없고, 초성 검색도 없다.
- **국가별 전체 목록은 페이지네이션이 없다**(명세). 인도 3,779개·미국 3,407개가 한 번에 내려간다.
- **추천 도시 사진은 국가명·도시명이 적재된 한국어 표시명과 정규화 후 완전히 일치해야 찾힌다.** AI 가
  영문명으로 주면 못 찾고 빈 값이 된다 — 언어 설정(#99) 이후 영문명 데이터셋이 필요하면 그때 더한다.
- GeoNames 한국 도시명은 행정 표기를 따른다(`서울특별시`, `부산광역시`). FE 표시 문구를 줄이려면
  후처리 규칙을 데이터셋 생성 스크립트에 넣는 편이 낫다 — 앱에서 가공하면 검색 키와 어긋난다.
- `cities15000` 기준이라 인구 15,000명 미만의 소도시·휴양지는 없다. 더 촘촘한 목록이 필요하면
  `cities5000`/`cities1000`으로 바꾼다(각각 파일과 테이블이 커진다).
