#!/usr/bin/env bash
# 백업이 **복구되는가** 를 본다 — 덤프를 검증 계정 `esptest` 에 풀고, 풀린 행 수를 **덤프 로그**와 대조한다 (2026-09-18, F-5).
#
# 왜: 백업은 «떴다» 가 아니라 «풀린다» 가 확인돼야 백업이다. expdp 가 성공해도 덤프가 못 풀리면 그날 알 방법이 없다.
# ⚠ 대조 기준은 **운영 `ESP` 의 지금 값이 아니라 덤프 로그의 `exported … N rows`** 다 — 백업 뒤에 운영이 움직였으면
#   지금 값과는 어긋나는 게 정상이라, 그걸 기준으로 삼으면 «복구 실패» 로 오판한다.
#
# 🔴 **운영 스키마는 건드리지 않는다** — 쓰는 곳은 `esptest` 하나다(`remap_schema=ESP:ESPTEST`, 테이블은 replace).
# ⚠ `esptest` 는 마이그레이션 빈 DB 검증 계정이기도 하다 — 이걸 돌리면 **운영 데이터 사본이 들어간다.**
#   다음 빈 DB 검증은 어차피 비우는 데서 시작하므로(db/migration/README 1단계) 그대로 둔다.
# 🔴 **테이블만 오는 게 아니다** — 운영의 시노님(`FLYWAY_SCHEMA_HISTORY`)도 따라온다. 테이블·시퀀스만 지우면 그게 남아
#   Flyway 가 V1 을 건너뛴다(2026-09-18 실측) → 비울 때는 반드시 `scripts/reset-esptest.sh`(객체 전부 · user_objects 로 센다).
#
# 실행: scripts/check-backup-restore.sh [STAMP]   (STAMP 를 안 주면 가장 최근 벌 · sudo 불필요)
# 종료코드: 0 = 전 테이블 행 수 일치, 1 = 어긋남, 2 = 판정 불가.
set -uo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR=/opt/glassvue-backup
DIR_OBJECT=GLASSVUE_BACKUP
TARGET=ESPTEST

[ -f "$REPO_DIR/.env" ] || { echo "⚠ .env 가 없다 — **판정 불가**."; exit 2; }
set -a; . "$REPO_DIR/.env"; set +a
export ORACLE_HOME="${ORACLE_HOME:-/opt/oracle/product/19c/dbhome_1}"
export PATH="$ORACLE_HOME/bin:$PATH"
export LD_LIBRARY_PATH="$ORACLE_HOME/lib:${LD_LIBRARY_PATH:-}"
export NLS_LANG=AMERICAN_AMERICA.AL32UTF8   # 로그 문구를 센다 — 영어로 고정(backup-db.sh 와 같은 이유)

STAMP="${1:-$(ls -1t "$BACKUP_DIR"/esp-*.dmp 2>/dev/null | head -1 | sed -E 's/.*esp-([0-9-]+)\.dmp/\1/')}"
DUMP="esp-$STAMP.dmp"
SRC_LOG="$BACKUP_DIR/esp-$STAMP.log"
IMP_LOG="restore-check-$STAMP.log"
[ -n "$STAMP" ] && [ -r "$BACKUP_DIR/$DUMP" ] && [ -r "$SRC_LOG" ] \
  || { echo "⚠ 덤프·로그를 못 찾는다(STAMP='$STAMP') — **판정 불가**."; exit 2; }

# 기준: 덤프 로그의 테이블별 행 수 — `. . exported "ESP"."ORDERS"  45.6 KB  87 rows`
EXPECTED=$(grep '^\. \. exported ' "$SRC_LOG" | awk '{gsub(/"/,"",$4); split($4,a,"."); print a[2], $(NF-1)}' | sort)
[ -n "$EXPECTED" ] || { echo "⚠ 덤프 로그에서 테이블을 못 읽었다 — **판정 불가**(「0 == 0」은 성립이 아니다)."; exit 2; }

CONNECT="$DB_USER/$DB_PASSWORD@//$DB_HOST:$DB_PORT/$DB_SERVICE"
PARFILE=$(umask 077; mktemp)
SQLFILE=$(umask 077; mktemp)
trap 'rm -f "$PARFILE" "$SQLFILE"' EXIT
# 비밀번호는 명령줄에 안 올린다(backup-db.sh 와 같은 이유 — /proc/<pid>/cmdline).
printf 'userid=%s\ndirectory=%s\ndumpfile=%s\nlogfile=%s\nremap_schema=%s:%s\ntable_exists_action=replace\n' \
  "$CONNECT" "$DIR_OBJECT" "$DUMP" "$IMP_LOG" "$DB_USER" "$TARGET" > "$PARFILE"

echo "▶ impdp $DUMP → $TARGET (remap · replace)"
impdp parfile="$PARFILE" > /dev/null 2>&1
IMP_RC=$?
# ⚠ impdp 는 «사용자·시퀀스가 이미 있다»(ORA-31684) 를 오류로 세서 종료코드 5 로 끝난다 — 데이터와 무관하다.
#   그래서 종료코드가 아니라 **행 수**로 판정하고, 그 밖의 ORA- 가 있으면 따로 보여 준다.
OTHER_ERR=$(grep -oE 'ORA-[0-9]+' "$BACKUP_DIR/$IMP_LOG" 2>/dev/null | grep -v 'ORA-31684' | sort | uniq -c)

# 풀린 쪽: esptest 의 테이블별 실제 행 수(통계가 아니라 count(*)).
{
  printf 'connect %s\nset pagesize 0 feedback off heading off linesize 200 serveroutput on\nwhenever sqlerror exit 2\n' "$CONNECT"
  printf "declare n number; begin for t in (select table_name from all_tables where owner='%s') loop\n" "$TARGET"
  printf "execute immediate 'select count(*) from %s.\"'||t.table_name||'\"' into n; dbms_output.put_line(t.table_name||' '||n); end loop; end;\n/\nexit\n" "$TARGET"
} > "$SQLFILE"
ACTUAL=$(sqlplus -s -L /nolog < "$SQLFILE" | grep -E '^[A-Za-z0-9_$#]+ [0-9]+$' | sort)

echo "  impdp 종료코드 $IMP_RC · 덤프 로그 테이블 $(echo "$EXPECTED" | wc -l) · $TARGET 테이블 $(echo "$ACTUAL" | grep -c .)"
[ -n "$OTHER_ERR" ] && { echo "  ⚠ ORA-31684(이미 있음) 말고 다른 오류:"; echo "$OTHER_ERR" | sed 's/^/    /'; }

DIFF=$(diff <(echo "$EXPECTED") <(echo "$ACTUAL"))
if [ -n "$DIFF" ]; then
  echo "  ✗ 행 수가 어긋난다(< 덤프 로그 · > $TARGET):"; echo "$DIFF" | sed 's/^/    /'; exit 1
fi
echo "✓ 복구 성립 — $STAMP 의 $(echo "$EXPECTED" | wc -l)개 테이블 · 행 합계 $(echo "$EXPECTED" | awk '{s+=$2} END {print s}') 가 $TARGET 에 그대로 풀렸다"
exit 0
