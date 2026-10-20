#!/usr/bin/env python3
"""국가·도시 기준 데이터셋(TSV) 생성 스크립트 (API-LOC-1 / API-LOC-2 / API-LOC-3).

표시명은 한국어·영어 두 벌이다. 한국어명이 없는 도시는 데이터셋에 넣지 않는다 — 영문 표기가
한글 목록에 섞여 나가는 것을 막기 위해서다(2026-10, 언어별 표시명 도입).

출처
  - 국가 표시명   : CLDR (unicode-org/cldr-json, ko·en/territories.json)
  - 국가 통화     : CLDR supplemental/currencyData.json (영토→통화) + ko/currencies.json (기호)
  - 국가 수도     : GeoNames cities15000 의 feature code PPLC (여럿이면 인구 최다)
  - 도시 목록     : GeoNames cities15000 (인구 15,000명 이상) 중 아래 §선정 규칙을 통과한 것
  - 도시 한국어명 : 덮어쓰기 파일 > GeoNames alternateNamesV2 (isolanguage=ko) > Wikidata ko 라벨
                    (P1566 GeoNames ID 로 연결, QLever 미러로 조회)
  - 도시 영문명   : 덮어쓰기 파일 > GeoNames alternateNamesV2 (isolanguage=en) > GeoNames name > asciiname

선정 규칙 (도시)
  0. GeoNames feature code 가 PPLX(도시의 일부 — 푸둥·코파카바나·두바이 마리나 같은 구역)면 뺀다.
     여행 목적지로 고르는 단위가 아니고, 한국어명이 있어도 "시흥동"처럼 동 이름이 도시 목록에 섞인다.
  1. 한국어명이 있어야 한다. 세 출처 어디에도 없으면 뺀다(작업 디렉터리의 missing-ko.tsv 에 남긴다 —
     여행지가 보이면 덮어쓰기 파일에 적어 살린다).
  2. 인구가 --min-population(기본 50,000) 이상이어야 한다. 각국 수도와 덮어쓰기 파일의 도시는 예외다.

한국어 표기 정리
  - 행정 접미사를 뗀다: "서울특별시"→"서울", "부산광역시"→"부산", "선전 시"→"선전", "후루다오시"→"후루다오".
    띄어쓴 접미사(시·현·주)는 어느 나라든 떼고, 붙여 쓴 "시"·"현"은 한·중·일·대만·홍콩·마카오만 뗀다
    (남는 글자가 둘 이상일 때만 — "우시"(無錫)가 "우"가 되면 안 된다).
  - 영문명도 한국·일본의 로마자 접미사("Goyang-si"·"Tottori-shi"), 중국·대만의 붙여 쓴 "shi"("Tongchuanshi"),
    " Province"("Bandundu Province")를 뗀다. 그 밖의 틀린 영문명(GeoNames 영어 별명이 동네 이름인
    "Honmachi"→Yatsushiro)은 덮어쓰기 파일로 바로잡는다.
  - 뗀 결과가 국가명과 같아지면("이란 시"(宜蘭)→"이란") 떼지 않는다 — 검색이 국가명 완전 일치를 먼저 봐서
    그 도시가 어떤 키워드로도 닿지 않게 되기 때문이다.
  - 붙여 쓴 "군"(郡)은 한국만 뗀다("홍천군"→"홍천").
  - 한 나라 안에서 한글 표기 또는 영문 표기가 같은 도시("프랑크푸르트" 암마인·오데르, "Sendai")는
    인구가 많은 하나만 남긴다 — 어느 언어로 목록을 보든 같은 이름이 둘 보이면 고를 수 없다.
  - 한국 도시의 GeoNames 영문명 일부는 매큔-라이샤워 표기("Wŏnju"·"Koch'ang")다. 영어 사용자와 AI 는
    국어의 로마자 표기법("Wonju"·"Gochang")을 쓰므로 덮어쓰기 파일로 바로잡는다(빌드 뒤 KR 행 중
    비ASCII·아포스트로피가 있는 영문명을 확인한다).
  - 검색 별칭은 두지 않는다. 접미사를 떼기 전에 저장된 코스 목적지("부산광역시")는 앱의 사진 조회가 같은
    접미사 목록을 거꾸로 적용해(SearchKeys.withoutAdministrativeSuffix) "부산"을 찾는다 — 여기 접미사를
    늘리면 그쪽도 같이 늘린다.

산출물 (src/main/resources/data/locations/)
  countries.tsv : countryId  nameKo  nameEn  capitalCityId  currencyCode  currencySymbol
                  (뒤 세 열은 비어 있을 수 있다 — 남극처럼 통화가 없거나 수도가 데이터셋에 없는 경우)
  cities.tsv    : cityId  nameKo  nameEn  countryId  population
  city_images.tsv : 새로 만들지는 않지만(build-location-images.py 몫), 도시 데이터셋에서 빠진 도시의 사진 행은
                  지우고 버전에 "+pruned" 를 붙인다 — 앱이 기동마다 고아 행 경고를 내지 않도록.

통화 기호는 CLDR 한국어 좁은 기호(symbol-alt-narrow: ¥·$·฿)를 우선하고, 없으면 일반 기호, 그것도
없으면 ISO 코드를 쓴다. 영토에 통용 통화가 여럿이면(파나마 PAB·USD 등) CLDR 이 먼저 적은 쪽을 쓴다.
코스 상세의 exRateIcon(API-COURSE-2, #100)이 이 기호를 그대로 내려보낸다.

사용법
  python3 scripts/build-location-dataset.py [--version <문자열>] [--work-dir <경로>] [--countries-only]
                                            [--min-population N] [--no-wikidata] [--overrides <경로>]

  --countries-only 는 countries.tsv 만 다시 만든다(200MB 짜리 alternateNamesV2 를 받지 않는다).
  국가만 바뀌어도 앱은 도시를 함께 재적재하므로(country_name_ko 사본) cities.tsv 는 그대로 둬도 된다.

alternateNamesV2.zip 이 200MB 가량이라 최초 실행은 시간이 걸린다. 작업 디렉터리에 받아 둔 파일이
있으면 다시 받지 않는다. Wikidata 조회 결과도 작업 디렉터리에 캐시한다. 표준 라이브러리만 사용한다.
"""

