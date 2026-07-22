#!/usr/bin/env bash
# glassvue-backend bootJar 빌드 → /opt 배포 → 서비스 재시작 → 헬스체크
# 사용: ./scripts/deploy-backend.sh   (ecstel 또는 root 어느 쪽으로 실행해도 됨)
set -euo pipefail

BACK_DIR=/home/ecstel/work/glassvue-backend
JAR_DST=/opt/glassvue-backend/glassvue-backend.jar   # 인프라 이름 정리 시 여기만 바꾸면 됨
SERVICE=glassvue-backend

SUDO=""; [ "$(id -u)" -ne 0 ] && SUDO="sudo"

# --- infra/ 사본 드리프트 경고 ---
# 서버 설정을 서버에서 직접 고치고 infra/ 반영을 잊으면, 저장소가 "맞는 것처럼 보이는 틀린 문서"가 된다.
# 배포는 어차피 매번 거치는 관문이라 여기서 알린다. **막지는 않는다** — 드리프트가 배포를 막으면
# 급할 때 스크립트를 우회하게 되어 더 나빠진다.
DRIFT_CHECK="$(dirname "${BASH_SOURCE[0]}")/check-infra-drift.sh"
if [ -x "$DRIFT_CHECK" ] && ! "$DRIFT_CHECK" >/dev/null 2>&1; then
  echo "⚠ infra/ 사본이 서버 설정과 다르다 — './scripts/check-infra-drift.sh'로 확인할 것 (배포는 계속한다)"
fi

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
