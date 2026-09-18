#!/usr/bin/env bash
# 검증 계정 `esptest` 로 백엔드를 8083 에 띄운다 — **운영과 나눠 쓰는 자원을 전부 격리한 채로** (2026-09-18).
#
# 쓰는 곳: 빈 DB 마이그레이션 검증(db/migration/README 2단계) · 시드(infra/README) · 백업 복구 리허설(infra/README «데이터 백업»).
#
# 🔴 **왜 스크립트인가** — 2026-09-18 하루에 이 앱을 **여섯 번** 손으로 띄웠는데 한 번도 격리하지 않았다.
#    DB 계정만 `esptest` 로 바꿨을 뿐, 나머지는 **운영과 같은 것**을 봤다:
#    ① Redis db0 — 🔴 공지 조회수 플러셔가 **기동 즉시·30초마다** `notice:view:*` 를 SCAN+GETDEL 해 자기 DB(esptest)에 더한다.
#       운영에 아직 반영 전이던 조회수가 있었다면 **그 순간 사라졌다**(창은 매번 30초 이내 · 원장이 없어 되짚을 수 없다).
#       테스트는 이미 2026-07-29 에 같은 사고로 db1 에 격리돼 있었다(build.gradle) — 손으로 띄우는 길만 빠져 있었다.
#    ② 업로드 폴더 — 이미지 정리 배치(기동 5분 뒤)는 **연결된 DB 의 «주인 없는 이미지» 행**을 보고 **폴더의 파일**을 지운다.
#       운영 사본이 든 esptest 로 5분 넘게 띄우면, 사본 이후 운영에서 다시 쓰이게 된 이미지를 **운영 폴더에서** 지울 수 있다.
#    ③ 상품 영구 삭제·알림 정리 배치 — esptest 의 행만 건드리지만, ②와 같은 이유로 파일을 지울 수 있는 것은 끈다.
#    → 한 곳에 묶어 **빠뜨릴 수 없게** 한다(규약으로 적어 두면 또 빠뜨린다 — check-deploy-branch.sh 가 생긴 경위와 같다).
#
# 실행:  scripts/esptest-app.sh          # 기본 프로파일 — Ctrl+C 로 내린다
#        scripts/esptest-app.sh seed     # 시드 — 빈 DB 에 넣고 스스로 끝난다(비어 있지 않으면 1)
# 업로드 폴더는 ESPTEST_UPLOAD_DIR(기본 ~/.cache/glassvue-esptest/uploads) — 운영 폴더를 절대 가리키지 않는다.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -f "$REPO_DIR/.env" ] || { echo "⚠ .env 가 없다"; exit 2; }
set -a; . "$REPO_DIR/.env"; set +a
[ -n "${ESPTEST_PASSWORD:-}" ] || { echo "⚠ .env 에 ESPTEST_PASSWORD 가 없다"; exit 2; }

PROFILE="${1:-}"
UPLOAD_DIR="${ESPTEST_UPLOAD_DIR:-$HOME/.cache/glassvue-esptest/uploads}"
case "$(realpath -m "$UPLOAD_DIR")" in
  /var/www/*) echo "✗ ESPTEST_UPLOAD_DIR 가 운영 경로다($UPLOAD_DIR) — 멈춘다"; exit 2 ;;
esac
mkdir -p "$UPLOAD_DIR"

# DB — 자격증명은 **환경변수**로(명령줄 인자는 /proc/<pid>/cmdline 에 보인다 · db/migration/README 2026-08-04).
export SPRING_DATASOURCE_USERNAME=esptest
export SPRING_DATASOURCE_PASSWORD="$ESPTEST_PASSWORD"
# ⚠ 비밀이 아닌 설정은 **명령줄 인자**로 넘긴다 — 환경변수 이름은 대시 처리 규칙 때문에 한 글자만 틀려도 **조용히 안 먹는다.**
#    인자는 설정 파일과 이름이 똑같아 틀릴 자리가 없다(비밀번호만 위 환경변수).
# ① Redis — 운영 db0 · 테스트 db1 과 겹치지 않는 db2. 조회수 플러셔는 사실상 끈다(24시간).
# ② ③ 파일·행을 지우는 배치는 끈다. 업로드 폴더는 운영이 아닌 곳.
ARGS="--server.port=8083"
ARGS="$ARGS --spring.data.redis.database=2 --notice.view-count.flush-interval-ms=86400000"
ARGS="$ARGS --image.cleanup-enabled=false --catalog.purge-enabled=false --notification.cleanup-enabled=false"
ARGS="$ARGS --app.upload.dir=$UPLOAD_DIR"
[ -n "$PROFILE" ] && ARGS="$ARGS --spring.profiles.active=$PROFILE"
echo "▶ esptest 로 기동 — 8083 · Redis db2 · 업로드 $UPLOAD_DIR · 배치(이미지·영구삭제·알림 정리·조회수) 꺼짐${PROFILE:+ · 프로파일 $PROFILE}"
cd "$REPO_DIR/glassvue-backend"
exec ./gradlew bootRun --args="$ARGS"
