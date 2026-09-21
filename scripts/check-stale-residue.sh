#!/usr/bin/env bash
# 핸드오프 **§조건부 잔여**가 지목한 «고유명»이 **지금도 실물과 맞는지** 대조한다.
#
# 왜 이게 필요한가 (2026-09-21): §조건부 잔여는 2026-08-27 이후 **아홉 개 문서 연속**으로
# «그대로 물려받는다» 한 줄이었다. 그날 서른여섯 개 주장을 손으로 판정하니 **일곱이 거짓**이었고,
# 그중 셋은 «적힌 시점에 이미» 거짓이었다:
#   · 「16번의 미결제 주문 둘」(20260813-4185·4186) → 08-13 07:30 에 이미 CANCELLED (5주간 오기)
#   · 「20260826-6187 은 ORDERED·미결제」        → 08-27 01:18 에 RETURNED (문서를 닫기 전에 바뀌었다)
#   · 「SignupView 의 border-ink-200」           → 파일에 0건
#
# 🔴 **왜 규약이 아니라 스크립트인가**: WA §1-3·§1-3-1 은 *«집을 때 확인해라»* 이고, 이 병은
#   *«아무도 안 집으면 영원히 판정 안 된다»* 다. 규약은 «집는 사람» 에게만 닿는다.
#   그리고 §4-0(check-handoff.sh)·§5(check-deploy-branch.sh) 둘 다 **규약을 올린 직후 재발해서**
#   스크립트가 된 자리다 — 같은 판단을 따른다.
#
# ── WA §3-6 이 요구하는 셋 ──────────────────────────────────
# ① **무엇을 세나**: §조건부 잔여 본문에서 «실물과 대조 가능한 고유명» 셋만 뽑는다.
#      주문번호 `\d{8}-\d{4}`  ·  백틱 안의 파일 이름  ·  백틱 안의 식별자(클래스·메서드·컬럼·CSS)
#    ⚠ 서술형 주장(「캐시 60초 지연 미실측」·「화면이 유일한 방어」)은 **여기서 안 센다** —
#      기계가 못 갈라서다. 그건 사람이 판정한다(2026-09-21 §2-3 이 그 예다).
# ② **어디서 세나**: 🔴 **실물에서 꺼낸다.** 주문번호는 **DB**, 파일은 **파일시스템**, 식별자는
#    **코드 트리 grep**. 문서끼리 대조하지 않는다(그러면 같이 늙는다).
# ③ **다 셌는지 어떻게 아나**: 🔴 **뽑은 고유명이 0개면 «어긋남 없음» 이 아니라 «판정 불가»(2) 다.**
#    파서가 깨지거나 절 제목이 바뀌면 조용히 «전부 성립» 이 나온다 — §3-6 의 ②·⑤·⑦ 이 그 모양이다.
#    그리고 **몇 개를 봤는지 늘 찍는다**(*"이 도구가 지금 몇 개를 봤나?"*).
#
# 🔴 **첫 판은 «과거 거짓» 을 하나도 못 잡았다**(2026-09-21). 2026-08-27 문서에 돌려 보니 50개를
#    대조하고 «어긋남 없다» 를 냈는데, 그날 손으로 찾은 넷이 전부 이 도구의 눈 밖이었다.
#    **왜 놓쳤는지가 이 스크립트의 설계 전부다** — 셋을 고쳐서 지금 판이 됐다:
#      ⓐ **주장이 번호 «앞» 에 있었다** — 「**미결제** 주문 둘(4185·4186)」. 번호 뒤만 보면 안 보인다.
#         → 번호마다 «제 뒤» 를 먼저 보고, 그 묶음의 **누구도 제 주장을 안 갖고 있으면**
#           **항목 전체의 주장을 전원에게** 적용한다. («둘» 을 한 문장으로 말하는 서술을 그래야 읽는다.)
#         ⚠ 거꾸로 각자 주장이 있으면(「0249 SHIPPED · 0257 RETURNED」) 절대 섞지 않는다.
#      ⓑ **표본 이름이 주문번호가 아니었다** — `ZZ-123123` 은 **상품**이다. 패턴에 안 걸렸다.
#         → 백틱 안의 `ZZ-…` 를 따로 뽑아 **product·coupon·member 에서** 찾는다.
#      ⓒ **주장이 «그 파일 안에서» 였다** — 「`SignupView` 의 `border-ink-200`」.
#         저장소 전체로 grep 하면 `index.css` 에 토큰 정의가 있어 **늘 참**이 된다.
#         → 항목이 화면 이름과 토큰을 함께 말하면 **그 파일 안에서** 찾는다.
#
# 실행: scripts/check-stale-residue.sh [YYYY-MM-DD]   (.env 를 읽는다 · 읽기 전용 · sudo 불필요)
# 종료코드: 0 = 어긋남 없음 · 1 = 어긋남 있음 · 2 = **판정 불가**(문서·절·고유명·DB 중 하나를 못 읽음)
set -uo pipefail

