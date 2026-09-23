#!/usr/bin/env bash
# 상품 하나를 **가리키는 것**을 전부 센다 — 영구 삭제(purge) 전후를 같은 잣대로 대조하려고 (2026-09-23).
#
# 왜: 영구 삭제의 운영 경로 — 알림 정리(M-4, 09-10) · 찜·재입고 정리(O-7, 09-11) · FK CASCADE — 는
#   **통합 테스트로만** 증명됐다. 운영 배치가 실제로 상품을 지우는 첫 날(2026-09-28, `ZZP-탈퇴검증`)
#   전후를 세려면 칸 목록을 손으로 적으면 안 된다 — O-7 이 바로 «손으로 적은 목록» 에서 샜다.
#   그래서 `product_id` 칸을 `user_tab_columns` 에서 꺼내고, «칸을 다 셌나» 까지 찍는다(WA §3-6).
#
# ⚠ 무엇이 «지워져야 / 남아야» 하는지는 여기 적지 않는다 — 그 분류의 원본은
#   `ProductPurgeReferenceIntegrationTest`(CASCADE · 이벤트 · 남김)다. 여기 또 적으면 두 벌이 된다.
#   이 스크립트는 **센다**. 판정은 그날 핸드오프의 기대값 표와 맞댄다.
# ⚠ 찜·재입고 리스너는 **로그를 안 남긴다** — 그 둘은 DB 로만 보인다. 알림 핸들러는 지운 게 있을 때만 남긴다.
#
# 실행: scripts/check-product-refs.sh 01a0c2bf-a59b-7c97-92d7-7a03b8e182d6
#   (이름이 아니라 **id** 를 받는다 — 영구 삭제 뒤에는 이름으로 못 찾는다 · 읽기 전용 · sudo 불필요)
#
# 종료코드: 0 = 셌다, **2 = 못 셌다**(인자 모양 · .env 없음 · DB 못 붙음 · `product_id` 칸 0개).
set -uo pipefail
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ID="${1:-}"
if ! [[ "$ID" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]; then
  echo "⚠ 상품 id(소문자 UUID, 대시 포함)를 인자로 준다 — 받은 값: '${ID}'. **판정 불가**"; exit 2
fi
HEX=$(echo "$ID" | tr -d '-' | tr 'a-f' 'A-F')

[ -f "$REPO_DIR/.env" ] || { echo "⚠ .env 가 없다 — **판정 불가**"; exit 2; }
set -a; . "$REPO_DIR/.env"; set +a
export ORACLE_HOME="${ORACLE_HOME:-/opt/oracle/product/19c/dbhome_1}"
export PATH="$ORACLE_HOME/bin:$PATH"
export NLS_LANG="${NLS_LANG:-KOREAN_KOREA.AL32UTF8}"

# 비밀번호는 명령줄이 아니라 표준입력의 `connect` 로 · `whenever sqlerror` 는 `connect` **앞**(check-money-invariants.sh 와 같은 이유).
OUT=$( { printf 'whenever sqlerror exit 2\nconnect %s/%s@//%s:%s/%s\n' "$DB_USER" "$DB_PASSWORD" "$DB_HOST" "$DB_PORT" "$DB_SERVICE"
         cat <<SQL
set pagesize 0 feedback off heading off linesize 300 serveroutput on
whenever sqlerror exit 2
declare
  v_id raw(16) := hextoraw('${HEX}'); v_n number; v_cols number := 0; v_state varchar2(200);
begin
  select count(*) into v_n from product where id = v_id;
  if v_n = 0 then v_state := '없다(영구 삭제됐거나 id 가 틀렸다)';
  else select case when deleted_at is null then '살아 있다' else '삭제 대기 · deleted_at '
              ||to_char(deleted_at at time zone 'Asia/Seoul','YYYY-MM-DD HH24:MI:SS')||' KST' end
         into v_state from product where id = v_id;
  end if;
  dbms_output.put_line('product|'||v_state);
  for c in (select table_name from user_tab_columns where column_name = 'PRODUCT_ID' order by table_name) loop
    v_cols := v_cols + 1;
    execute immediate 'select count(*) from "'||c.table_name||'" where product_id = :1' into v_n using v_id;
    dbms_output.put_line(lower(c.table_name)||'.product_id|'||v_n);
  end loop;
  select count(*) into v_n from notification where link like '%${ID}%';
  dbms_output.put_line('notification.link|'||v_n);
  dbms_output.put_line('#cols|'||v_cols);
end;
/
SQL
       } | timeout 60 sqlplus -s -L /nolog ); RC=$?
if [ "$RC" -ne 0 ]; then
  echo "⚠ DB 를 못 읽었다(sqlplus rc=$RC) — **판정 불가**"; echo "$OUT" | sed 's/^/  /' | head -10; exit 2
fi
COLS=$(echo "$OUT" | awk -F'|' '$1=="#cols"{print $2}')
if ! [[ "${COLS:-}" =~ ^[0-9]+$ ]] || [ "$COLS" -eq 0 ]; then
  echo "⚠ \`product_id\` 칸을 하나도 못 읽었다 — **판정 불가**"; exit 2
fi
ROWS=$(echo "$OUT" | grep -c '\.product_id|')
echo "상품 $ID  ($(date '+%F %T %Z'))"
echo "$OUT" | grep -v '^#cols|' | awk -F'|' '{printf "  %-34s %s\n", $1, $2}'
echo "→ product_id 칸 ${COLS}개 중 ${ROWS}개를 셌다 + 알림 링크"
[ "$ROWS" -eq "$COLS" ] || { echo "⚠ 센 칸이 모자라다 — **판정 불가**"; exit 2; }
