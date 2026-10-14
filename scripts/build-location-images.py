#!/usr/bin/env python3
"""도시 대표 사진 데이터셋(TSV + 이미지 파일) 생성 스크립트 (API-PREF-4 recommendCity.imageUrl, #97).

출처
  - 사진        : Pixabay API (https://pixabay.com/api/docs/) — Content License 는 저작자 표시가
                  필요 없고, API 규칙은 핫링크를 금지하는 대신 "쓰려면 서버에 내려받으라"고 한다.
                  그래서 URL 이 아니라 **파일을 받아 우리 S3 에 올린다**. 출처 필드는 API 에 내보내지
                  않는다(사용자 결정, docs/spec-index.md). Wikidata 등 저작자 표시가 필요한 출처는
                  한 장이라도 섞지 않는다 — 섞는 순간 출처 필드가 다시 필요해진다.
  - 도시 영문명 : GeoNames cities15000 (asciiname) — Pixabay 검색어로 쓴다
  - 국가 영문명 : CLDR en/territories.json — 검색어에 붙여 동명 도시를 가른다

산출물
  src/main/resources/data/locations/city_images.tsv : cityId  s3Key  source  sourceId
  <work-dir>/images/<cityId>.jpg                     : S3 에 올릴 파일 (저장소에는 넣지 않는다)

대상
  cities.tsv 의 도시 중 인구 --min-population(기본 200,000) 이상 + 각국 수도(GeoNames PPLC).
  실측(2026-10-14, 400개 표본): 도시명이 태그에 있는 사진은 100만 이상 도시 77%, 10만 이상 41%,
  관광 도시 40곳 100%, 수도 77%. 롱테일은 약하므로 폴백(도시 → 수도 → 빈 문자열)은 앱이 맡는다.

선정 규칙 (Pixabay 검색은 퍼지 매칭이라 결과 수만 믿으면 안 된다 — "Lisala Congo" 가 콩고 새 사진을 돌려준다)
  1. "<도시 영문명> <국가 영문명>" 으로 검색 (photo, 가로, safesearch, 인기순, 100건)
  2. 태그에 도시명이 **단어 단위로 완전히** 들어 있는 사진만 후보 (부분 일치는 "Aba" 가 "Aba
     prefecture" 에 걸리는 오탐을 낸다)
  3. 후보 중 경관 키워드(city/skyline/temple/…) 태그가 있는 것을 우선, 그다음 좋아요 순
  4. 없으면 그 도시는 TSV 에 넣지 않는다 (앱이 수도로 폴백)

수동 덮어쓰기
  TSV 에서 source 가 pixabay 가 아닌 행(예: manual)은 다시 검색하지 않고 그대로 보존한다. 관광
  상위 도시의 사진이 마음에 들지 않으면 그 행의 sourceId 를 바꾸고 이미지를 직접 올린 뒤 source 를
  manual 로 적는다.

사용법
  PIXABAY_API_KEY=... python3 scripts/build-location-images.py [--min-population 200000]
      [--work-dir build/location-images] [--limit N] [--upload s3://<bucket>/locations/cities]

  --upload 를 주면 끝에 `aws s3 sync` 로 올린다(AWS CLI 와 자격증명 필요). 주지 않으면 파일만 남기므로
  나중에 직접 올린다:  aws s3 sync <work-dir>/images s3://<bucket>/locations/cities --content-type image/jpeg

  Pixabay 는 분당 100회 제한이라 3,000개 도시에 약 1시간 걸린다. 받은 이미지와 검색 결과는 work-dir 에
  캐시되어 중단 후 다시 실행하면 이어서 한다. 표준 라이브러리만 사용한다.
"""

import argparse
import csv
import datetime
import io
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

