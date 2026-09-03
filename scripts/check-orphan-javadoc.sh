#!/usr/bin/env bash
# 선언에 못 붙은 javadoc(고아 주석)을 찾는다.
#
# javadoc 블록이 둘 연달아 있으면 자바는 **뒤엣것만** 선언에 붙이고 앞 블록은
# 조용히 버린다 — 사이에 빈 줄이 있어도 마찬가지다. 컴파일도 통과하고 경고도
# 없어서, 공들여 쓴 주석이 IDE 호버·생성 문서 어디에도 안 뜨는 채로 남는다.
# 2026-09-03 에 7곳이 그 상태였다(주문 도메인에 몰려 있었다).
#
# 생기는 경위는 둘이다:
#   ① 새 메서드·상수를 **기존 주석과 선언 사이에** 끼워 넣었다 (Order#cancelItem)
#   ② 딸린 선언이 다른 파일로 옮겨 가고 주석만 남았다 (OrderService 의 DETAIL_MAX)
# 그래서 고치는 법도 「지운다」가 아니라 대개 **붙을 자리로 옮긴다** 이다.
#
# ⚠ `/* */`(일반 블록주석)로 쓴 구역 머리말은 정상이라 세지 않는다 — 필드 여러 개를
#   묶어 설명하는 자리는 그렇게 쓰는 것이 맞다. 블록 종류를 구분하지 않고 닫는 `*/`
#   만 세면 그것들이 전부 오검출된다(첫 판이 그렇게 틀렸다).
#
# 종료코드: 0 = 없음, 1 = 발견
set -uo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-$REPO_DIR/glassvue-backend}"

python3 - "$TARGET" <<'PY'
import io, os, sys

root = sys.argv[1]
files = []
for base in ('src/main', 'src/test'):
    for d, _, names in os.walk(os.path.join(root, base)):
        files += [os.path.join(d, n) for n in names if n.endswith('.java')]

hits = []
for f in sorted(files):
    in_block = False; is_jd = False; start = 0
    pending = 0                       # 닫힌 직후의 javadoc 시작 줄 (0 = 없음)
    for i, ln in enumerate(io.open(f, encoding='utf-8'), 1):
        s = ln.strip()
        if in_block:
            if '*/' in s:
                in_block = False
                pending = start if is_jd else 0
            continue
        if not s:
            continue                  # 빈 줄은 건너뛴다 (pending 유지)
        if s.startswith('/**'):
            if pending:
                hits.append((f, pending, i))
            start = i
            if s.endswith('*/') and len(s) > 3:
                pending = i           # 한 줄짜리 javadoc: 열고 그 줄에서 닫힌다
            else:
                in_block = True; is_jd = True; pending = 0
        elif s.startswith('/*'):
            start = i
            if not s.endswith('*/'):
                in_block = True; is_jd = False
            pending = 0               # 일반 블록주석은 pending 을 소멸시킨다
        else:
            pending = 0               # 선언·애노테이션이 주석을 소비했다

for f, orphan, eaten_by in hits:
    print("%s:%d  선언에 안 붙는다 (%d 번 줄 javadoc 이 가린다)"
          % (os.path.relpath(f, root), orphan, eaten_by))

if hits:
    print("\n고아 javadoc %d 건. 대개 «붙을 선언 바로 위로 옮긴다» 로 고친다." % len(hits))
    sys.exit(1)
print("고아 javadoc 없음.")
PY
