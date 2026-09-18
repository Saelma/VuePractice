#!/usr/bin/env bash
# 검증 전용 계정 `esptest` 의 스키마를 **통째로** 비운다 — 빈 DB 마이그레이션 검증 · 시드 확인의 1단계 (2026-09-18).
#
# 원본은 `glassvue-backend/src/main/resources/db/migration/README.md` 1단계였다(sqlplus 블록). 스크립트로 옮긴 이유 둘:
#   ① 🔴 **그 블록은 테이블·시퀀스만 지우고 그 둘만 셌다.** 2026-09-18 에 백업 복구 확인(impdp)이 운영의 시노님
#      `FLYWAY_SCHEMA_HISTORY`(ESP 에 07-16 부터 있던 대문자 별명)를 함께 가져왔는데, 비우고 나서 «0 · 0» 이 나왔다.
#      Flyway 는 **객체가 하나라도 있으면** 빈 스키마로 안 보고 V1 을 baseline 으로 건너뛴다 → V2 가 ORA-00942.
#      «다 셌다» 가 덜 센 것이었다(WA §3-6) → **객체 전부**를 지우고 **`user_objects` 전체**를 센다.
#   ② 세션 임시 폴더에 두던 사본은 세션과 함께 사라졌다(09-17 · 09-18 두 번).
#
# 🔴 **`esptest` 가 아니면 아무것도 안 지운다** — 접속 계정을 PL/SQL 안에서 다시 확인하고 아니면 멈춘다.
# ⚠ 이 스크립트는 **지운다**(검증 계정이라도) — 자동 모드 분류기가 막으면 사람이 `!` 로 돌린다(09-17·09-18 실측).
#
# 실행: scripts/reset-esptest.sh   (.env 의 ESPTEST_PASSWORD · sudo 불필요)
# 종료코드: 0 = 객체 0개, 1 = 남은 객체 있음(목록을 찍는다), 2 = 판정 불가.
set -uo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -f "$REPO_DIR/.env" ] || { echo "⚠ .env 가 없다 — **판정 불가**."; exit 2; }
set -a; . "$REPO_DIR/.env"; set +a
# ⚠ 셋 다 먼저 본다 — 빠지면 아래 heredoc 이 `set -u` 로 죽으며 **1(«남았다»)** 로 끝나 오판된다(/code-review).
for v in ESPTEST_PASSWORD DB_HOST DB_PORT; do
  [ -n "${!v:-}" ] || { echo "⚠ .env 에 $v 가 없다 — **판정 불가**."; exit 2; }
done
export ORACLE_HOME="${ORACLE_HOME:-/opt/oracle/product/19c/dbhome_1}"
export PATH="$ORACLE_HOME/bin:$PATH"
export LD_LIBRARY_PATH="$ORACLE_HOME/lib:${LD_LIBRARY_PATH:-}"
export NLS_LANG=AMERICAN_AMERICA.AL32UTF8

# 비밀번호를 명령줄에 안 올린다 — `connect` 를 표준입력으로 넘긴다(명령줄 인자는 `/proc/<pid>/cmdline` 에 보인다 — sqlplus 는 뜬 직후 접속 문자열을 공백으로 덮지만(2026-09-18 실측) 그 전 짧은 창이 있고, expdp·java 는 덮어 준다는 보장이 없다).
# `whenever sqlerror` 는 `connect` **앞** — 뒤에 두면 접속 실패가 0 으로 끝나 뒤 PL/SQL 이 «Not connected» 로 헛돈다.
SQLFILE=$(umask 077; mktemp)
trap 'rm -f "$SQLFILE"' EXIT
cat > "$SQLFILE" <<SQL
whenever sqlerror exit 2
connect esptest/${ESPTEST_PASSWORD}@//${DB_HOST}:${DB_PORT}/${DB_SERVICE:-espdb}
set serveroutput on pagesize 0 feedback off heading off linesize 200
BEGIN
  IF USER <> 'ESPTEST' THEN RAISE_APPLICATION_ERROR(-20001, 'not esptest: '||USER); END IF;
  -- 의존하는 쪽부터: 뷰·시노님 → 테이블(제약·인덱스·LOB·트리거가 함께 간다) → 나머지.
  FOR o IN (SELECT object_name, object_type FROM user_objects
             WHERE object_type IN ('VIEW','SYNONYM','MATERIALIZED VIEW')) LOOP
    EXECUTE IMMEDIATE 'DROP '||o.object_type||' "'||o.object_name||'"';
  END LOOP;
  FOR t IN (SELECT table_name FROM user_tables WHERE nested = 'NO') LOOP
    EXECUTE IMMEDIATE 'DROP TABLE "'||t.table_name||'" CASCADE CONSTRAINTS PURGE';
  END LOOP;
  FOR o IN (SELECT object_name, object_type FROM user_objects
             WHERE object_type IN ('SEQUENCE','PROCEDURE','FUNCTION','PACKAGE','TYPE')) LOOP
    EXECUTE IMMEDIATE 'DROP '||o.object_type||' "'||o.object_name||'"'
      ||CASE WHEN o.object_type = 'TYPE' THEN ' FORCE' END;
  END LOOP;
  EXECUTE IMMEDIATE 'PURGE RECYCLEBIN';
END;
/
select 'LEFT|'||object_type||'|'||object_name from user_objects order by object_type, object_name;
select 'COUNT|'||count(*) from user_objects;
exit
SQL

OUT=$(sqlplus -s -L /nolog < "$SQLFILE")
RC=$?
COUNT=$(echo "$OUT" | sed -n 's/^COUNT|//p' | tr -d '[:space:]')
if [ "$RC" -ne 0 ] || ! [[ "$COUNT" =~ ^[0-9]+$ ]]; then
  echo "⚠ 비우기를 끝까지 못 했다(sqlplus 종료코드 $RC) — **판정 불가**:"; echo "$OUT" | sed 's/^/  /' | head -20; exit 2
fi
if [ "$COUNT" -ne 0 ]; then
  echo "✗ esptest 에 객체가 ${COUNT}개 남았다 — Flyway 가 V1 을 건너뛴다:"
  echo "$OUT" | sed -n 's/^LEFT|/  /p'; exit 1
fi
echo "✓ esptest 비움 — user_objects 0개 (빈 DB 검증을 시작해도 된다)"
exit 0
