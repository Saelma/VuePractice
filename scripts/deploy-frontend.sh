#!/usr/bin/env bash
# glassvue-frontend 빌드 → nginx 웹루트에 정적 배포
# 사용: ./scripts/deploy-frontend.sh   (ecstel 또는 root 어느 쪽으로 실행해도 됨)
set -euo pipefail

FRONT_DIR=/home/ecstel/work/glassvue-frontend
WEBROOT=/var/www/glassvue-frontend          # 인프라 이름 정리 시 여기만 바꾸면 됨

# root면 sudo 불필요, 아니면 sudo 사용
SUDO=""; [ "$(id -u)" -ne 0 ] && SUDO="sudo"

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
