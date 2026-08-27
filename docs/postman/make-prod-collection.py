"""dev 컬렉션을 기준으로 prod 전용 컬렉션을 생성한다.

dev 파일을 손으로 복사하면 어디를 바꿔야 하는지 매번 기억해야 하므로, 변환 규칙을 코드로 둔다.
릴리스(dev→main) 때 이 스크립트를 다시 돌리면 prod 컬렉션이 그 시점 계약으로 갱신된다.
"""
import json
import sys

SRC = 'docs/postman/Yeolo-BE-Dev.postman_collection.json'
DST = 'docs/postman/Yeolo-BE-Prod.postman_collection.json'

PROD_BASE_URL = 'https://api.yeolo.app'
PROD_ID = '9e0a1c7a-4f3b-4a21-8d55-yeolo000be02'  # dev 와 달라야 Postman 이 별개 컬렉션으로 본다

# prod 서버 설정이 dev 와 다른 변수는 **여기에** 적는다. 생성물(JSON)을 손으로 고치면
# 다음 릴리스에서 재생성될 때 조용히 되돌아가고, diff 에도 원인이 남지 않는다.
# 비어 있으면 dev 값을 그대로 물려받는다는 뜻이다.
PROD_OVERRIDES = {
    # 예) 'googleClientId': '...apps.googleusercontent.com',
    # prod/dev 비교: kubectl -n app     get secret was-secrets \
    #                  -o go-template='{{.data.GOOGLE_CLIENT_ID | base64decode}}{{"\n"}}'
}

DESCRIPTION = (
    "Yeolo 백엔드 API 컬렉션 — **prod(운영) 대상**.\n\n"
    "⚠️ **실사용자 데이터를 건드립니다.** dev 컬렉션과 같은 요청이지만 대상이 운영 서버입니다.\n"
    "쓰기 API(프로필 수정·MBTI·동의·코스 생성)는 즉시 영구 반영되고, 코스 삭제·회원탈퇴는 되돌릴 수 없습니다.\n\n"
    "- 파괴적 요청(회원탈퇴·코스 삭제)은 기본으로 **차단**돼 있습니다. 정말 보낼 때만 컬렉션 변수\n"
    "  `allowDestructive` 를 `yes` 로 바꾸고, 끝나면 반드시 되돌리세요.\n"
    "- 토큰은 로그인으로만 얻습니다(tokenMode=mint 는 사용하지 않음).\n"
    "- 이 파일은 **main 브랜치의 API 계약**을 반영합니다. dev 가 앞서 나간 기능은 여기 없는 것이 정상입니다.\n"
    "- 상세는 docs/postman/README.md.")

# 파괴적 요청 가드. allowDestructive 가 yes 가 아니면 요청 자체를 보내지 않는다.
#
# ⚠️ throw 가 주 차단 수단이다. pm.execution.skipRequest() 는 **Collection Runner·CLI·Flows
#    에서만** 동작하고, 개별 요청에서 Send 를 누르는 경로에는 효력이 없다. 이 컬렉션의 실제
#    사용법이 바로 그 수동 Send 이므로, skipRequest 를 먼저 호출하고 끝내면 DELETE 가 그대로
#    나간다. 그래서 skipRequest 는 러너용 보조로만 부르고, 마지막에 항상 throw 한다.
GUARD = [
    "// prod 파괴적 요청 가드 — 실사용자 데이터가 지워지는 것을 막는다.",
    "// 정말 실행하려면 컬렉션 변수 allowDestructive 를 yes 로 바꾸고, 끝나면 되돌린다.",
    "if (String(pm.collectionVariables.get('allowDestructive')).toLowerCase() !== 'yes') {",
    "    const msg = 'prod 파괴적 요청 차단됨 — allowDestructive=yes 로 바꾸면 실행됩니다.';",
    "    // 러너에서는 이걸로 '건너뜀'으로 깔끔히 표시되고, 개별 Send 에서는 아무 효과가 없다.",
    "    if (pm.execution && typeof pm.execution.skipRequest === 'function') {",
    "        pm.execution.skipRequest();",
    "    }",
    "    // 개별 Send 를 실제로 막는 것은 이 throw 다. 어떤 경로에서도 요청이 나가지 않는다.",
    "    throw new Error(msg);",
    "}",
]

# dev 컬렉션의 설명문에 박힌 dev 전용 지시를 prod 로 바꾼다. 네임스페이스가 대표적인데,
# `-n app-dev`(dev) 를 그대로 두면 "prod 컬렉션이 시키는 대로 했는데 dev 를 찔렀다"가 된다.
TEXT_SUBSTITUTIONS = [
    ('-n app-dev', '-n app'),
    ('api-dev.yeolo.app', 'api.yeolo.app'),
]


def is_destructive(item):
    req = item.get('request') or {}
    if req.get('method') != 'DELETE':
        return False
    url = req.get('url')
    raw = url if isinstance(url, str) else (url or {}).get('raw', '')
    return '/api/users/me' in raw or '/api/courses/' in raw


def add_guard(item):
    events = item.setdefault('event', [])
    for e in events:
        if e.get('listen') == 'prerequest':          # 기존 prerequest 가 있으면 앞에 끼운다
            e['script']['exec'] = GUARD + [''] + e['script'].get('exec', [])
            return
    events.insert(0, {
        'listen': 'prerequest',
        'script': {'type': 'text/javascript', 'exec': GUARD},
    })


def substitute_text(node):
    """설명문(description)의 dev 전용 문구를 prod 용으로 바꾼다."""
    if isinstance(node, dict):
        for k, v in node.items():
            if k == 'description' and isinstance(v, str):
                for old, new in TEXT_SUBSTITUTIONS:
                    v = v.replace(old, new)
                node[k] = v
            else:
                substitute_text(v)
    elif isinstance(node, list):
        for v in node:
            substitute_text(v)


def walk(items):
    for it in items:
        if 'item' in it:
            walk(it['item'])
        elif is_destructive(it):
            add_guard(it)
            print(f"  가드 추가: {it['name']}")


def main():
    with open(SRC, encoding='utf-8') as f:
        col = json.load(f)

    col['info']['_postman_id'] = PROD_ID
    col['info']['name'] = 'Yeolo BE (prod)'
    col['info']['description'] = DESCRIPTION

    seen = set()
    for v in col.get('variable', []):
        seen.add(v['key'])
        if v['key'] in PROD_OVERRIDES:
            v['value'] = PROD_OVERRIDES[v['key']]
        elif v['key'] == 'baseUrl':
            v['value'] = PROD_BASE_URL
        elif v['key'] in ('accessToken', 'refreshToken', 'userId', 'courseId', 'placeId',
                          'shareToken', 'shareUrl', 'googleAuthCode', 'appleAuthCode',
                          'appleIdToken', 'internalApiKey', 'jwtSecret'):
            v['value'] = ''                          # 세션값·시크릿은 비운 채로 커밋
    if 'allowDestructive' not in seen:
        col.setdefault('variable', []).append(
            {'key': 'allowDestructive', 'value': 'no', 'type': 'string'})

    substitute_text(col['item'])
    walk(col['item'])

    with open(DST, 'w', encoding='utf-8') as f:
        json.dump(col, f, ensure_ascii=False, indent='\t')
        f.write('\n')
    print(f"생성: {DST}")


if __name__ == '__main__':
    sys.exit(main())
