#!/usr/bin/env bash
# 배포 워크트리가 다른 로컬 브랜치보다 뒤처졌는지 확인한다.
#
# 배포 스크립트는 **배포 워크트리(/home/ecstel/work)에서 빌드**한다. 작업 브랜치에 커밋·푸시만 하고
# main ff 머지를 잊은 채 배포하면 **옛 코드가 조용히 나간다** — 스크립트는 정상 종료하고 jar 시각도
# 갱신돼서 겉보기엔 성공한 것 같다. 2026-07-23에 이걸로 두 번 당했다(WORKING-AGREEMENTS §5).
#
# 규약에 적어두는 것만으로는 안 걸러져서(두 번째가 그 증거) 배포 관문에서 자동으로 알린다.
# **막지는 않는다** — 배포를 막으면 급할 때 스크립트를 우회하게 되어 오히려 나빠진다(드리프트 검사와 동일).
#
# 종료코드: 0 = 뒤처진 것 없음(또는 판단 불가), 1 = 뒤처짐 있음
set -uo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# root로 실행되면 ecstel 소유 저장소에서 git이 "dubious ownership"으로 거부한다 → 소유자로 실행.
git_q() {
  if [ "$(id -un)" = "root" ]; then
    runuser -l ecstel -c "git -C '$REPO_DIR' $*" 2>/dev/null
  else
    git -C "$REPO_DIR" "$@" 2>/dev/null
  fi
}

CURRENT=$(git_q rev-parse --abbrev-ref HEAD)
# 판단이 안 서면 조용히 넘어간다 — 근거 없는 경고는 신뢰를 깎아 다음 진짜 경고를 무시하게 만든다.
[ -n "$CURRENT" ] || exit 0

BEHIND=0
while read -r branch; do
  [ -n "$branch" ] || continue
  [ "$branch" = "$CURRENT" ] && continue
  n=$(git_q rev-list --count "HEAD..$branch")
  [[ "$n" =~ ^[0-9]+$ ]] || continue
  if [ "$n" -gt 0 ]; then
    if [ "$BEHIND" -eq 0 ]; then
      echo "⚠ 배포 워크트리('$CURRENT')가 다른 브랜치보다 뒤처져 있다 — 옛 코드가 배포될 수 있다:"
    fi
    echo "    $branch 에 $CURRENT 로 안 넘어온 커밋 ${n}건"
    git_q log --oneline "HEAD..$branch" | sed 's/^/      /'
    BEHIND=1
  fi
done < <(git_q for-each-ref --format='%(refname:short)' refs/heads/)

if [ "$BEHIND" -eq 1 ]; then
  echo "  → 의도한 것이면 그대로 진행해도 된다. 아니면 먼저 머지할 것:"
  echo "     git -C $REPO_DIR merge --ff-only <브랜치> && git -C $REPO_DIR push"
  exit 1
fi
exit 0
