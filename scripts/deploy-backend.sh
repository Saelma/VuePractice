#!/usr/bin/env bash
# glassvue-backend bootJar 빌드 → /opt 배포 → 서비스 재시작 → 헬스체크
# 사용: ./scripts/deploy-backend.sh   (ecstel 또는 root 어느 쪽으로 실행해도 됨)
set -euo pipefail

BACK_DIR=/home/ecstel/work/glassvue-backend
JAR_DST=/opt/glassvue-backend/glassvue-backend.jar   # 인프라 이름 정리 시 여기만 바꾸면 됨
SERVICE=glassvue-backend

SUDO=""; [ "$(id -u)" -ne 0 ] && SUDO="sudo"

# --- 배포 브랜치 뒤처짐 경고 ---
# 배포는 **이 워크트리에서 빌드**한다. 작업 브랜치에만 커밋하고 머지를 잊으면 옛 코드가 조용히 나간다
# (2026-07-23에 두 번 당했다). 드리프트 검사와 같은 이유로 **막지 않고 경고만** 한다.
BRANCH_CHECK="$(dirname "${BASH_SOURCE[0]}")/check-deploy-branch.sh"
[ -x "$BRANCH_CHECK" ] && "$BRANCH_CHECK" || true

# --- infra/ 사본 드리프트 경고 ---
# 서버 설정을 서버에서 직접 고치고 infra/ 반영을 잊으면, 저장소가 "맞는 것처럼 보이는 틀린 문서"가 된다.
# 배포는 어차피 매번 거치는 관문이라 여기서 알린다. **막지는 않는다** — 드리프트가 배포를 막으면
# 급할 때 스크립트를 우회하게 되어 더 나빠진다.
DRIFT_CHECK="$(dirname "${BASH_SOURCE[0]}")/check-infra-drift.sh"
if [ -x "$DRIFT_CHECK" ] && ! "$DRIFT_CHECK" >/dev/null 2>&1; then
  echo "⚠ infra/ 사본이 서버 설정과 다르다 — './scripts/check-infra-drift.sh'로 확인할 것 (배포는 계속한다)"
fi

# --- 핸드오프 집계 어긋남 경고 ---
# 커밋 표·총괄·종결 기록·조건부 잔여는 **모든 항목이 함께 쓰는 자리**라, 하루가 길어지면 조용히
# 어긋난다(2026-08-03 에 하루 8건 작업하며 세 번 어긋났고, 세 번 다 "다 됐다"고 답한 뒤 드러났다).
# 배포는 그 항목의 기록이 **확정되는 지점**이고 실측값도 손에 있어, 여기가 닫기 가장 싼 자리다
# (WORKING-AGREEMENTS §4-0-1). 규약만으로는 안 걸러져서(규약을 올린 직후 또 안 셌다) 여기서 알린다.
# **막지는 않는다** — 위 두 검사와 같은 판단.
HANDOFF_CHECK="$(dirname "${BASH_SOURCE[0]}")/check-handoff.sh"
[ -x "$HANDOFF_CHECK" ] && "$HANDOFF_CHECK" || true

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

# --- ⚠ 반드시 «먼저 내리고» 덮어쓴다 (2026-08-05) ---
# 전에는 cp -f 로 덮어쓴 뒤 restart 했는데, 그러면 **구 프로세스가 살아 있는 채로 자기 jar 가
# 바뀐다.** JVM 은 클래스를 필요할 때 읽으므로, 종료 경로에서만 쓰는 클래스를 **이미 바뀐 파일**
# 에서 찾다 실패한다:
#     WARN  Failed to stop bean 'webServerGracefulShutdown'
#     NoClassDefFoundError: org/springframework/boot/web/server/GracefulShutdownCallback
# → 웹서버 종료 단계가 통째로 건너뛰어져 연결이 곱게 닫히지 않고, 종료 로그가 스택트레이스로
#   더러워진다(배포 확인에서 로그를 근거로 쓰는데 그게 오염된다 — 같은 날 고친 404/405 건과 한 계열).
# 다운타임은 늘지 않는다: 어차피 restart 로 내렸다 올리던 것이고, 늘어난 건 95M 복사 시간뿐이다.
echo "▶ 서비스 정지…"
$SUDO systemctl stop "$SERVICE"

if ! $SUDO cp -f "$JAR_SRC" "$JAR_DST"; then
  # 내린 상태에서 복사가 깨지면 서비스가 죽은 채로 남는다 — 이전 jar 로 되돌리고 올려 둔다.
  echo "✗ jar 복사 실패 — 이전 jar($JAR_DST.bak)로 되돌리고 기동한다"
  [ -f "$JAR_DST.bak" ] && $SUDO cp -f "$JAR_DST.bak" "$JAR_DST" || true
  $SUDO systemctl start "$SERVICE" || true
  exit 1
fi
$SUDO chmod 644 "$JAR_DST"

echo "▶ 서비스 시작…"
$SUDO systemctl start "$SERVICE"

echo "▶ 헬스체크…"
for i in $(seq 1 30); do
  if curl -sf -m 2 http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo "✅ 백엔드 배포 완료 → :8080 UP"; exit 0
  fi
  sleep 1
done
echo "⚠ 헬스체크 실패 — 'journalctl -u $SERVICE -n 50' 로 로그 확인"; exit 1