GEONAMES_CITIES = "https://download.geonames.org/export/dump/cities15000.zip"
CLDR_TERRITORIES_EN = (
    "https://raw.githubusercontent.com/unicode-org/cldr-json/main/"
    "cldr-json/cldr-localenames-full/main/en/territories.json"
)
PIXABAY_API = "https://pixabay.com/api/"
USER_AGENT = "Yeolo-BE location-images builder"

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(REPO_ROOT, "src", "main", "resources", "data", "locations")
CITIES_TSV = os.path.join(DATA_DIR, "cities.tsv")
OUT_TSV = os.path.join(DATA_DIR, "city_images.tsv")
S3_KEY_PREFIX = "locations/cities"
SOURCE_PIXABAY = "pixabay"

# 경관 사진을 우선하기 위한 태그 키워드. 하나라도 태그에 포함되면 "경관" 후보로 본다.
SCENERY_WORDS = (
    "city", "town", "cityscape", "skyline", "architecture", "landmark", "building", "old town",
    "downtown", "temple", "shrine", "castle", "palace", "cathedral", "church", "mosque", "bridge",
    "tower", "street", "square", "monument", "harbor", "harbour", "port", "river", "lake", "beach",
    "coast", "mountain", "aerial", "panorama", "night", "travel", "tourism", "park", "ruins",
)
REQUEST_INTERVAL_SEC = 0.65  # 분당 100회 제한 아래로


def download(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return dest
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120) as resp, open(dest, "wb") as f:
        while chunk := resp.read(1 << 20):
            f.write(chunk)
    return dest


def load_geonames(work_dir):
    """cities15000 → {geonameid: (asciiname, countryCode, isCapital)}"""
    path = download(GEONAMES_CITIES, os.path.join(work_dir, "cities15000.zip"))
    result = {}
    with zipfile.ZipFile(path) as z, z.open("cities15000.txt") as raw:
        for line in io.TextIOWrapper(raw, encoding="utf-8"):
            cols = line.rstrip("\n").split("\t")
            if len(cols) < 15:
                continue
            result[cols[0]] = (cols[2] or cols[1], cols[8], cols[7] == "PPLC")
    return result


def load_country_names_en(work_dir):
    path = download(CLDR_TERRITORIES_EN, os.path.join(work_dir, "territories-en.json"))
    with open(path, encoding="utf-8") as f:
        territories = json.load(f)["main"]["en"]["localeDisplayNames"]["territories"]
    return {code: name for code, name in territories.items() if "-alt-" not in code}


def read_tsv(path):
    rows = []
    if not os.path.exists(path):
        return rows
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            rows.append(line.split("\t"))
    return rows


def select_targets(min_population, geonames):
    targets = []
    for city_id, _name_ko, _country, population in read_tsv(CITIES_TSV):
        info = geonames.get(city_id)
        if info is None:
            continue
        if int(population) >= min_population or info[2]:
            targets.append(city_id)
    return targets


def pixabay_search(api_key, query, cache_path):
    """검색 결과를 캐시해 재실행 시 요청을 반복하지 않는다."""
    if os.path.exists(cache_path):
        with open(cache_path, encoding="utf-8") as f:
            return json.load(f)
    params = {
        "key": api_key, "q": query[:100], "image_type": "photo", "orientation": "horizontal",
        "safesearch": "true", "order": "popular", "per_page": 100,
    }
    url = PIXABAY_API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                hits = json.load(resp).get("hits", [])
            break
        except urllib.error.HTTPError as e:
            if e.code == 429:  # 분당 제한 — 한 번 쉬고 다시
                time.sleep(60)
                continue
            if attempt == 3:
                raise
            time.sleep(5)
        except OSError:   # URLError·socket.timeout 모두 OSError 다 (3.9 의 socket.timeout 은 TimeoutError 가 아니다)
            if attempt == 3:
                raise
            time.sleep(5)
    else:
        # 재시도를 다 써도 429 면 결과를 캐시하지 않는다 — 빈 결과로 남기면 "사진 없는 도시"로 굳어진다.
        raise RuntimeError(f"Pixabay 요청이 계속 429 다: {query}")
    os.makedirs(os.path.dirname(cache_path), exist_ok=True)
    with open(cache_path, "w", encoding="utf-8") as f:
        json.dump(hits, f, ensure_ascii=False)
    time.sleep(REQUEST_INTERVAL_SEC)
    return hits


