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
TODAY="$(date +%F)"
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
COMMITS=$(git_q log --since="${TODAY} 00:00" --oneline | wc -l | tr -d ' ')
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
TABLE=$(grep -cE '^\| `[0-9a-f]{7,}` \|' "$HANDOFF" | tr -d ' ')
[[ "$TABLE" =~ ^[0-9]+$ ]] || TABLE=0
GAP=$(( COMMITS - TABLE ))
if [ "$GAP" -ge 3 ]; then
  say "커밋 표: 실제 ${COMMITS}건 / 표 ${TABLE}건 — ${GAP}건 빠짐"
  say "  → 이어 붙이지 말고 통째로 다시 만들 것:"
  say "     git log --reverse --since=\"${TODAY} 00:00\" --pretty='| \`%h\` | %s |'"
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

if [ "$DRIFT" -eq 1 ]; then
  echo "  → 배포는 계속된다. 배포 직후에 닫는 게 가장 싸다(실측값이 손에 있을 때, WA §4-0-1)."
  exit 1
fi
exit 0