import argparse
import csv
import datetime
import io
import json
import os
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
import zipfile

CLDR_BASE = "https://raw.githubusercontent.com/unicode-org/cldr-json/main/cldr-json/"
CLDR_TERRITORIES = CLDR_BASE + "cldr-localenames-full/main/{lang}/territories.json"
CLDR_CURRENCY_DATA = CLDR_BASE + "cldr-core/supplemental/currencyData.json"
CLDR_CURRENCIES_KO = CLDR_BASE + "cldr-numbers-full/main/ko/currencies.json"
GEONAMES_CITIES = "https://download.geonames.org/export/dump/cities15000.zip"
GEONAMES_ALT_NAMES = "https://download.geonames.org/export/dump/alternateNamesV2.zip"
# Wikidata 공식 SPARQL(query.wikidata.org)은 장애 시 분당 1회로 조이는 일이 있어 QLever 미러를 기본으로 쓴다.
# 같은 SPARQL 이므로 --wikidata-endpoint 로 바꿀 수 있다.
WIKIDATA_ENDPOINT = "https://qlever.dev/api/wikidata"
WIKIDATA_BATCH = 3000

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO_ROOT, "src", "main", "resources", "data", "locations")
DEFAULT_OVERRIDES = os.path.join(REPO_ROOT, "scripts", "location-name-overrides.tsv")

LANGS = ("ko", "en")
DEFAULT_MIN_POPULATION = 50_000

# CLDR 은 국가가 아닌 지역(대륙·"세계"·EU 등)도 같은 파일에 담는다. 국가 코드는 알파벳 2글자이므로
# 숫자 코드(001=세계, 150=유럽 …)를 걸러내면 대부분 정리된다. 아래는 알파벳 2글자지만 ISO 3166-1
# 국가가 아니거나(EU/EZ/UN) 여행지로서 의미가 없는 코드다.
NON_COUNTRY_CODES = {"EU", "EZ", "UN", "QO", "ZZ", "XA", "XB"}

