#!/usr/bin/env python3
"""국가·도시 기준 데이터셋(TSV) 생성 스크립트 (API-LOC-1 / API-LOC-2).

출처
  - 국가 한국어명: CLDR (unicode-org/cldr-json, ko/territories.json)
  - 국가 통화    : CLDR supplemental/currencyData.json (영토→통화) + ko/currencies.json (기호)
  - 국가 수도    : GeoNames cities15000 의 feature code PPLC (여럿이면 인구 최다)
  - 도시 목록    : GeoNames cities15000 (인구 15,000명 이상)
  - 도시 한국어명: GeoNames alternateNamesV2 (isolanguage=ko)

산출물 (src/main/resources/data/locations/)
  countries.tsv : countryId  nameKo  capitalCityId  currencyCode  currencySymbol
                  (뒤 세 열은 비어 있을 수 있다 — 남극처럼 통화가 없거나 수도가 cities15000 에 없는 경우)
  cities.tsv    : cityId  nameKo  countryId  population

통화 기호는 CLDR 한국어 좁은 기호(symbol-alt-narrow: ¥·$·฿)를 우선하고, 없으면 일반 기호, 그것도
없으면 ISO 코드를 쓴다. 영토에 통용 통화가 여럿이면(파나마 PAB·USD 등) CLDR 이 먼저 적은 쪽을 쓴다.
코스 상세의 exRateIcon(API-COURSE-2, #100)이 이 기호를 그대로 내려보낸다.

사용법
  python3 scripts/build-location-dataset.py [--version <문자열>] [--work-dir <경로>] [--countries-only]

  --countries-only 는 countries.tsv 만 다시 만든다(200MB 짜리 alternateNamesV2 를 받지 않는다).
  국가만 바뀌어도 앱은 도시를 함께 재적재하므로(country_name_ko 사본) cities.tsv 는 그대로 둬도 된다.

alternateNamesV2.zip 이 200MB 가량이라 최초 실행은 시간이 걸린다. 작업 디렉터리에 받아 둔 파일이
있으면 다시 받지 않는다. 표준 라이브러리만 사용한다.
"""

import argparse
import csv
import datetime
import io
import json
import os
import sys
import urllib.request
import zipfile

CLDR_TERRITORIES_KO = (
    "https://raw.githubusercontent.com/unicode-org/cldr-json/main/"
    "cldr-json/cldr-localenames-full/main/ko/territories.json"
)
CLDR_CURRENCY_DATA = (
    "https://raw.githubusercontent.com/unicode-org/cldr-json/main/"
    "cldr-json/cldr-core/supplemental/currencyData.json"
)
CLDR_CURRENCIES_KO = (
    "https://raw.githubusercontent.com/unicode-org/cldr-json/main/"
    "cldr-json/cldr-numbers-full/main/ko/currencies.json"
)
GEONAMES_CITIES = "https://download.geonames.org/export/dump/cities15000.zip"
GEONAMES_ALT_NAMES = "https://download.geonames.org/export/dump/alternateNamesV2.zip"

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO_ROOT, "src", "main", "resources", "data", "locations")

# CLDR 은 국가가 아닌 지역(대륙·"세계"·EU 등)도 같은 파일에 담는다. 국가 코드는 알파벳 2글자이므로
# 숫자 코드(001=세계, 150=유럽 …)를 걸러내면 대부분 정리된다. 아래는 알파벳 2글자지만 ISO 3166-1
# 국가가 아니거나(EU/EZ/UN) 여행지로서 의미가 없는 코드다.
NON_COUNTRY_CODES = {"EU", "EZ", "UN", "QO", "ZZ", "XA", "XB"}


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


def load_country_names(work_dir):
    """CLDR ko/territories.json → {ISO alpha-2: 한국어 국가명}"""
    path = download(CLDR_TERRITORIES_KO, os.path.join(work_dir, "territories-ko.json"))
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    territories = data["main"]["ko"]["localeDisplayNames"]["territories"]

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


