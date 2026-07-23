#!/usr/bin/env bash
# infra/ 의 사본이 서버의 실제 설정과 어긋났는지 확인한다.
#
# 사본은 조용히 어긋난다 — 서버에서 급히 고치고 저장소 반영을 잊으면
# infra/ 는 "맞는 것처럼 보이는 틀린 문서"가 되어 없느니만 못해진다.
# 배포 전이나 설정을 만진 뒤에 돌린다. sudo 불필요(읽기만 한다).
#
# 종료코드: 0 = 일치, 1 = 차이 있음
set -uo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DRIFT=0

# 저장소 경로 : 서버 경로
PAIRS=(
  "infra/nginx/nginx.conf:/etc/nginx/nginx.conf"
  "infra/nginx/conf.d/glassvue.conf:/etc/nginx/conf.d/glassvue.conf"
  "infra/systemd/glassvue-backend.service:/etc/systemd/system/glassvue-backend.service"
  "infra/systemd/oracledb_ESPDB-19c.service.d/override.conf:/etc/systemd/system/oracledb_ESPDB-19c.service.d/override.conf"
)

for pair in "${PAIRS[@]}"; do
  repo="$REPO_DIR/${pair%%:*}"
  live="${pair##*:}"

  if [ ! -f "$repo" ]; then
    echo "✗ 저장소에 없음: ${pair%%:*}"; DRIFT=1; continue
  fi
  if [ ! -r "$live" ]; then
    echo "? 서버 파일을 읽을 수 없음(권한/부재): $live"; DRIFT=1; continue
  fi

  if diff -q "$repo" "$live" >/dev/null; then
    echo "✅ ${pair%%:*}"
  else
    echo "⚠ 다름: ${pair%%:*}  ↔  $live"
    diff -u "$repo" "$live" | sed 's/^/    /'
    DRIFT=1
  fi
done

echo
if [ "$DRIFT" -eq 0 ]; then
  echo "저장소와 서버 설정이 일치한다."
else
  echo "차이가 있다. 어느 쪽이 맞는지 판단하고 맞춘다 — 반영 명령은 infra/README.md 참고."
  echo "원칙: 저장소가 원본, 서버는 그 사본이다."
fi
exit "$DRIFT"
