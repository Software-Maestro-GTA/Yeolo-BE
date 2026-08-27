#!/usr/bin/env python3
"""국가·도시 기준 데이터셋(TSV) 생성 스크립트 (API-LOC-1 / API-LOC-2).

출처
  - 국가 한국어명: CLDR (unicode-org/cldr-json, ko/territories.json)
  - 도시 목록    : GeoNames cities15000 (인구 15,000명 이상)
  - 도시 한국어명: GeoNames alternateNamesV2 (isolanguage=ko)

산출물 (src/main/resources/data/locations/)
  countries.tsv : countryId  nameKo
  cities.tsv    : cityId  nameKo  countryId  population

사용법
  python3 scripts/build-location-dataset.py [--version <문자열>] [--work-dir <경로>]

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
    args = parser.parse_args()

    version = args.version or "cldr-geonames-" + datetime.date.today().strftime("%Y%m%d")

    print("1/3 국가 한국어명 (CLDR)")
    country_names = load_country_names(args.work_dir)
    print(f"  국가 {len(country_names)}개")

    print("2/3 도시 한국어명 (GeoNames alternateNames)")
    korean_names = load_korean_city_names(args.work_dir)
    print(f"  한국어 이름 {len(korean_names)}건")

    print("3/3 도시 목록 (GeoNames cities15000)")
    cities = load_cities(args.work_dir, country_names, korean_names)
    translated = sum(1 for c in cities if c[0] in korean_names)
    print(f"  도시 {len(cities)}개 (한국어명 {translated}개)")

    # 검증은 반드시 쓰기 '전'이다. 탭이 섞인 값을 csv.writer 에 넘기면 quoting=QUOTE_NONE 설정
    # 때문에 쓰는 도중 _csv.Error 로 터지고, 잘린 TSV가 디스크에 남는다 — 그걸 막으려던 검사가
    # 뒤에 있으면 아무 소용이 없다. 빈 값도 여기서 잡는다(파서가 기동 시점에 거절한다).
    countries_rows = sorted(country_names.items())
    for name, rows in (("countries", countries_rows), ("cities", cities)):
        for row in rows:
            for value in row:
                text = str(value)
                if "\t" in text or "\n" in text or "\r" in text:
                    sys.exit(f"오류: {name} 데이터에 탭/개행이 포함된 값이 있다: {row}")
                if not text.strip():
                    sys.exit(f"오류: {name} 데이터에 빈 값이 있다: {row}")

    write_tsv(os.path.join(OUT_DIR, "countries.tsv"), version, ["countryId", "nameKo"],
              countries_rows)
    write_tsv(os.path.join(OUT_DIR, "cities.tsv"), version,
              ["cityId", "nameKo", "countryId", "population"], cities)
    print("완료")


if __name__ == "__main__":
    main()