def load_capitals(work_dir, known_city_ids):
    """cities15000 → {ISO alpha-2: 수도 geonameid}. 여럿이면(남아공 등) 인구가 가장 많은 쪽."""
    path = download(GEONAMES_CITIES, os.path.join(work_dir, "cities15000.zip"))
    best = {}
    with zipfile.ZipFile(path) as z, z.open("cities15000.txt") as raw:
        for line in io.TextIOWrapper(raw, encoding="utf-8"):
            cols = line.rstrip("\n").split("\t")
            if len(cols) < 15 or cols[7] != "PPLC" or cols[0] not in known_city_ids:
                continue
            population = int(cols[14] or 0)
            current = best.get(cols[8])
            if current is None or population > current[0]:
                best[cols[8]] = (population, cols[0])
    return {code: city_id for code, (_, city_id) in best.items()}


def read_city_ids(path):
    ids = set()
    if not os.path.exists(path):
        return ids
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line and not line.startswith("#"):
                ids.add(line.split("\t", 1)[0])
    return ids


def load_korean_city_names(work_dir):
    """alternateNamesV2 → {geonameid: 한국어 도시명}

    한 도시에 한국어 이름이 여럿일 수 있어 우선순위를 둔다: preferred > 축약(short) > 일반.

    축약형을 일반 이름보다 위에 두는 게 맞다. GeoNames의 isShortName은 행정 접미사를 뺀 통용
    명칭이라("뉴욕" vs "뉴욕 시", "워싱턴 D.C." vs "워싱턴 DC") 자동완성 후보로도 표시 문구로도
    그쪽이 낫다. 이 값이 그대로 FE에 나가고 검색 키의 근거가 되므로 우선순위를 바꾸지 말 것.

    historic(옛 이름)·colloquial(속칭)은 제외한다 — 자동완성 후보로 부적절하다.
    """
    path = download(GEONAMES_ALT_NAMES, os.path.join(work_dir, "alternateNamesV2.zip"))
    best = {}  # geonameid -> (preferred, name)
    with zipfile.ZipFile(path) as z:
        member = next(n for n in z.namelist() if n.endswith("alternateNamesV2.txt"))
        with z.open(member) as raw:
            for line in io.TextIOWrapper(raw, encoding="utf-8"):
                cols = line.rstrip("\n").split("\t")
                if len(cols) < 8 or cols[2] != "ko":
                    continue
                geoname_id, name = cols[1], cols[3]
                is_preferred = cols[4] == "1"
                is_short = cols[5] == "1"
                is_colloquial = cols[6] == "1"
                is_historic = cols[7] == "1"
                if is_colloquial or is_historic or not name:
                    continue
                rank = 2 if is_preferred else (1 if is_short else 0)
                current = best.get(geoname_id)
                if current is None or rank > current[0]:
                    best[geoname_id] = (rank, name)
    return {gid: name for gid, (_, name) in best.items()}


def load_cities(work_dir, country_names, korean_names):
    """cities15000 → [(cityId, nameKo, countryId, population)]"""
    path = download(GEONAMES_CITIES, os.path.join(work_dir, "cities15000.zip"))
    rows = []
    with zipfile.ZipFile(path) as z:
        with z.open("cities15000.txt") as raw:
            for line in io.TextIOWrapper(raw, encoding="utf-8"):
                cols = line.rstrip("\n").split("\t")
                if len(cols) < 15:
                    continue
                geoname_id, name, ascii_name = cols[0], cols[1], cols[2]
                country_code, population = cols[8], cols[14]
                if country_code not in country_names:
                    continue
                # 한국어 이름이 없으면 원문 표기로 둔다. 검색 키는 소문자·구분자 제거라
                # 라틴 표기도 그대로 검색된다(초성 검색만 불가).
                display = korean_names.get(geoname_id) or name or ascii_name
                if not display:
                    continue
                rows.append((geoname_id, display, country_code, int(population or 0)))
    # 인구 많은 순으로 정렬해 두면 파일 diff 를 볼 때도 주요 도시가 위에 온다.
    rows.sort(key=lambda r: (-r[3], r[1]))
    return rows


def normalized_search_key(name):
    """앱의 SearchKeys.normalize 와 같은 규칙: 발음 부호 제거 → 소문자 → 영숫자·한글만."""
    import unicodedata
    decomposed = unicodedata.normalize("NFD", name)
    stripped = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    return "".join(ch.lower() for ch in unicodedata.normalize("NFC", stripped) if ch.isalnum())


