#!/usr/bin/env bash
# 운영 DB 의 «돈과 수량» 불변식을 센다 (2026-09-02, 「돈과 수량이 맞는가」 축).
#
# 왜 스크립트인가: 2026-09-02 에 이 일곱 개를 sqlplus 로 **손으로** 돌려 sold_count 어긋남을
# 찾았는데, 손으로 돌린 SQL 은 그 세션이 끝나면 사라진다. 아침 기준값 재계수(WA §3-5)가
# «행이 몇 개인가» 를 세는 자리라면, 여기는 «그 값들이 서로 맞는가» 를 세는 자리다.
#
# ⚠ 테스트가 아니라 **운영 데이터 점검**이다. 테스트는 시나리오를 만들어 코드를 묻고,
#   이건 이미 쌓인 데이터를 묻는다 — 둘은 서로를 대신하지 못한다:
#   코드가 고쳐져도 **이미 샌 값은 안 돌아온다**(sold_count 가 그 산 증거다).
#
# 실행: scripts/check-money-invariants.sh   (.env 를 읽는다 · 읽기 전용 · sudo 불필요)
#
# 종료코드: 0 = 전부 성립, 1 = 위반 있음, **2 = 판정 불가**(.env 없음 · DB 못 붙음).
# 🔴 판정 불가를 0 으로 내보내지 않는다 — 2026-09-02 에 그 모양에 두 번 물렸다:
#   변형 주입이 컴파일을 깨 「0 test」로 나왔고, .env 없이 돌린 통합이 전부 SKIP 된 채
#   BUILD SUCCESSFUL 로 나왔다. **둘 다 «안 돌았음» 이 «성립» 으로 보였다**(WA §변형주입).
set -uo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ ! -f "$REPO_DIR/.env" ]; then
  echo "⚠ .env 가 없다 — 접속 정보를 못 읽는다. **판정 불가**(성립이 아니다)."; exit 2
fi
set -a; . "$REPO_DIR/.env"; set +a

# sqlplus 는 ORACLE_HOME 없이는 못 뜬다(Error 6 / SP2-0667). -L 은 1회만 시도(2026-07-29).
export ORACLE_HOME="${ORACLE_HOME:-/opt/oracle/product/19c/dbhome_1}"
export PATH="$ORACLE_HOME/bin:$PATH"
export NLS_LANG="${NLS_LANG:-KOREAN_KOREA.AL32UTF8}"

