# 국가·도시 기준 데이터셋 (API-LOC-1 / API-LOC-2)

자동완성이 돌려주는 국가·도시 후보의 원천이다. **파일이 근거이고 DB는 파생물**이다 —
저장소에 커밋된 TSV를 앱이 기동 시 `countries`/`cities` 테이블로 적재한다.

명세(`specs/`)에 국가·도시 도메인 정의가 없어 데이터 출처를 BE에서 정했다.

| 항목 | 출처 |
| :--- | :--- |
| 국가 목록·한국어명 | [CLDR](https://github.com/unicode-org/cldr-json) `cldr-localenames-full/main/ko/territories.json` |
| 도시 목록·인구·소속 국가 | [GeoNames](https://download.geonames.org/export/dump/) `cities15000` (인구 15,000명 이상) |
| 도시 한국어명 | GeoNames `alternateNamesV2` 중 `isolanguage=ko` |

## 파일

```
src/main/resources/data/locations/
  countries.tsv   countryId  nameKo
  cities.tsv      cityId  nameKo  countryId  population
```

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
python3 scripts/build-location-dataset.py            # 표준 라이브러리만 사용
python3 scripts/build-location-dataset.py --version geonames-20260901   # 버전 문자열 지정
```

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
- prod는 `ddl-auto=validate`라 테이블이 먼저 있어야 한다 — `docs/ddl/{countries,cities,location_seed_state}.sql`.

## 알려진 한계

- **한국어 이름이 있는 도시는 전체의 일부다** (34,078개 중 6,097개). 나머지는 원문 표기로 들어간다.
  라틴 표기는 발음 부호를 벗겨 `new york`·`leon`으로 찾히지만, **초성 검색은 되지 않는다.**
  키릴·아랍 문자 등으로 남은 도시는 해당 문자를 직접 입력해야 한다. 주요 여행지는 대부분 한국어명이 있다.
- GeoNames 한국 도시명은 행정 표기를 따른다(`서울특별시`, `부산광역시`). FE 표시 문구를 줄이려면
  후처리 규칙을 데이터셋 생성 스크립트에 넣는 편이 낫다 — 앱에서 가공하면 검색 키와 어긋난다.
- `cities15000` 기준이라 인구 15,000명 미만의 소도시·휴양지는 없다. 더 촘촘한 목록이 필요하면
  `cities5000`/`cities1000`으로 바꾼다(각각 파일과 테이블이 커진다).
