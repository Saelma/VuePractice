#!/usr/bin/env bash
# 운영 DB 의 «돈과 수량» 불변식 **11개**를 센다 (2026-09-02, 「돈과 수량이 맞는가」 축).
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

-- ⑧ 판매량 = 실판매. 🔴 **전량 취소·전량 반품은 `order_item` 의 수량 칸을 안 건드린다** —
--    그 칸은 «부분» 만 기록하고 전량은 주문 `status` 가 들고 있다(OrderStatsRepository 의 TOP 쿼리와
--    같은 판단: *"상태 필터는 전량 취소·반품만 걸러내므로 부분은 PAID·DELIVERED 로 남는다"*).
--    ⚠ 그래서 status 를 안 보고 품목 칸만 빼면 **전량 취소된 주문이 «팔린 것» 으로 잡힌다** —
--    2026-09-02 에 그 식으로 «위반 6건» 이라는 허깨비를 만들었다.
--    ⚠ ORDERED(결제 전)는 **센다** — 핸들러가 «주문됨» 에 더하기 때문이다(정의가 그렇다).
select 'product.sold_count = 실판매|'||count(*) from (
  select p.id from product p left join order_item i on i.product_id=p.id
                             left join orders o on o.id=i.order_id
   group by p.id, p.sold_count
   having nvl(p.sold_count,0) <> nvl(sum(
     case when o.status in ('CANCELLED','RETURNED') then 0
          else i.quantity-nvl(i.cancelled_quantity,0)-nvl(i.returned_quantity,0) end),0));

-- ⑨⑩⑪ 재고 원장 (2026-09-02, §K-5). 🔴 **적립금 원장(⑦)과 같은 모양이다** —
--    `stock_history` 가 `stock_after` 를 들고 있으므로 «이력이 현재 값을 설명하는가» 를 물을 수 있다.
--    ⚠ 처음 여덟 개를 세울 때 이 자리를 **안 봤다**(적립금만 원장으로 취급했다).

-- ⑨ 마지막 이력의 `stock_after` 가 지금 재고와 같다.
select 'stock 마지막 이력 = 현재 재고|'||count(*) from (
  select v.id from product_variant v
    join (select variant_id, max(created_at) mx from stock_history group by variant_id) l on l.variant_id=v.id
    join stock_history h on h.variant_id=v.id and h.created_at=l.mx
   where h.stock_after <> v.stock);

-- ⑩ 이력의 증감 합이 «최초 이전 → 현재» 변화와 같다(중간에 이력 없이 움직인 적이 없다).
select 'stock 이력 합 = 재고 변화|'||count(*) from (
  select h.variant_id from stock_history h group by h.variant_id
   having sum(h.quantity) <> (select v.stock from product_variant v where v.id=h.variant_id)
                           - (select min(h2.stock_after - h2.quantity) keep (dense_rank first order by h2.created_at)
                                from stock_history h2 where h2.variant_id=h.variant_id));

-- ⑪ 재고가 «이력 없이» 생기지 않는다 — 등록도 `ADMIN_CREATE` 로 남긴다.
--    🔴 **날짜를 박지 않고 자기보정한다**: 첫 `ADMIN_CREATE` **이후에 생긴 옵션**만 본다.
--    ⚠ 그전 옵션(`무선 키보드`, 07-24 생성)은 **그 기능이 있기 전** 것이라 이력이 없는 게 맞다 —
--       하드코딩하면 다음에 같은 일이 또 나도 이 줄이 못 잡는다.
select 'stock 이력 없이 생긴 재고|'||count(*) from product_variant v
 where v.created_at >= (select min(created_at) from stock_history where reason='ADMIN_CREATE')
   and not exists (select 1 from stock_history h where h.variant_id=v.id);
exit
SQL
)

if [ $? -ne 0 ]; then
  echo "⚠ DB 에 못 붙었거나 쿼리가 실패했다 — 판정 불가:"; echo "$OUT" | sed 's/^/    /'; exit 2
fi

# ⚠ 알려진 위반은 **없다**. 2026-09-02 실측: 열한 개 전부 0.
# 🔴 «sold_count 에 08-25 이전의 잔재가 남아 있다» 는 오래 물려받힌 주장은 **실측하니 거짓**이었다
#    (백로그 §K-1). 값을 다시 0 이 아닌 것으로 두려면 **왜 그런지를 여기 적고** 기준을 올린다.
KNOWN_SOLD_COUNT=0

FAIL=0
SEEN=0
while IFS='|' read -r name n; do
  [ -z "${name// }" ] && continue
  name="${name#"${name%%[![:space:]]*}"}"; n="${n// }"
  SEEN=$((SEEN+1))
  if [ "$n" = "0" ]; then
    printf '  ✅ %-34s 위반 0\n' "$name"
  elif [ "$name" = "product.sold_count = 실판매" ] && [ "$n" = "$KNOWN_SOLD_COUNT" ]; then
    printf '  ⚠  %-34s 위반 %s — **알려진 잔재**(기준과 같다)\n' "$name" "$n"
  else
    printf '  🔴 %-34s 위반 %s\n' "$name" "$n"; FAIL=1
  fi
done <<< "$OUT"

# ⚠ 불변식 여덟 개를 다 읽었는가 — 덜 읽었으면 «성립» 이 아니라 «못 셌다» 다.
if [ "$SEEN" -ne 11 ]; then
  echo "  ⚠ 불변식 11개 중 ${SEEN}개만 읽혔다 — **판정 불가**."; exit 2
fi

if [ "$FAIL" -eq 1 ]; then
  echo "  → 새 위반이다. handoffs/2026-09-02-handoff.md §4 가 이 불변식들이 무엇인지 적어 뒀다."
  exit 1
fi
exit 0