def name_matches(tag, name):
    """태그 안에 도시명이 단어 단위로 완전히 들어 있는가. ("tokyo", "tokyo tower" ○ / "aba prefecture" 의 "ab" ×)"""
    words = tag.split()
    name_words = name.split()
    n = len(name_words)
    return any(words[i:i + n] == name_words for i in range(len(words) - n + 1))


def pick(hits, ascii_name):
    name = ascii_name.lower()
    # "New York City" 로 검색해도 태그는 "new york" 인 경우가 많다 — 행정 접미사를 뗀 이름도 허용.
    names = {name}
    for suffix in (" city", " shi", " si"):
        if name.endswith(suffix):
            names.add(name[: -len(suffix)])
    scenery, name_only = [], []
    for hit in hits:
        tags = [t.strip().lower() for t in hit.get("tags", "").split(",")]
        if not any(name_matches(t, n) for t in tags for n in names):
            continue
        if any(w in t for t in tags for w in SCENERY_WORDS):
            scenery.append(hit)
        else:
            name_only.append(hit)
    for bucket in (scenery, name_only):
        if bucket:
            return max(bucket, key=lambda h: h.get("likes", 0))
    return None


def image_urls(hit):
    """내려받을 URL 후보(우선순위순).

    API 가 주는 largeImageURL/webformatURL 은 pixabay.com/get/ 경로라 연속 요청에 429 가 난다(실측).
    같은 사진의 CDN 경로(previewURL 의 _150 → _1280/_640)는 제한 없이 받히므로 그쪽을 먼저 쓴다.
    """
    urls = []
    preview = hit.get("previewURL", "")
    if preview.endswith("_150.jpg"):
        urls += [preview[: -len("_150.jpg")] + "_1280.jpg", preview[: -len("_150.jpg")] + "_640.jpg"]
    urls += [u for u in (hit.get("largeImageURL"), hit.get("webformatURL")) if u]
    return urls


