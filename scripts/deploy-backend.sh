#!/usr/bin/env bash
# glassvue-backend bootJar 빌드 → /opt 배포 → 서비스 재시작 → 헬스체크
# 사용: ./scripts/deploy-backend.sh   (ecstel 또는 root 어느 쪽으로 실행해도 됨)
set -euo pipefail

BACK_DIR=/home/ecstel/work/glassvue-backend
JAR_DST=/opt/esp-backend/esp-backend.jar   # 인프라 이름 정리 시 여기만 바꾸면 됨
SERVICE=esp-backend

SUDO=""; [ "$(id -u)" -ne 0 ] && SUDO="sudo"

echo "▶ bootJar 빌드…"
if [ "$(id -un)" = "root" ]; then
  runuser -l ecstel -c "'$BACK_DIR/gradlew' -p '$BACK_DIR' bootJar"
else
  "$BACK_DIR/gradlew" -p "$BACK_DIR" bootJar
fi

# 실행 가능한 boot jar 찾기 (-plain.jar 제외)
JAR_SRC=$(ls "$BACK_DIR"/build/libs/glassvue-backend-*.jar 2>/dev/null | grep -v -- '-plain' | head -1)
[ -n "$JAR_SRC" ] || { echo "✗ 빌드 산출물 jar을 못 찾음"; exit 1; }
echo "  jar: $JAR_SRC"

echo "▶ 배포 → $JAR_DST"
[ -f "$JAR_DST" ] && $SUDO cp -f "$JAR_DST" "$JAR_DST.bak"   # 이전 jar 백업(롤백용)
$SUDO cp -f "$JAR_SRC" "$JAR_DST"
$SUDO chmod 644 "$JAR_DST"

echo "▶ 서비스 재시작…"
$SUDO systemctl restart "$SERVICE"

echo "▶ 헬스체크…"
for i in $(seq 1 30); do
  if curl -sf -m 2 http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo "✅ 백엔드 배포 완료 → :8080 UP"; exit 0
  fi
  sleep 1
done
echo "⚠ 헬스체크 실패 — 'journalctl -u $SERVICE -n 50' 로 로그 확인"; exit 1
