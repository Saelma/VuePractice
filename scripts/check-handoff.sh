#!/usr/bin/env bash
# 오늘 핸드오프의 **집계 절**이 실제와 어긋났는지 확인한다.
#
# 핸드오프에는 개수를 말하는 자리가 여럿이다 — 커밋 표, 하루 총괄, 기능 검증 종결 기록,
# 조건부 잔여. 항목은 하루 종일 늘어나는데 **문장은 처음 쓴 상태로 남는다.**
# 개별 절(§2, §2-10 …)은 append-only 라 안전한데, **여러 항목이 함께 쓰는 집계 절만** 어긋난다.
#
# 2026-08-03 에 하루가 8건까지 길어지자 **세 번 어긋났다**:
#   ① 커밋 표가 22건 중 9건만 — 중간 「마감 대조」를 두 번 했는데도
#      ("그 뒤 몇 개" 만 이어 붙이다 한 번 건너뛴 뒤로 계속 어긋났다)
#   ② 그걸 고치는 스크립트가 **다른 워크트리**에서 실행돼 커밋 메시지만 앞서 나갔다
#   ③ 조건부 잔여를 같은 문서 **두 군데**에 "7건" 이라 적어 뒀는데 그날 8번을 추가해 둘 다 틀렸다
# 셋 다 **"문서 다 됐다"고 답한 뒤** 사용자가 되물어서 드러났다.
#
# 규약(WORKING-AGREEMENTS §4-0)에 적어 두는 것만으로는 안 걸러진다 — 규약을 올린 **직후에** 또
# 안 셌다. 그래서 배포 관문에서 자동으로 알린다(check-deploy-branch.sh 와 같은 자리·같은 판단:
# 그것도 "규약에 적었는데 같은 날 재발" 해서 스크립트가 됐다).
#
# **막지는 않는다** — 배포를 막으면 급할 때 우회하게 되어 오히려 나빠진다(드리프트 검사와 동일).
# 문서가 아직 안 닫힌 건 정상일 수 있다(작업 중). 이건 "틀렸다"가 아니라 **"닫을 때가 됐다"** 는 신호다.
#
# 종료코드: 0 = 어긋남 없음(또는 판단 불가), 1 = 어긋남 있음
set -uo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 날짜를 인자로 받는다 — 없으면 오늘. 과거 문서를 되짚어 볼 수 있어야 한다(2026-09-07:
# 09-02 의 「마감값」 어긋남을 찾고 나서, **다른 날에도 있나** 를 세려니 방법이 없었다).
TODAY="${1:-$(date +%F)}"
if ! [[ "$TODAY" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo "쓰는 법: $(basename "$0") [YYYY-MM-DD]   (날짜를 안 주면 오늘)" >&2
  exit 2
fi
HANDOFF="$REPO_DIR/docs/handoffs/${TODAY}-handoff.md"

# root로 실행되면 ecstel 소유 저장소에서 git이 "dubious ownership"으로 거부한다 → 소유자로 실행.
git_q() {
  if [ "$(id -un)" = "root" ]; then
    runuser -l ecstel -c "git -C '$REPO_DIR' $*" 2>/dev/null
  else
    git -C "$REPO_DIR" "$@" 2>/dev/null
  fi
}

# 오늘 커밋이 없으면 볼 것도 없다(문서 작업만 한 날·다른 날 배포).
# ⚠ 상한(--until)을 함께 건다 — 날짜를 인자로 받게 되면서 필요해졌다.
# 없으면 과거 날짜를 검사할 때 「그날 이후 전부」를 세어 커밋 표가 늘 모자라 보인다.
COMMITS=$(git_q log --since="${TODAY} 00:00" --until="${TODAY} 23:59:59" --oneline | wc -l | tr -d ' ')
[[ "$COMMITS" =~ ^[0-9]+$ ]] || exit 0
[ "$COMMITS" -eq 0 ] && exit 0

# 오늘 커밋이 있는데 핸드오프가 없다 = 아직 안 만든 것. 그 자체가 알릴 값이 있다(WA §4).
if [ ! -f "$HANDOFF" ]; then
  echo "⚠ 오늘(${TODAY}) 커밋이 ${COMMITS}건인데 핸드오프가 없다: docs/handoffs/${TODAY}-handoff.md"
  exit 1
fi

DRIFT=0
say() { [ "$DRIFT" -eq 0 ] && echo "⚠ 핸드오프 집계가 실제와 어긋난다 (${TODAY}):"; DRIFT=1; echo "    $*"; }

# --- ① 커밋 표 ---
# 표는 **문서를 닫는 커밋 자체**를 구조적으로 못 담는다(WA §4-4) — 그래서 1~2건 차이는 정상이다.
# 3건 이상 벌어지면 "이어 붙이다 건너뛴" 것이다.
# ⚠ **「## 0. 오늘의 커밋」 절 안만 센다.** 문서 다른 곳에 **커밋 표를 인용**해 둘 수 있고
# (2026-09-07 §2 가 09-03 커밋 7건을 그대로 인용했다) 행 모양이 같아 함께 세어진다.
# 🔴 그러면 **인용 행이 진짜 표의 부족분을 메워** 모자란데도 조용히 통과한다 —
# 09-07 은 실제 13건 / 전체 세기 18행이라 «-5» 가 나와 검사가 통과했다(우연히 맞은 것이다).
TABLE=$(awk '/^## 0\. /{f=1;next} /^## /{f=0} f' "$HANDOFF" \
        | grep -cE '^\| `[0-9a-f]{7,}` \|' | tr -d ' ')
[[ "$TABLE" =~ ^[0-9]+$ ]] || TABLE=0
GAP=$(( COMMITS - TABLE ))
if [ "$GAP" -ge 3 ]; then
  say "커밋 표: 실제 ${COMMITS}건 / 표 ${TABLE}건 — ${GAP}건 빠짐"
  say "  → 이어 붙이지 말고 통째로 다시 만들 것:"
  say "     git log --reverse --since=\"${TODAY} 00:00\" --until=\"${TODAY} 23:59:59\" --pretty='| \`%h\` | %s |'"
fi

# --- ② 세어야 아는 숫자를 문장에 적었나 (WA §4-0-2) ---
# 목록이 있는 자리에 "N건" 을 손으로 적으면 항목이 늘 때 반드시 어긋난다.
if grep -nE '[0-9]+건 (그대로|전부) (살아|남아)' "$HANDOFF" >/dev/null 2>&1; then
  say "목록 옆에 손으로 쓴 개수가 있다 — 목록이 곧 개수다(WA §4-0-2):"
  grep -nE '[0-9]+건 (그대로|전부) (살아|남아)' "$HANDOFF" | sed 's/^/      /' | head -5
fi

# --- ③ 하루 총괄 표 ↔ 기능 검증 종결 기록 ---
# 총괄에 올린 항목은 종결 기록에도 있어야 한다. 2026-08-03 에 7건 중 2건만 있어서,
# 다음 세션이 **이미 한 일을 다시 후보로 집을** 뻔했다.
SUMMARY=$(sed -n '/^## .*하루 총괄/,/^### /p' "$HANDOFF" | grep -cE '^\| §' | tr -d ' ')
CLOSED=$(sed -n '/^### 기능 검증 종결 기록/,/^### 조건부 잔여/p' "$HANDOFF" | grep -cE '^- ~~|^- \*\*' | tr -d ' ')
[[ "$SUMMARY" =~ ^[0-9]+$ ]] || SUMMARY=0
[[ "$CLOSED"  =~ ^[0-9]+$ ]] || CLOSED=0
if [ "$SUMMARY" -gt 0 ] && [ "$CLOSED" -lt "$SUMMARY" ]; then
  say "총괄 표 ${SUMMARY}건 / 기능 검증 종결 기록 ${CLOSED}건 — 종결 기록이 모자라다"
  say "  → 다음 세션이 '오늘 뭐가 끝났나'를 보는 자리다. 빠지면 한 일을 다시 후보로 집는다"
fi

# --- ④ 이월 절 (WA §4-1, 필수) ---
if ! grep -q '^## 이월' "$HANDOFF"; then
  say "「## 이월」 절이 없다 — 비었으면 '없음'이라고 명시한다(WA §4-1)"
fi

# --- ⑤ 아직 '대기' 인 절 ---
# 배포 중이라면 정상이다. 배포가 끝났는데 남아 있으면 닫을 때가 된 것이다.
PENDING=$(grep -cE '배포 — \*\*대기\*\*' "$HANDOFF" | tr -d ' ')
[[ "$PENDING" =~ ^[0-9]+$ ]] || PENDING=0
if [ "$PENDING" -gt 0 ]; then
  say "아직 「배포 — **대기**」인 절이 ${PENDING}개 있다 — 배포가 끝나면 종결로 바꿀 것"
fi

# --- ⑥ 「마감값」 블록이 둘 이상이면 서로 맞는지 본다 (2026-09-07) ---
# 2026-09-02 문서에 마감값 블록이 **둘** 있었고 서로 달랐다. 위쪽 블록은 하루 중 세 번 정정됐는데
# 아래쪽 블록이 **전날(09-01) 값을 그대로** 들고 있어, 같은 문서가 자기 정정을 되돌려 놓았다.
# 다음 날 아침 재계수가 **무엇과 대조해야 하는지를 잃는다** — 마감값은 하나여야 한다.
# ⚠ 위 ①~⑤ 는 「개수」를 세는데 이건 「값」을 본다. 그래서 규약(§4-0)만으로 안 걸렸다.
# ⚠ 인라인 코드(`…`)는 빼고 센다 — 형식을 **설명하는 산문**이 블록으로 오검출된다.
# (2026-09-07: 이 검사를 설명한 문장 두 줄 때문에 «블록 3개» 라고 했다. 첫 판이 그렇게 틀렸다 —
#  check-orphan-javadoc.sh 가 「구역 머리말」을 오검출한 것과 같은 모양이다.)
mark_lines() { awk '{ t=$0; gsub(/`[^`]*`/, "", t); if (t ~ /\*\*마감값 \(/) print NR": "$0 }' "$1"; }
MARKS=$(mark_lines "$HANDOFF" | wc -l | tr -d ' ')
[[ "$MARKS" =~ ^[0-9]+$ ]] || MARKS=0
# 「마감값」을 적는 관행은 2026-08-26 에 시작됐다(그 전 문서 28개에는 없다 — 09-07 실측).
# 그보다 앞선 날짜를 되짚어 볼 때 «없다» 고 알려 봐야 소음이라 묻지 않는다.
CLOSEVAL_SINCE="2026-08-26"
if [ "$MARKS" -eq 0 ] && [[ "$TODAY" > "$CLOSEVAL_SINCE" || "$TODAY" == "$CLOSEVAL_SINCE" ]]; then
  # 2026-09-07: 이 검사를 만든 그날의 문서에 마감값 블록이 없었다 — «오늘 마감값 = 아침값이다»
  # 라고 **문장으로** 적어 형식을 벗어났다. 다음 날 아침 재계수가 대조할 값을 못 찾는다.
  say "「마감값 (날짜)」 블록이 없다 — 다음 날 아침 재계수가 대조할 값이 없다(WA §3-5)"
  say "  → 값이 안 움직인 날에도 적는다. «안 움직였다» 는 문장이 아니라 **값**으로 남겨야 대조된다"
fi
if [ "$MARKS" -ge 2 ]; then
  say "「마감값」 블록이 ${MARKS}개다 — 마감값은 **하나**여야 한다:"
  mark_lines "$HANDOFF" | cut -c1-100 | sed 's/^/      /'
  # 같은 지표가 블록마다 다른 값을 들고 있나. 「85 → 86」 정정 줄은 경위라 뺀다.
  CONFLICT=$(awk '
    {
      t = $0; gsub(/`[^`]*`/, "", t)          # 마커 판정은 인라인 코드를 뺀 사본으로
      if (t ~ /\*\*마감값 \(/) { blk++; active=1 }
      else if ($0 ~ /^[0-9]+\. /) { active = 0 }
      if (!active) next
      if (index($0, "→")) next
      s = $0
      while (match(s, /`[A-Za-z_][A-Za-z0-9_]*` \*\*[0-9,]+\*\*/)) {
        tok = substr(s, RSTART, RLENGTH); s = substr(s, RSTART + RLENGTH)
        name = tok; sub(/^`/, "", name); sub(/`.*$/, "", name)
        val  = tok; sub(/^[^*]*\*\*/, "", val); sub(/\*\*$/, "", val)
        key = name SUBSEP blk
        if (!(key in seen)) { seen[key] = val; names[name] = 1 }
      }
    }
    END {
      for (n in names) {
        first = ""; conflict = 0; shown = ""
        for (b = 1; b <= blk; b++) {
          k = n SUBSEP b
          if (!(k in seen)) continue
          shown = shown sprintf("블록%d=%s ", b, seen[k])
          if (first == "") first = seen[k]; else if (seen[k] != first) conflict = 1
        }
        if (conflict) printf "%-18s %s\n", n, shown
      }
    }' "$HANDOFF")
  if [ -n "$CONFLICT" ]; then
    say "  → 같은 지표가 블록마다 다르다 (정정 줄「→」은 뺀 값이다):"
    echo "$CONFLICT" | sed 's/^/      /'
    say "  → 🔴 다음 날 아침 재계수가 **무엇과 대조할지를 잃는다.** 블록을 하나로 합칠 것"
  fi
fi

# --- ⑦ 「마감값」이 **지금 DB 와 맞는가** (2026-09-10) ---
# ①~⑥ 은 전부 **문서 안**만 본다. 그래서 «문서끼리는 일관된데 현실과 다른» 경우를 못 잡는다 —
# 2026-09-07 이 `notification` 을 **263** 이라 적었는데 그날 오후 전수가 3건을 더해 실제는 266 이었다.
# 🔴 아침 재계수가 다음 날 그것을 잡았지만, **하루가 거짓인 채로 지나갔다.**
#
# ⚠ **«값이 움직인 날» 과 «안 고친 날» 을 가르지 않는다.** 09-10 에 만들며 고민한 자리인데,
#   🔴 **가를 필요가 없다** — 둘 다 결론이 «문서가 낡았다» 이고 처방도 «다시 세서 고쳐라» 로 같다.
#   가르려 들면 «전수를 돌렸나» 를 추적해야 하고, 그건 이 스크립트가 알 수 있는 것이 아니다.
#
# ⚠ 표에 없는 지표(매출·잔액·등급)는 **안 본다** — 계산이 필요하고 그건 불변식 스크립트의 일이다.
# 🔴 DB 에 못 붙으면 **조용히 건너뛴다**(경고도 안 낸다) — 이 스크립트는 배포 관문에서 돌고,
#   DB 접속은 그 관문의 관심사가 아니다. 불변식 스크립트가 그쪽을 이미 «판정 불가» 로 말한다.
#
# 🔴 **오늘 문서일 때만 본다.** 마감값은 «그날의 기록» 이라, 지난 날짜 문서를 지금 DB 와 견주면
#   맞는 값이 틀린 것으로 나온다 — 만들면서 실제로 그랬다(09-07 의 `notification` 266 은
#   그날 맞았고 오늘은 211 이라 «낡았다» 로 잡혔다). ⚠ **이 검사는 «오늘 문서를 닫는» 일이지
#   «과거를 감사하는» 일이 아니다.**
CLOSEVAL_TABLES="member point_account member_coupon admin_audit_log product_variant orders point_history stock_history notification"
if [ "$MARKS" -eq 1 ] && [ "$TODAY" = "$(date +%F)" ] && [ -f "$REPO_DIR/.env" ]; then
  # 마감값 줄에서 `지표` **값** 쌍을 뽑는다. ⚠ 인라인 코드는 여기선 지표 이름 그 자체라 안 지운다.
  DOC_PAIRS=$(awk '
    { t=$0; gsub(/`[^`]*`/, "", t); if (t ~ /\*\*마감값 \(/) { inblk=1 } else if ($0 ~ /^[0-9]+\. /) { inblk=0 }
      if (!inblk) next
      if (index($0, "→")) next
      s=$0
      while (match(s, /`[A-Za-z_][A-Za-z0-9_]*` \*\*[0-9,]+\*\*/)) {
        tok=substr(s,RSTART,RLENGTH); s=substr(s,RSTART+RLENGTH)
        name=tok; sub(/^`/,"",name); sub(/`.*$/,"",name)
        val=tok;  sub(/^[^*]*\*\*/,"",val); sub(/\*\*$/,"",val); gsub(/,/,"",val)
        if (!(name in seen)) { seen[name]=val; print name" "val }
      } }' "$HANDOFF")

  if [ -z "$DOC_PAIRS" ]; then
    # 🔴 블록은 있는데 «지표 하나도» 못 뽑았다 — 형식이 바뀌었거나 파서가 낡은 것이다.
    #   ⚠ 조용히 넘어가면 이 검사가 **영원히 아무것도 안 하게** 된다(WA §3-6).
    say "「마감값」 블록에서 지표를 하나도 못 뽑았다 — 형식이 바뀌었나? (이 검사가 헛돈다)"
  else
    ( set -a; . "$REPO_DIR/.env"; set +a
      export ORACLE_HOME="${ORACLE_HOME:-/opt/oracle/product/19c/dbhome_1}"
      export PATH="$ORACLE_HOME/bin:$PATH"
      SQL_LINES=""
      while read -r name val; do
        case " $CLOSEVAL_TABLES " in *" $name "*) ;; *) continue ;; esac   # 🔴 허용 목록 밖은 SQL 에 안 넣는다
        SQL_LINES="${SQL_LINES}select '$name|'||count(*) from $name;"$'\n'
      done <<< "$DOC_PAIRS"
      [ -z "$SQL_LINES" ] && exit 0
      # ⚠ **20초 상한**. 배포 관문에서 도는 자리라 DB 가 응답이 없으면 매달린다 —
      #   못 붙는 것은 «못 붙었다» 로 빨리 말하는 것이 낫다(-L 은 재시도만 막지 무응답은 못 막는다).
      ACTUAL=$(printf 'set heading off feedback off pagesize 0 linesize 200\nwhenever sqlerror exit 2\n%sexit\n' "$SQL_LINES" \
               | timeout 20 sqlplus -s -L "$DB_USER/$DB_PASSWORD@//$DB_HOST:$DB_PORT/$DB_SERVICE" 2>/dev/null)
      # 🔴 «못 붙었다» 를 **명시 신호**로 남긴다. 빈 파일로 두면 «맞았다» 와 구별이 안 된다(WA §3-6).
      echo "$ACTUAL" | grep -q '|' || { echo "__NOCONN__"; exit 0; }
      while read -r name val; do
        now=$(echo "$ACTUAL" | grep "^$name|" | head -1 | cut -d'|' -f2 | tr -d ' ')
        [ -n "$now" ] || continue
        [ "$now" = "$val" ] || echo "$name 문서 $val / 지금 $now"
      done <<< "$DOC_PAIRS"
    ) > /tmp/.closeval.$$ 2>/dev/null
    # 🔴 **몇 개를 봤는지 말한다**(WA §3-6). 이 검사는 DB 에 못 붙거나 지표를 하나도 못 뽑으면
    #   조용히 넘어가는데, 그러면 «맞았다» 와 «안 봤다» 가 화면에서 똑같아 보인다.
    #   ⚠ 규약을 쓰자마자 이 도구가 그 모양이었다 — 그래서 여기 줄을 하나 더 뒀다.
    CHECKED=$(grep -c . <<< "$DOC_PAIRS")
    if grep -q '__NOCONN__' /tmp/.closeval.$$ 2>/dev/null; then
      say "「마감값」을 DB 와 **대조하지 못했다**(접속 실패) — 🔴 «맞았다» 가 아니라 «안 봤다» 다"
    elif [ -s /tmp/.closeval.$$ ]; then
      say "「마감값」이 지금 DB 와 다르다 — 문서가 낡았다 (지표 ${CHECKED}개 중):"
      sed 's/^/      /' /tmp/.closeval.$$
      say "  → 🔴 다시 세서 고칠 것. 다음 날 아침 재계수가 **틀린 값과 대조**하게 된다"
    fi
    rm -f /tmp/.closeval.$$
  fi
fi

if [ "$DRIFT" -eq 1 ]; then
  echo "  → 배포는 계속된다. 배포 직후에 닫는 게 가장 싸다(실측값이 손에 있을 때, WA §4-0-1)."
  exit 1
fi
exit 0