# 🔴 **문서 본문 스캔은 전부 `LC_ALL=C`(바이트 기준)로 한다.**
#    `ko_KR.UTF-8` 에서 GNU sed 4.8 의 `s/…​.*$//` 가 **한글·이모지가 섞인 긴 문자열에서 조용히 안 바뀐다**
#    — 오류도 종료코드도 없이 **원문이 그대로 나온다**(2026-09-21 실측: 같은 입력이 로케일에선 828바이트,
#    `LC_ALL=C` 에선 18바이트). 그래서 «다음 주문번호 앞에서 끊기» 가 통째로 안 먹었고,
#    창이 항목 끝까지 벌어져 **옆 주문의 상태를 집었다.** 🔴 처음엔 이걸 «한 줄에 주문이 여럿» 으로
#    진단했는데 그건 증상이고, 원인은 로케일이었다(WA §1-3: *"결론이 맞아도 이유는 틀릴 수 있다"*).
SCAN() { LC_ALL=C "$@"; }

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HANDOFF_DIR="$REPO_DIR/docs/handoffs"

# 날짜를 안 주면 **가장 최근 핸드오프**를 쓴다(오늘 문서가 아직 없을 수 있다).
if [ $# -ge 1 ]; then
  [[ "$1" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "쓰는 법: $(basename "$0") [YYYY-MM-DD]" >&2; exit 2; }
  HANDOFF="$HANDOFF_DIR/$1-handoff.md"
else
  HANDOFF=$(ls -1 "$HANDOFF_DIR"/*-handoff.md 2>/dev/null | sort | tail -1)
fi
[ -f "${HANDOFF:-}" ] || { echo "⚠ 핸드오프를 못 찾았다: ${HANDOFF:-(없음)} — **판정 불가**"; exit 2; }

# ── §조건부 잔여 절을 꺼낸다. «그대로 물려받는다» 뿐이면 원본까지 따라간다 ──
# ⚠ 이 체인 자체가 이 스크립트가 생긴 이유다 — 09-18 은 09-17 을, 그건 09-10 을 가리켰다.
section_of() { awk '/^#{2,3} 조건부 잔여/{f=1;next} f && /^## /{exit} f' "$1"; }

SRC="$HANDOFF"; BODY=$(section_of "$SRC"); HOPS=0; CHAIN="$(basename "$SRC")"
while [ "$HOPS" -lt 10 ]; do
  # 본문에 고유명이 하나도 없고 다른 핸드오프를 가리키면 → 그쪽이 원본이다.
  if echo "$BODY" | grep -qE '[0-9]{8}-[0-9]{4}|`[^`]+`'; then break; fi
  NEXT=$(echo "$BODY" | grep -oE '20[0-9]{2}-[0-9]{2}-[0-9]{2}' | head -1)
  [ -z "$NEXT" ] && break
  [ -f "$HANDOFF_DIR/$NEXT-handoff.md" ] || break
  SRC="$HANDOFF_DIR/$NEXT-handoff.md"; BODY=$(section_of "$SRC"); CHAIN="$CHAIN → $NEXT"
  HOPS=$((HOPS+1))
done

if [ -z "${BODY// }" ]; then
  echo "⚠ §조건부 잔여 절을 못 읽었다($(basename "$SRC")) — 절 제목이 바뀌었나. **판정 불가**"; exit 2
fi

echo "대상: $CHAIN"

# ── ① 고유명을 뽑는다 ────────────────────────────────────────
# 항목(불릿) 단위로 접는다 — 이어진 줄이 같은 주장에 속한다.
# 🔴 **인용(`>`) 줄은 뺀다** — 거기 적히는 것은 «지금의 주장» 이 아니라 **«과거 경위»** 다
#    («✏️ 2026-09-21 에 지웠다 — 파일에 0건이다» 같은 정정 기록). 이걸 안 빼면 **고친 사실 자체가
#    어긋남으로 다시 잡힌다** — 2026-09-21 첫 판이 `SignupView` 의 `border-ink-200` 을 그렇게 오탐했다.
BULLETS=$(echo "$BODY" | awk '
  /^[[:space:]]*[-*] /{ if (cur != "") print cur; cur = $0; next }
  /^[[:space:]]*>/    { if (cur != "") { print cur; cur = "" } next }
  /^[[:space:]]*$/    { if (cur != "") { print cur; cur = "" } next }
  /^#/                { if (cur != "") { print cur; cur = "" } next }
                      { if (cur != "") cur = cur " " $0; else cur = $0 }
  END                 { if (cur != "") print cur }')

# 추출도 «인용을 뺀» 본문에서 한다 — 정정 기록의 이름이 살아 있는 주장으로 세어지면 안 된다.
CLAIMS="$BULLETS"
ORDERS=$(echo "$CLAIMS" | SCAN grep -oE '[0-9]{8}-[0-9]{4}' | sort -u)
# ⓑ `ZZ-…` 표본 이름 — 주문번호가 아니라 **상품·쿠폰·회원** 이름이다(`ZZ-123123` 을 놓친 자리).
ZZNAMES=$(echo "$CLAIMS" | SCAN grep -oE '`ZZ-[^`]+`' | tr -d '`' | sort -u)
FILES=$(echo "$CLAIMS"  | SCAN grep -oE '`[A-Za-z0-9_./-]+\.(vue|java|js|sh|yml|sql|md)`' | tr -d '`' | sort -u)
# 식별자: 클래스·메서드·camelCase·snake_case 컬럼·CSS 토큰만. 일반 낱말은 일부러 안 받는다.
SYMS=$(echo "$CLAIMS" | SCAN grep -oE '`[A-Za-z_][A-Za-z0-9_.]*(\(\))?`' | tr -d '`' \
       | SCAN grep -E '^([A-Z][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*(\(\))?|[a-z][a-z0-9]*[A-Z][A-Za-z0-9_]*(\(\))?|[a-z]+(_[a-z0-9]+)+)$' \
       | SCAN grep -vE '\.(vue|java|js|sh|yml|sql|md)$' \
       | sort -u)   # 파일 이름은 위 FILES 가 이미 본다 — 두 번 지목하지 않는다
CSS=$(echo "$CLAIMS" | SCAN grep -oE '`[a-z]+(-[a-z0-9]+){2,}`' | tr -d '`' | sort -u)

N_ORD=$(echo "$ORDERS" | grep -c . ); N_FIL=$(echo "$FILES" | grep -c .)
N_SYM=$(echo "$SYMS"   | grep -c . ); N_CSS=$(echo "$CSS"   | grep -c .)
N_ZZ=$(echo "$ZZNAMES" | grep -c . )
TOTAL=$((N_ORD + N_FIL + N_SYM + N_CSS + N_ZZ))

# ③ 🔴 **다 셌는가** — 0 이면 «성립» 이 아니라 «판정 불가» 다.
if [ "$TOTAL" -eq 0 ]; then
  echo "⚠ §조건부 잔여에서 고유명을 **하나도** 못 뽑았다 — 파서가 깨졌거나 절이 비었다. **판정 불가**"
  exit 2
fi
echo "본 것: 주문번호 ${N_ORD} · 표본이름(ZZ) ${N_ZZ} · 파일 ${N_FIL} · 식별자 ${N_SYM} · CSS 토큰 ${N_CSS}  (합 ${TOTAL})"

DRIFT=0
say() { [ "$DRIFT" -eq 0 ] && echo "⚠ 조건부 잔여가 지목한 이름이 실물과 어긋난다:"; DRIFT=1; echo "    $*"; }

# ── ② 파일 · 식별자 · CSS 토큰은 파일시스템·코드에서 확인한다 ──
SRC_DIRS=()
[ -d "$REPO_DIR/glassvue-backend/src" ]  && SRC_DIRS+=("$REPO_DIR/glassvue-backend/src")
[ -d "$REPO_DIR/glassvue-frontend/src" ] && SRC_DIRS+=("$REPO_DIR/glassvue-frontend/src")
if [ "${#SRC_DIRS[@]}" -eq 0 ]; then
  echo "⚠ 코드 트리를 못 찾았다(glassvue-*/src) — **판정 불가**"; exit 2
fi

while IFS= read -r f; do
  [ -z "$f" ] && continue
  find "$REPO_DIR" -path "$REPO_DIR/.git" -prune -o -name "$(basename "$f")" -print -quit 2>/dev/null | grep -q . \
    || say "파일 없음: $f"
done <<< "$FILES"

while IFS= read -r sym; do
  [ -z "$sym" ] && continue
  base="${sym%()}"                     # fit() → fit
  needle="${base##*.}"                 # AdminAuditLog.fit → fit
  owner="${base%.*}"                   # AdminAuditLog.fit → AdminAuditLog
  # 🔴 **점 앞뒤를 둘 다 본다.** 마지막 조각만 보면 `NoSuchSymbolXyz.fit()` 이 **다른 클래스의
  #    `fit`** 에 걸려 통과한다(2026-09-21 변형 주입에서 이것만 안 잡혔다).
  if [ "$owner" != "$base" ] && ! grep -rqF -- "$owner" "${SRC_DIRS[@]}" 2>/dev/null; then
    say "코드에 없음: $sym  («$owner» 를 못 찾았다)"
  elif ! grep -rqF -- "$needle" "${SRC_DIRS[@]}" 2>/dev/null; then
    say "코드에 없음: $sym"
  fi
done <<< "$SYMS"

# ⓒ 항목이 «화면 이름 + 토큰» 을 함께 말하면 **그 파일 안에서** 찾는다.
#    저장소 전체로 보면 `index.css` 의 토큰 정의에 걸려 **늘 참**이 된다(`border-ink-200` 을 놓친 자리).
CSS_SCOPED=""
while IFS= read -r b; do
  [ -z "$b" ] && continue
  views=$(printf '%s' "$b" | SCAN grep -oE '`[A-Z][A-Za-z0-9]*View`' | tr -d '`' | sort -u)
  toks=$(printf  '%s' "$b" | SCAN grep -oE '`[a-z]+(-[a-z0-9]+){2,}`' | tr -d '`' | sort -u)
  { [ -z "$views" ] || [ -z "$toks" ]; } && continue
  while IFS= read -r v; do
    [ -z "$v" ] && continue
    vf=$(find "$REPO_DIR/glassvue-frontend/src" -name "$v.vue" -print -quit 2>/dev/null)
    [ -z "$vf" ] && { say "화면 파일 없음: $v.vue"; continue; }
    while IFS= read -r t; do
      [ -z "$t" ] && continue
      CSS_SCOPED="$CSS_SCOPED $t"
      grep -qF -- "$t" "$vf" || say "그 화면에 없음: $v.vue 의 $t"
    done <<< "$toks"
  done <<< "$views"
done <<< "$BULLETS"

# 화면에 묶이지 않은 토큰만 저장소 전체로 본다(약한 검사다 — 위가 진짜다).
while IFS= read -r c; do
  [ -z "$c" ] && continue
  case " $CSS_SCOPED " in *" $c "*) continue;; esac
  grep -rqF -- "$c" "${SRC_DIRS[@]}" 2>/dev/null || say "코드에 없음(CSS): $c"
done <<< "$CSS"

# ── ② 주문번호는 **DB** 에서 확인한다 ────────────────────────
if [ ! -f "$REPO_DIR/.env" ]; then
  echo "⚠ .env 가 없어 주문번호 ${N_ORD}개를 **못 쟀다** — 나머지만 본 결과는 **판정 불가**다."
  exit 2
fi
set -a; . "$REPO_DIR/.env"; set +a
export ORACLE_HOME="${ORACLE_HOME:-/opt/oracle/product/19c/dbhome_1}"
export PATH="$ORACLE_HOME/bin:$PATH"
export NLS_LANG="${NLS_LANG:-KOREAN_KOREA.AL32UTF8}"

IN_LIST=$(echo "$ORDERS" | sed "s/^/'/;s/$/'/" | paste -sd, -)
if [ "$N_ZZ" -gt 0 ]; then
  ZZ_LIST=$(echo "$ZZNAMES" | sed "s/'/''/g;s/^/'/;s/$/'/" | paste -sd, -)
  ZZ_SQL="select 'ZZ|'||name from product where name in ($ZZ_LIST)
          union all select 'ZZ|'||name from coupon  where name in ($ZZ_LIST)
          union all select 'ZZ|'||nickname from member where nickname in ($ZZ_LIST);"
else
  ZZ_SQL="select * from dual where 1=0;"
fi
# 🔴 접속 실패를 성공으로 내보내지 않는다 — `whenever sqlerror` 를 `connect` **앞**에 둔다(2026-09-18).
DBOUT=$( { printf 'whenever sqlerror exit 2\nconnect %s/%s@//%s:%s/%s\n' \
             "$DB_USER" "$DB_PASSWORD" "$DB_HOST" "$DB_PORT" "$DB_SERVICE"
           cat <<SQL
set pagesize 0 feedback off heading off linesize 200
whenever sqlerror exit 2
select order_no||'|'||status||'|'||case when paid_at is null then 'UNPAID' else 'PAID_AT' end
from orders where order_no in ($IN_LIST);
$ZZ_SQL
exit
SQL
         } | sqlplus -s /nolog ); DBRC=$?
if [ "$DBRC" -ne 0 ]; then
  echo "⚠ DB 를 못 읽었다(sqlplus rc=$DBRC) — 주문번호 ${N_ORD}개 **판정 불가**"; exit 2
fi

SEEN=$(echo "$DBOUT" | grep -cE '^[0-9]{8}-[0-9]{4}\|')
while IFS= read -r o; do
  [ -z "$o" ] && continue
  row=$(echo "$DBOUT" | grep "^$o|" | head -1)
  if [ -z "$row" ]; then say "주문 없음: $o  (DB 에 그 주문번호가 없다)"; continue; fi
  st=$(echo "$row" | cut -d'|' -f2); pd=$(echo "$row" | cut -d'|' -f3)
  # 문서가 그 주문번호에 **상태를 붙였으면** 대조한다. 안 붙였으면 존재만 본다.
  # 🔴 **줄 전체를 보면 안 된다** — 한 줄에 주문이 여럿이라 **옆 주문의 상태**를 집는다.
  #    (2026-09-21 첫 판이 그렇게 셋을 오탐했다 — WA §3-6 ⑤ 「여러 줄로 쓴 라우트」의 거울상이다.)
  #    그래서 «그 번호 바로 뒤 ~ 다음 주문번호 앞» 구간만 본다.
  # 🔴 **«제 뒤» 를 먼저 보고, 거기 없을 때만 «제 앞» 을 본다.** 둘 다 이웃 주문번호로 경계를 끊는다.
  #    · 뒤 우선이라 「`0249` `SHIPPED` · `0257` `RETURNED`」 에서 0257 이 앞의 SHIPPED 를 안 집는다.
  #    · 앞도 보므로 「**미결제** 주문 둘(`4185`·`4186`)」 처럼 **주장이 번호보다 먼저 오는** 서술을 읽는다.
  claimed=""; unpaid_claim=0
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    rgt="${line#*$o}";  rgt=$(printf '%s' "$rgt" | SCAN sed -E 's/[0-9]{8}-[0-9]{4}.*$//')
    # 왼쪽은 **이웃 번호와 «·» 둘 다**에서 끊는다. 「…0257 `RETURNED`) · 계정 … · 검증 주문 셋(`5295`」
    # 에서 `·` 를 안 끊으면 5295 가 **0257 의 상태**를 집는다(2026-09-21 실측 오탐).
    lft="${line%%$o*}"; lft=$(printf '%s' "$lft" | SCAN sed -E 's/^.*[0-9]{8}-[0-9]{4}//' | SCAN sed 's/^.*·//')
    c=$(printf '%s' "$rgt" | SCAN grep -oE 'RETURN_REQUESTED|ORDERED|SHIPPED|DELIVERED|CANCELLED|RETURNED|PAID' | head -1)
    win="$rgt"
    if [ -z "$c" ]; then
      c=$(printf '%s' "$lft" | SCAN grep -oE 'RETURN_REQUESTED|ORDERED|SHIPPED|DELIVERED|CANCELLED|RETURNED|PAID' | head -1)
      win="$rgt $lft"
    fi
    if printf '%s' "$win" | SCAN grep -qF '미결제'; then unpaid_claim=1; fi
    if [ -n "$c" ]; then claimed="$c"; break; fi
  done <<< "$(echo "$BULLETS" | grep -F "$o")"
  if [ -n "$claimed" ] && [ "$claimed" != "$st" ]; then
    say "상태 어긋남: $o  문서=$claimed / DB=$st"
  elif [ "$unpaid_claim" -eq 1 ] && [ "$pd" != "UNPAID" ]; then
    say "상태 어긋남: $o  문서=미결제 / DB=결제됨($st)"
  elif [ "$unpaid_claim" -eq 1 ] && { [ "$st" = "CANCELLED" ] || [ "$st" = "RETURNED" ]; }; then
    # 🔴 «미결제 주문» 은 **살아 있는 표본**을 뜻한다 — 끝난 주문은 더는 그 자리를 못 밟는다.
    #    결제 여부만 보면 `paid_at` 이 NULL 인 채 취소된 주문이 **«여전히 참»** 으로 통과한다
    #    (2026-08-13 07:30 에 손으로 취소된 4185·4186 이 5주간 그렇게 살아남았다).
    say "표본이 죽었다: $o  문서=미결제 표본 / DB=$st"
  fi
done <<< "$ORDERS"

ZZ_SEEN=0
while IFS= read -r z; do
  [ -z "$z" ] && continue
  if echo "$DBOUT" | grep -qF "ZZ|$z"; then ZZ_SEEN=$((ZZ_SEEN+1))
  else say "표본 없음: $z  (product·coupon·member 어디에도 없다)"; fi
done <<< "$ZZNAMES"

# ③ DB 쪽도 «다 읽었는가» 를 남긴다 — 못 찾은 것은 위에서 이미 지목했다.
echo "DB 에서 읽은 주문: ${SEEN}/${N_ORD} · 표본이름: ${ZZ_SEEN}/${N_ZZ}"

if [ "$DRIFT" -eq 0 ]; then echo "✅ 어긋남 없다 (${TOTAL}개 대조)"; exit 0; fi
echo "  → §조건부 잔여를 목록 대 목록으로 판정할 때가 됐다(WA §1-3-1 · 2026-09-21 §2)"
exit 1