OUT=$(sqlplus -s -L "$DB_USER/$DB_PASSWORD@//$DB_HOST:$DB_PORT/$DB_SERVICE" <<'SQL'
set pagesize 0 feedback off heading off linesize 200
whenever sqlerror exit 2

-- ① 품목 줄금액 = 단가 × 수량
select 'line_total = price*qty|'||count(*) from order_item where line_total <> price*quantity;

-- ② 주문 상품합계 = 품목 줄금액의 합
select 'orders.total_price = SUM(line_total)|'||count(*) from (
  select o.id from orders o join order_item i on i.order_id=o.id
   group by o.id, o.total_price having o.total_price <> sum(i.line_total));

-- ③ 되돌린 수량이 주문 수량을 못 넘는다
select 'cancelled+returned <= quantity|'||count(*) from order_item
 where nvl(cancelled_quantity,0)+nvl(returned_quantity,0) > quantity;

-- ④⑤ 주문에 떠 둔 취소·반품 상품합계가 품목과 맞는다
select 'orders.cancelled_items_total|'||count(*) from (
  select o.id from orders o join order_item i on i.order_id=o.id
   group by o.id, o.cancelled_items_total
   having nvl(o.cancelled_items_total,0) <> sum(i.price*nvl(i.cancelled_quantity,0)));
select 'orders.returned_items_total|'||count(*) from (
  select o.id from orders o join order_item i on i.order_id=o.id
   group by o.id, o.returned_items_total
   having nvl(o.returned_items_total,0) <> sum(i.price*nvl(i.returned_quantity,0)));

-- ⑥ 🔴 되돌린 몫이 원래 낸 것을 못 넘는다 — I-11 의 「잔돈이 현금으로 샌다」가 사실이면 여기가 깨진다.
--    ⚠ 지금 0 인 것은 «그 버그가 없다» 가 아니라 «아직 안 밟혔다» 다(I-11 은 재현 테스트를 요구한다).
select 'refund <= original (I-11 잔돈)|'||count(*) from orders
 where nvl(cancelled_coupon_discount,0)+nvl(returned_coupon_discount,0) > nvl(coupon_discount,0)
    or nvl(cancelled_point,0)+nvl(returned_point,0) > nvl(used_point,0)
    or nvl(reversed_earned_point,0) > nvl(earned_point,0);

-- ⑦ 적립금 원장이 잔액과 맞는다 (⚠ 조인 키는 member_id 다 — point_history 에 계정 칸은 없다)
select 'point 잔액 = 원장 합|'||count(*) from (
  select pa.member_id from point_account pa
   where pa.balance <> (select nvl(sum(ph.amount),0) from point_history ph where ph.member_id=pa.member_id));

-- ⑧ 🔴 판매량 = 실판매(주문 − 취소 − 반품). 2026-09-02 현재 **알려진 위반이 있다**(아래 주석).
select 'product.sold_count = 실판매|'||count(*) from (
  select p.id from product p left join order_item i on i.product_id=p.id
   group by p.id, p.sold_count
   having nvl(p.sold_count,0) <> nvl(sum(i.quantity-nvl(i.cancelled_quantity,0)-nvl(i.returned_quantity,0)),0));
exit
SQL
)

if [ $? -ne 0 ]; then
  echo "⚠ DB 에 못 붙었거나 쿼리가 실패했다 — 판정 불가:"; echo "$OUT" | sed 's/^/    /'; exit 2
fi

# 🔴 **알려진 위반**: sold_count 는 2026-09-02 현재 6건이 어긋나 있다(전부 낮은 쪽).
# 08-25 이전에 «되돌릴 때 원본 수량을 썼던» 결함의 잔재이고, addSoldCount 가 0 에서 바닥을 쳐
# 그 손실이 영구가 됐다. **현재 코드는 안 샌다**(SoldCountLifecycleIntegrationTest 6건이 지킨다).
# 보정할지는 아직 안 정했다 — 정하면 이 숫자를 0 으로 내리고 이 주석을 지운다.
KNOWN_SOLD_COUNT=6

FAIL=0
SEEN=0
while IFS='|' read -r name n; do
  [ -z "${name// }" ] && continue
  name="${name#"${name%%[![:space:]]*}"}"; n="${n// }"
  SEEN=$((SEEN+1))
  if [ "$n" = "0" ]; then
    printf '  ✅ %-34s 위반 0\n' "$name"
  elif [ "$name" = "product.sold_count = 실판매" ] && [ "$n" = "$KNOWN_SOLD_COUNT" ]; then
    printf '  ⚠  %-34s 위반 %s — **알려진 잔재**(09-02, 안 늘었다)\n' "$name" "$n"
  else
    printf '  🔴 %-34s 위반 %s\n' "$name" "$n"; FAIL=1
  fi
done <<< "$OUT"

# ⚠ 불변식 여덟 개를 다 읽었는가 — 덜 읽었으면 «성립» 이 아니라 «못 셌다» 다.
if [ "$SEEN" -ne 8 ]; then
  echo "  ⚠ 불변식 8개 중 ${SEEN}개만 읽혔다 — **판정 불가**."; exit 2
fi

if [ "$FAIL" -eq 1 ]; then
  echo "  → 새 위반이다. handoffs/2026-09-02-handoff.md §4 가 이 불변식들이 무엇인지 적어 뒀다."
  exit 1
fi
exit 0