def fetch_image(urls, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return
    tmp = dest + ".part"
    last = None
    for url in urls:
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(req, timeout=60) as resp, open(tmp, "wb") as f:
                if not resp.headers.get("Content-Type", "").startswith("image/"):
                    raise ValueError("이미지가 아닌 응답: " + resp.headers.get("Content-Type", ""))
                while chunk := resp.read(1 << 20):
                    f.write(chunk)
            os.replace(tmp, dest)
            return
        except Exception as e:  # 다음 후보 URL 로
            last = e
    raise RuntimeError(f"모든 URL 실패: {last}")


def write_tsv(path, version, rows):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(f"#version\t{version}\n")
        f.write("# cityId\ts3Key\tsource\tsourceId\n")
        writer = csv.writer(f, delimiter="\t", lineterminator="\n", quoting=csv.QUOTE_NONE,
                            escapechar=None)
        for row in rows:
            writer.writerow(row)


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--min-population", type=int, default=200_000)
    parser.add_argument("--work-dir", default=os.path.join(REPO_ROOT, "build", "location-images"))
    parser.add_argument("--limit", type=int, default=None, help="앞에서 N개 도시만 처리(점검용)")
    parser.add_argument("--version", default=None,
                        help="데이터셋 버전 (기본: pixabay-YYYYMMDD-<행 수>; 같은 날 재생성해도 행 수가 다르면 재적재된다)")
    parser.add_argument("--upload", default=None, help="s3://<bucket>/<prefix> — 끝에 aws s3 sync")
    args = parser.parse_args()

    api_key = os.environ.get("PIXABAY_API_KEY")
    if not api_key:
        sys.exit("PIXABAY_API_KEY 환경변수가 필요하다 (커밋하지 말 것)")

    images_dir = os.path.join(args.work_dir, "images")
    cache_dir = os.path.join(args.work_dir, "search-cache")
    os.makedirs(images_dir, exist_ok=True)

    print("1/3 원본 참조 데이터")
    geonames = load_geonames(args.work_dir)
    country_en = load_country_names_en(args.work_dir)
    targets = select_targets(args.min_population, geonames)
    if args.limit:
        targets = targets[: args.limit]
    print(f"  대상 도시 {len(targets)}개 (인구 {args.min_population:,} 이상 + 수도)")

    # 기존 TSV 행은 전부 출발점으로 삼는다. 수동 지정(manual) 행은 다시 검색하지 않고, pixabay 행은 이번
    # 대상이면 다시 계산해 덮어쓴다. --limit 로 일부만 돌려도 나머지 행이 사라지지 않는다. 도시 데이터셋에서
    # 빠진 도시의 사진 행은 앱 로더가 고아로 걸러내므로 여기서 지우지 않아도 된다.
    existing = {row[0]: row for row in read_tsv(OUT_TSV) if len(row) == 4}
    manual = {k for k, row in existing.items() if row[2] != SOURCE_PIXABAY}
    if existing:
        print(f"  기존 {len(existing)}건 (수동 지정 {len(manual)}건 보존)")

    print("2/3 Pixabay 검색·다운로드")
    rows, found, started = dict(existing), 0, time.time()
    for i, city_id in enumerate(targets, 1):
        if city_id in manual:
            continue
        ascii_name, cc, _ = geonames[city_id]
        query = f"{ascii_name} {country_en.get(cc, cc)}"
        hits = pixabay_search(api_key, query, os.path.join(cache_dir, f"{city_id}.json"))
        hit = pick(hits, ascii_name)
        if hit is None:
            rows.pop(city_id, None)  # 전에는 있었는데 이번 규칙으로 빠졌다면 제거
            continue
        dest = os.path.join(images_dir, f"{city_id}.jpg")
        try:
            fetch_image(image_urls(hit), dest)
        except Exception as e:  # 한 장 실패로 전체를 멈추지 않는다
            print(f"  이미지 다운로드 실패 {city_id} ({ascii_name}): {e}")
            continue
        rows[city_id] = [city_id, f"{S3_KEY_PREFIX}/{city_id}.jpg", SOURCE_PIXABAY, str(hit["id"])]
        found += 1
        if i % 100 == 0:
            print(f"  {i}/{len(targets)} 처리, 사진 {found}건, {time.time() - started:.0f}초", flush=True)
    print(f"  사진 확보 {found}건 / 대상 {len(targets)}개 ({100 * found // max(len(targets), 1)}%)")

    print("3/3 TSV 작성")
    ordered = [rows[k] for k in sorted(rows, key=int)]
    # 날짜만으로는 같은 날 다시 만든 더 큰 데이터셋이 같은 버전이 되어 로더가 건너뛴다. 행 수를 붙인다.
    version = args.version or "pixabay-%s-%d" % (datetime.date.today().strftime("%Y%m%d"), len(ordered))
    for row in ordered:
        for value in row:
            if "\t" in value or "\n" in value or not value.strip():
                sys.exit(f"오류: 탭/개행/빈 값이 있다: {row}")
    write_tsv(OUT_TSV, version, ordered)
    print(f"  생성: {OUT_TSV} ({len(ordered)}건, version={version})")

    if args.upload:
        print(f"업로드: aws s3 sync {images_dir} {args.upload}")
        subprocess.run(["aws", "s3", "sync", images_dir, args.upload.rstrip("/"),
                        "--content-type", "image/jpeg", "--exclude", "*.part"], check=True)
    else:
        print(f"업로드는 직접: aws s3 sync {images_dir} s3://<bucket>/{S3_KEY_PREFIX} --content-type image/jpeg")
    print("완료")


if __name__ == "__main__":
    main()