def check_unique_country_names(countries_rows):
    """국가명은 완전 일치 조회의 유일 키다(countries.search_name). 겹치면 앱 조회가 500 이 되므로 빌드에서 막는다."""
    seen = {}
    for row in countries_rows:
        key = normalized_search_key(row[1])
        if key in seen:
            sys.exit(f"오류: 국가명이 정규화 후 겹친다: {seen[key]} / {row[0]} ({row[1]})")
        seen[key] = row[0]


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
                        help="데이터셋 버전 문자열 (기본: cldr-geonames-YYYYMMDD)")
    parser.add_argument("--work-dir", default=os.path.join(REPO_ROOT, "build", "location-dataset"),
                        help="원본 파일 캐시 디렉터리")
    parser.add_argument("--countries-only", action="store_true",
                        help="countries.tsv 만 다시 만든다 (cities.tsv 는 기존 파일을 그대로 둔다)")
    args = parser.parse_args()

    version = args.version or "cldr-geonames-" + datetime.date.today().strftime("%Y%m%d")

    print("1/4 국가 한국어명 (CLDR)")
    country_names = load_country_names(args.work_dir)
    print(f"  국가 {len(country_names)}개")

    cities_path = os.path.join(OUT_DIR, "cities.tsv")
    if args.countries_only:
        cities = None
        city_ids = read_city_ids(cities_path)
        if not city_ids:
            sys.exit(f"오류: --countries-only 인데 {cities_path} 가 없다")
    else:
        print("2/4 도시 한국어명 (GeoNames alternateNames)")
        korean_names = load_korean_city_names(args.work_dir)
        print(f"  한국어 이름 {len(korean_names)}건")

        print("3/4 도시 목록 (GeoNames cities15000)")
        cities = load_cities(args.work_dir, country_names, korean_names)
        translated = sum(1 for c in cities if c[0] in korean_names)
        print(f"  도시 {len(cities)}개 (한국어명 {translated}개)")
        city_ids = {c[0] for c in cities}

    print("4/4 국가 통화·수도 (CLDR currencyData, GeoNames PPLC)")
    currencies = load_currencies(args.work_dir)
    capitals = load_capitals(args.work_dir, city_ids)
    countries_rows = [
        (code, name, capitals.get(code, ""), *currencies.get(code, ("", "")))
        for code, name in sorted(country_names.items())
    ]
    print(f"  통화 {sum(1 for r in countries_rows if r[3])}개국, 수도 {sum(1 for r in countries_rows if r[2])}개국")

    # 검증은 반드시 쓰기 '전'이다. 탭이 섞인 값을 csv.writer 에 넘기면 quoting=QUOTE_NONE 설정
    # 때문에 쓰는 도중 _csv.Error 로 터지고, 잘린 TSV가 디스크에 남는다 — 그걸 막으려던 검사가
    # 뒤에 있으면 아무 소용이 없다. 빈 값도 여기서 잡는다(파서가 기동 시점에 거절한다).
    check_unique_country_names(countries_rows)
    # countries 의 뒤 세 열(수도·통화 코드·기호)은 비어 있을 수 있다 — 파서가 선택 열로 허용한다.
    for name, rows, required in (("countries", countries_rows, 2), ("cities", cities or [], 4)):
        for row in rows:
            for i, value in enumerate(row):
                text = str(value)
                if "\t" in text or "\n" in text or "\r" in text:
                    sys.exit(f"오류: {name} 데이터에 탭/개행이 포함된 값이 있다: {row}")
                if i < required and not text.strip():
                    sys.exit(f"오류: {name} 데이터에 빈 값이 있다: {row}")

    write_tsv(os.path.join(OUT_DIR, "countries.tsv"), version,
              ["countryId", "nameKo", "capitalCityId", "currencyCode", "currencySymbol"],
              countries_rows)
    if cities is not None:
        write_tsv(cities_path, version, ["cityId", "nameKo", "countryId", "population"], cities)
    print("완료")


if __name__ == "__main__":
    main()
