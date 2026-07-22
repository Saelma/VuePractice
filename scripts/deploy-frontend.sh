#!/usr/bin/env bash
# glassvue-frontend 빌드 → nginx 웹루트에 정적 배포
# 사용: ./scripts/deploy-frontend.sh   (ecstel 또는 root 어느 쪽으로 실행해도 됨)
set -euo pipefail

FRONT_DIR=/home/ecstel/work/glassvue-frontend
WEBROOT=/var/www/glassvue-frontend          # 인프라 이름 정리 시 여기만 바꾸면 됨

# root면 sudo 불필요, 아니면 sudo 사용
SUDO=""; [ "$(id -u)" -ne 0 ] && SUDO="sudo"

# --- infra/ 사본 드리프트 경고 ---
# 서버 설정을 서버에서 직접 고치고 infra/ 반영을 잊으면, 저장소가 "맞는 것처럼 보이는 틀린 문서"가 된다.
# 배포는 어차피 매번 거치는 관문이라 여기서 알린다. **막지는 않는다** — 드리프트가 배포를 막으면
# 급할 때 스크립트를 우회하게 되어 더 나빠진다.
DRIFT_CHECK="$(dirname "${BASH_SOURCE[0]}")/check-infra-drift.sh"
if [ -x "$DRIFT_CHECK" ] && ! "$DRIFT_CHECK" >/dev/null 2>&1; then
  echo "⚠ infra/ 사본이 서버 설정과 다르다 — './scripts/check-infra-drift.sh'로 확인할 것 (배포는 계속한다)"
fi

echo "▶ 빌드…"
if [ "$(id -un)" = "root" ]; then
  runuser -l ecstel -c "pnpm -C '$FRONT_DIR' build"   # 빌드는 프로젝트 소유자(ecstel)로
else
  pnpm -C "$FRONT_DIR" build
fi

echo "▶ 배포 → $WEBROOT"
$SUDO rm -rf "${WEBROOT:?}"/*                          # :? = 변수 비면 중단(rm -rf / 방지)
$SUDO cp -rf "$FRONT_DIR/dist/." "$WEBROOT"/
$SUDO chown -R root:nginx "$WEBROOT"
$SUDO chmod -R u=rwX,g=rX,o= "$WEBROOT"

echo "✅ 프론트 배포 완료 → nginx(:80)"