# 붙여 쓴 "시"·"현"을 떼는 나라. 다른 나라의 "…시"는 지명의 일부일 가능성이 높다(마라케시·트빌리시).
CJK_SUFFIX_COUNTRIES = {"KR", "KP", "JP", "CN", "TW", "HK", "MO"}
SPACED_SUFFIX = re.compile(r"^(.+?)\s+(시|현|주)$")
JOINED_SUFFIX = re.compile(r"^(.+?)(특별자치시|특별자치도|특별시|광역시|직할시|시|현)$")
# "군"(郡)은 한국만 뗀다 — 일본의 "군"은 도시 단위가 아니고, 다른 나라에서는 지명의 일부다.
KR_COUNTY_SUFFIX = re.compile(r"^(.+?)군$")
# 도시의 일부(구역)를 뜻하는 GeoNames feature code. 덮어쓰기 파일의 도시는 예외다.
EXCLUDED_FEATURE_CODES = {"PPLX"}
# 영문명의 행정 접미사: GeoNames 영어 별명이 "Goyang-si"·"Tottori-shi"처럼 로마자 접미사를 단 경우.
EN_SUFFIX_COUNTRIES = {"KR", "JP"}
EN_SUFFIX = re.compile(r"^(.+?)-(si|shi)$")
# 중국·대만의 로마자 "…shi"(市)는 붙여 쓴다("Tongchuanshi"). 남는 글자가 셋 이상일 때만.
EN_JOINED_SHI_COUNTRIES = {"CN", "TW"}
EN_JOINED_SHI = re.compile(r"^(.{3,}?)shi$")
EN_PROVINCE = re.compile(r"^(.+?) Province$")


def download(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        print(f"  캐시 사용: {dest}")
        return dest
    print(f"  다운로드: {url}")
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with urllib.request.urlopen(url) as resp, open(dest, "wb") as f:
        while chunk := resp.read(1 << 20):
            f.write(chunk)
    return dest


def load_country_names(work_dir, lang):
    """CLDR {lang}/territories.json → {ISO alpha-2: 국가명}"""
    path = download(CLDR_TERRITORIES.format(lang=lang), os.path.join(work_dir, f"territories-{lang}.json"))
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    territories = data["main"][lang]["localeDisplayNames"]["territories"]

    names = {}
    for code, name in territories.items():
        # "US-alt-short" 같은 변형 키는 버리고 기본 표기만 쓴다.
        if "-alt-" in code:
            continue
        if len(code) != 2 or not code.isalpha():
            continue
        if code.upper() in NON_COUNTRY_CODES:
            continue
        names[code.upper()] = name
    return names


def load_currencies(work_dir):
    """CLDR → {ISO alpha-2: (currencyCode, currencySymbol)}. 통용 통화가 없는 영토는 빠진다."""
    with open(download(CLDR_CURRENCY_DATA, os.path.join(work_dir, "currencyData.json")),
              encoding="utf-8") as f:
        region = json.load(f)["supplemental"]["currencyData"]["region"]
    with open(download(CLDR_CURRENCIES_KO, os.path.join(work_dir, "currencies-ko.json")),
              encoding="utf-8") as f:
        symbols = json.load(f)["main"]["ko"]["numbers"]["currencies"]

    result = {}
    for code, entries in region.items():
        for entry in entries:
            (currency, meta), = entry.items()
            # _to 가 있으면 폐지된 통화, _tender=false 는 법정 통화가 아닌 것(기념 주화 등).
            if meta.get("_to") or meta.get("_tender") == "false":
                continue
            info = symbols.get(currency, {})
            symbol = info.get("symbol-alt-narrow") or info.get("symbol") or currency
            result[code.upper()] = (currency, symbol)
            break  # CLDR 이 먼저 적은 통화가 주 통화
    return result


def read_geonames_cities(work_dir):
    """cities15000 → [(geonameid, name, asciiname, featureCode, countryCode, population)]"""
    path = download(GEONAMES_CITIES, os.path.join(work_dir, "cities15000.zip"))
    rows = []
    with zipfile.ZipFile(path) as z, z.open("cities15000.txt") as raw:
        for line in io.TextIOWrapper(raw, encoding="utf-8"):
            cols = line.rstrip("\n").split("\t")
            if len(cols) < 15:
                continue
            rows.append((cols[0], cols[1], cols[2], cols[7], cols[8], int(cols[14] or 0)))
    return rows


def pick_capitals(geonames_rows):
    """{ISO alpha-2: 수도 geonameid}. PPLC 가 여럿이면(남아공 등) 인구가 가장 많은 쪽."""
    best = {}
    for gid, _, _, feature, country, population in geonames_rows:
        if feature != "PPLC":
            continue
        current = best.get(country)
        if current is None or population > current[0]:
            best[country] = (population, gid)
    return {code: gid for code, (_, gid) in best.items()}


def read_city_ids(path):
    ids = set()
    if not os.path.exists(path):
        return ids
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line and not line.startswith("#"):
                ids.add(line.split("\t", 1)[0])
    return ids


def load_alternate_names(work_dir, geoname_ids):
    """alternateNamesV2 → {lang: {geonameid: 이름}} (lang ∈ LANGS)

    한 도시에 같은 언어 이름이 여럿일 수 있어 우선순위를 둔다: preferred > 축약(short) > 일반.

    축약형을 일반 이름보다 위에 두는 게 맞다. GeoNames의 isShortName은 행정 접미사를 뺀 통용
    명칭이라("뉴욕" vs "뉴욕 시", "New York" vs "New York City") 표시 문구로 그쪽이 낫다.
    이 값이 그대로 FE에 나가고 검색 키의 근거가 되므로 우선순위를 바꾸지 말 것.

    historic(옛 이름)·colloquial(속칭)은 제외한다 — 표시명으로 부적절하다.
    """
    path = download(GEONAMES_ALT_NAMES, os.path.join(work_dir, "alternateNamesV2.zip"))
    best = {lang: {} for lang in LANGS}  # lang -> geonameid -> (rank, name)
    with zipfile.ZipFile(path) as z:
        member = next(n for n in z.namelist() if n.endswith("alternateNamesV2.txt"))
        with z.open(member) as raw:
            for line in io.TextIOWrapper(raw, encoding="utf-8"):
                cols = line.rstrip("\n").split("\t")
                if len(cols) < 8 or cols[2] not in best or cols[1] not in geoname_ids:
                    continue
                geoname_id, name = cols[1], cols[3].strip()
                is_preferred = cols[4] == "1"
                is_short = cols[5] == "1"
                is_colloquial = cols[6] == "1"
                is_historic = cols[7] == "1"
                if is_colloquial or is_historic or not name:
                    continue
                rank = 2 if is_preferred else (1 if is_short else 0)
                current = best[cols[2]].get(geoname_id)
                if current is None or rank > current[0]:
                    best[cols[2]][geoname_id] = (rank, name)
    return {lang: {gid: name for gid, (_, name) in names.items()} for lang, names in best.items()}


def sparql(endpoint, query):
    body = urllib.parse.urlencode({"query": query}).encode()
    request = urllib.request.Request(endpoint, data=body, headers={
        "Accept": "application/sparql-results+json",
        "Content-Type": "application/x-www-form-urlencoded",
        "User-Agent": "yeolo-location-dataset/1.0 (build script; github.com/Software-Maestro-GTA/Yeolo-BE)",
    })
    with urllib.request.urlopen(request, timeout=300) as resp:
        return json.load(resp)["results"]["bindings"]


def load_wikidata_korean_names(work_dir, geoname_ids, endpoint):
    """Wikidata → {geonameid: 한국어 라벨}. P1566(GeoNames ID) 로 연결한다.

    결과는 wikidata-ko.json 에 캐시한다 — 재실행 때 네트워크를 다시 타지 않고, 미러가 죽어도 지난
    결과로 빌드할 수 있다. 새로 받으려면 캐시 파일을 지운다.
    """
    cache = os.path.join(work_dir, "wikidata-ko.json")
    if os.path.exists(cache):
        print(f"  캐시 사용: {cache}")
        with open(cache, encoding="utf-8") as f:
            return json.load(f)

    ids = sorted(geoname_ids)
    result = {}
    for start in range(0, len(ids), WIKIDATA_BATCH):
        batch = ids[start:start + WIKIDATA_BATCH]
        values = " ".join(f'"{gid}"' for gid in batch)
        query = f"""
            PREFIX wdt: <http://www.wikidata.org/prop/direct/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT ?g ?ko WHERE {{
              VALUES ?g {{ {values} }}
              ?item wdt:P1566 ?g .
              ?item rdfs:label ?ko . FILTER(LANG(?ko) = "ko")
            }}"""
        for attempt in range(3):
            try:
                bindings = sparql(endpoint, query)
                break
            except Exception as e:  # noqa: BLE001 — 미러 일시 장애는 몇 번 더 시도한다
                if attempt == 2:
                    raise
                print(f"  Wikidata 조회 실패({e}), {5 * (attempt + 1)}초 뒤 재시도")
                time.sleep(5 * (attempt + 1))
        for b in bindings:
            gid, label = b["g"]["value"], b["ko"]["value"].strip()
            # 한 GeoNames ID 에 항목이 둘 연결된 드문 경우는 먼저 온 라벨을 쓴다.
            if label and gid not in result:
                result[gid] = label
        print(f"  Wikidata {min(start + WIKIDATA_BATCH, len(ids))}/{len(ids)} 조회, 한국어 라벨 {len(result)}건")
        time.sleep(1)
    with open(cache, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=0, sort_keys=True)
    return result


def load_overrides(path):
    """덮어쓰기 파일 → {id: (nameKo | None, nameEn | None)}. 파일이 없으면 빈 dict."""
    overrides = {}
    if not path or not os.path.exists(path):
        return overrides
    with open(path, encoding="utf-8") as f:
        for line_no, line in enumerate(f, 1):
            if not line.strip() or line.startswith("#"):
                continue
            cols = line.rstrip("\n").split("\t")
            if len(cols) < 3:
                sys.exit(f"오류: 덮어쓰기 파일 {line_no}번째 줄의 열이 부족하다(id, nameKo, nameEn[, note]): {line!r}")
            key = cols[0].strip()
            if key in overrides:
                sys.exit(f"오류: 덮어쓰기 파일에 {key} 가 두 번 있다")
            overrides[key] = (cols[1].strip() or None, cols[2].strip() or None)
    return overrides


def has_hangul(text):
    return any("가" <= ch <= "힣" for ch in text)


def normalize_korean_city_name(name, country_code, country_keys=frozenset()):
    """행정 접미사를 뗀 표시명. 규칙은 모듈 머리말 §한국어 표기 정리.

    뗀 결과가 어떤 국가명과 같아지면("이란 시"(宜蘭)→"이란") 떼지 않는다 — 검색(API-LOC-3)은 국가명 완전
    일치를 먼저 보므로 그 도시는 어떤 키워드로도 닿을 수 없게 된다.
    """
    name = " ".join(name.split())
    stripped = None
    spaced = SPACED_SUFFIX.match(name)
    if spaced:
        stripped = spaced.group(1)
    elif country_code in CJK_SUFFIX_COUNTRIES:
        joined = JOINED_SUFFIX.match(name)
        if joined and len(joined.group(1)) >= 2:
            stripped = joined.group(1)
    if stripped is None and country_code == "KR":
        county = KR_COUNTY_SUFFIX.match(name)
        if county and len(county.group(1)) >= 2:
            stripped = county.group(1)
    if stripped is None or normalized_search_key(stripped) in country_keys:
        return name
    return stripped


def normalize_english_city_name(name, country_code):
    """로마자 행정 접미사를 뗀 영문 표시명("Goyang-si"→"Goyang", "Tottori-shi"→"Tottori")."""
    name = " ".join(name.split())
    if country_code in EN_SUFFIX_COUNTRIES:
        matched = EN_SUFFIX.match(name)
        if matched and len(matched.group(1)) >= 2:
            return matched.group(1)
    if country_code in EN_JOINED_SHI_COUNTRIES:
        matched = EN_JOINED_SHI.match(name)
        if matched:
            return matched.group(1)
    province = EN_PROVINCE.match(name)
    if province:
        return province.group(1)
    return name


def dedupe_same_names(cities, overrides):
    """한 나라 안에서 한국어 또는 영문 표시명이 같은 도시는 인구가 가장 많은 하나만 남긴다.

    "프랑크푸르트"(암마인·오데르)·"푸저우"(福州·抚州)·"Sendai"처럼 다른 도시가 같은 표기를 갖는 경우가 있다.
    어느 언어로 목록을 보든 같은 이름이 둘 보이면 고를 수 없고, 어느 쪽을 고르든 FE 는 이름만 보내므로
    (명세에 cityId 없음) 결국 인구가 많은 쪽으로 해석된다 — 처음부터 그쪽만 둔다. 덮어쓰기 파일의 도시는
    지우지 않는다.
    """
    for name_index in (1, 2):
        best = {}
        for city in cities:
            key = (city[3], normalized_search_key(city[name_index]))
            current = best.get(key)
            if current is None:
                best[key] = city
                continue
            # 덮어쓰기 도시가 아닌 쪽보다 우선하고, 같은 등급끼리는 인구가 많은 쪽.
            forced, current_forced = city[0] in overrides, current[0] in overrides
            if (forced and not current_forced) or (forced == current_forced and city[4] > current[4]):
                best[key] = city
        kept = set(id(c) for c in best.values())
        cities = [c for c in cities if id(c) in kept]
    return cities


def build_cities(geonames_rows, country_names, alt_names, wikidata_ko, overrides, capitals, min_population):
    """→ (cities, missing) — cities: [(cityId, nameKo, nameEn, countryId, population)],
    missing: 한국어명이 없어 뺀 도시 중 인구 하한 이상인 것 [(cityId, name, countryId, population)]."""
    capital_ids = set(capitals.values())
    country_codes = set(country_names["ko"]) & set(country_names["en"])
    country_keys = frozenset(normalized_search_key(n) for lang in LANGS for n in country_names[lang].values())
    cities, missing = [], []
    for gid, name, ascii_name, feature, country, population in geonames_rows:
        if country not in country_codes:
            continue
        forced = gid in overrides
        if feature in EXCLUDED_FEATURE_CODES and not forced:
            continue
        if population < min_population and gid not in capital_ids and not forced:
            continue
        override_ko, override_en = overrides.get(gid, (None, None))
        name_ko = override_ko or alt_names["ko"].get(gid) or wikidata_ko.get(gid)
        if not name_ko or not has_hangul(name_ko):
            if population >= min_population or forced or gid in capital_ids:
                missing.append((gid, name, country, population))
            continue
        if not override_ko:
            name_ko = normalize_korean_city_name(name_ko, country, country_keys)
        name_en = override_en or alt_names["en"].get(gid) or name or ascii_name
        if not override_en:
            name_en = normalize_english_city_name(name_en, country)
        cities.append((gid, name_ko, name_en, country, population))
    before = len(cities)
    cities = dedupe_same_names(cities, overrides)
    if before != len(cities):
        print(f"  한 나라 안에서 한글 표기가 같은 도시 {before - len(cities)}개를 인구 적은 쪽부터 뺐다")
    # 인구 많은 순으로 정렬해 두면 파일 diff 를 볼 때도 주요 도시가 위에 온다.
    cities.sort(key=lambda r: (-r[4], r[1]))
    missing.sort(key=lambda r: -r[3])
    return cities, missing


def normalized_search_key(name):
    """앱의 SearchKeys.normalize 와 같은 규칙: 발음 부호 제거 → 소문자 → 영숫자·한글만."""
    decomposed = unicodedata.normalize("NFD", name)
    stripped = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    return "".join(ch.lower() for ch in unicodedata.normalize("NFC", stripped) if ch.isalnum())


def check_unique_country_names(countries_rows):
    """국가명은 완전 일치 조회의 유일 키다(countries.search_name / search_name_en — 앱은 둘을 OR 로 찾는다).
    두 언어의 키를 합쳐 겹치면 조회가 500 이 되므로 빌드에서 막는다."""
    seen = {}
    for row in countries_rows:
        for name in (row[1], row[2]):
            key = normalized_search_key(name)
            if key in seen and seen[key] != row[0]:
                sys.exit(f"오류: 국가명이 정규화 후 겹친다: {seen[key]} / {row[0]} ({name})")
            seen[key] = row[0]


def pruned_city_images(path, city_ids):
    """도시 데이터셋에 없는 도시의 사진 행을 뺀 내용 → (텍스트, 지운 행 수). 지운 게 있으면 버전에 +pruned 를
    붙여 재적재시킨다. 파일이 없거나 지울 게 없으면 (None, 0). 쓰기는 호출자가 다른 산출물과 함께 한다."""
    if not os.path.exists(path):
        return None, 0
    with open(path, encoding="utf-8") as f:
        lines = f.read().split("\n")
    kept, dropped, version_line = [], 0, None
    for line in lines:
        if line.startswith("#version"):
            version_line = line
            continue
        if not line.strip() or line.startswith("#"):
            kept.append(line)
            continue
        if line.split("\t", 1)[0] in city_ids:
            kept.append(line)
        else:
            dropped += 1
    if version_line is None or "\t" not in version_line:
        sys.exit(f"오류: {path} 에 #version 헤더가 없다")
    if dropped == 0:
        return None, 0
    version = version_line.split("\t", 1)[1].strip().removesuffix("+pruned")
    return f"#version\t{version}+pruned\n" + "\n".join(kept).rstrip("\n") + "\n", dropped


def write_tsv(path, version, header, rows):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(f"#version\t{version}\n")
        f.write("# " + "\t".join(header) + "\n")
        writer = csv.writer(f, delimiter="\t", lineterminator="\n", quoting=csv.QUOTE_NONE,
                            escapechar=None)
        for row in rows:
            writer.writerow(row)
    print(f"  생성: {path} ({len(rows)}건)")


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--version", default=None,
                        help="데이터셋 버전 문자열 (기본: cldr-geonames-wikidata-YYYYMMDD)")
    parser.add_argument("--work-dir", default=os.path.join(REPO_ROOT, "build", "location-dataset"),
                        help="원본 파일 캐시 디렉터리")
    parser.add_argument("--countries-only", action="store_true",
                        help="countries.tsv 만 다시 만든다 (cities.tsv 는 기존 파일을 그대로 둔다)")
    parser.add_argument("--min-population", type=int, default=DEFAULT_MIN_POPULATION,
                        help=f"도시 인구 하한 (기본 {DEFAULT_MIN_POPULATION:,}). 수도와 덮어쓰기 도시는 예외")
    parser.add_argument("--no-wikidata", action="store_true",
                        help="Wikidata 한국어 라벨을 쓰지 않는다 (GeoNames 와 덮어쓰기만)")
    parser.add_argument("--wikidata-endpoint", default=WIKIDATA_ENDPOINT, help="SPARQL 엔드포인트")
    parser.add_argument("--overrides", default=DEFAULT_OVERRIDES, help="표시명 덮어쓰기 TSV")
    args = parser.parse_args()

    version = args.version or "cldr-geonames-wikidata-" + datetime.date.today().strftime("%Y%m%d")

    print("1/5 국가 표시명 (CLDR ko·en)")
    country_names = {lang: load_country_names(args.work_dir, lang) for lang in LANGS}
    country_codes = set(country_names["ko"]) & set(country_names["en"])
    print(f"  국가 {len(country_codes)}개")
    overrides = load_overrides(args.overrides)

    print("2/5 도시 목록 (GeoNames cities15000)")
    geonames_rows = read_geonames_cities(args.work_dir)
    capitals = pick_capitals(geonames_rows)
    cities_path = os.path.join(OUT_DIR, "cities.tsv")
    if args.countries_only:
        cities = None
        city_ids = read_city_ids(cities_path)
        if not city_ids:
            sys.exit(f"오류: --countries-only 인데 {cities_path} 가 없다")
        print("  (--countries-only) 기존 cities.tsv 의 도시를 그대로 둔다")
    else:
        all_ids = {row[0] for row in geonames_rows}
        print("3/5 도시 한국어·영문명 (GeoNames alternateNames)")
        alt_names = load_alternate_names(args.work_dir, all_ids)
        print(f"  ko {len(alt_names['ko'])}건, en {len(alt_names['en'])}건")

        print("4/5 도시 한국어명 보강 (Wikidata)")
        if args.no_wikidata:
            wikidata_ko = {}
            print("  (--no-wikidata) 건너뜀")
        else:
            wikidata_ko = load_wikidata_korean_names(args.work_dir, all_ids, args.wikidata_endpoint)
            print(f"  Wikidata 한국어 라벨 {len(wikidata_ko)}건")

        cities, missing = build_cities(geonames_rows, country_names, alt_names, wikidata_ko, overrides,
                                       capitals, args.min_population)
        city_ids = {c[0] for c in cities}
        from_wikidata = sum(1 for c in cities if c[0] not in alt_names["ko"] and c[0] not in overrides)
        print(f"  도시 {len(cities)}개 (인구 {args.min_population:,} 이상 또는 수도·덮어쓰기, 한국어명 있음; "
              f"Wikidata 로 채운 것 {from_wikidata}개)")
        missing_path = os.path.join(args.work_dir, "missing-ko.tsv")
        with open(missing_path, "w", encoding="utf-8") as f:
            f.write("# cityId\tname\tcountryId\tpopulation — 한국어명이 없어 뺀 도시(인구 하한 이상). "
                    "여행지가 있으면 scripts/location-name-overrides.tsv 에 적는다\n")
            for row in missing:
                f.write("\t".join(str(v) for v in row) + "\n")
        print(f"  한국어명이 없어 뺀 도시 {len(missing)}개 → {missing_path}")

    print("5/5 국가 통화·수도 (CLDR currencyData, GeoNames PPLC)")
    currencies = load_currencies(args.work_dir)
    countries_rows = []
    for code in sorted(country_codes):
        override_ko, override_en = overrides.get(code, (None, None))
        capital = capitals.get(code, "")
        countries_rows.append((code,
                               override_ko or country_names["ko"][code],
                               override_en or country_names["en"][code],
                               capital if capital in city_ids else "",
                               *currencies.get(code, ("", ""))))
    print(f"  통화 {sum(1 for r in countries_rows if r[4])}개국, 수도 {sum(1 for r in countries_rows if r[3])}개국")

    # 검증은 반드시 쓰기 '전'이다. 탭이 섞인 값을 csv.writer 에 넘기면 quoting=QUOTE_NONE 설정
    # 때문에 쓰는 도중 _csv.Error 로 터지고, 잘린 TSV가 디스크에 남는다 — 그걸 막으려던 검사가
    # 뒤에 있으면 아무 소용이 없다. 빈 값도 여기서 잡는다(파서가 기동 시점에 거절한다).
    check_unique_country_names(countries_rows)
    # countries 의 뒤 세 열(수도·통화 코드·기호)은 비어 있을 수 있다 — 파서가 선택 열로 허용한다.
    for name, rows, required in (("countries", countries_rows, 3), ("cities", cities or [], 5)):
        for row in rows:
            for i, value in enumerate(row):
                text = str(value)
                if "\t" in text or "\n" in text or "\r" in text:
                    sys.exit(f"오류: {name} 데이터에 탭/개행이 포함된 값이 있다: {row}")
                if i < required and not text.strip():
                    sys.exit(f"오류: {name} 데이터에 빈 값이 있다: {row}")
            if not has_hangul(row[1]):
                sys.exit(f"오류: {name} 의 한국어명에 한글이 없다: {row}")

    write_tsv(os.path.join(OUT_DIR, "countries.tsv"), version,
              ["countryId", "nameKo", "nameEn", "capitalCityId", "currencyCode", "currencySymbol"],
              countries_rows)
    if cities is not None:
        images_path = os.path.join(OUT_DIR, "city_images.tsv")
        # 쓰기 전에 사진 파일까지 검사해 두면, 사진 파일 결함으로 도시만 바뀐 반쪽 산출물이 남지 않는다.
        pruned_images, dropped_images = pruned_city_images(images_path, city_ids)
        write_tsv(cities_path, version, ["cityId", "nameKo", "nameEn", "countryId", "population"], cities)
        if pruned_images is not None:
            with open(images_path, "w", encoding="utf-8", newline="\n") as f:
                f.write(pruned_images)
            print(f"  사진 데이터셋에서 데이터셋에 없는 도시의 사진 {dropped_images}건을 지웠다: {images_path}")
        else:
            print(f"  사진 데이터셋에 고아 행 없음: {images_path}")
    print("완료")


if __name__ == "__main__":
    main()
