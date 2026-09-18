#!/usr/bin/env bash
# 운영 DB 의 «원장이 서로 맞는가» 불변식 **20개**를 센다.
# (2026-09-02 「돈과 수량」 11개 · 2026-09-03 「쿠폰·알림 원장」 8개를 더했다)
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
--    🔴 **재고 0 으로 등록한 옵션은 제외한다**(2026-09-18) — 등록은 수량 0 이면 이력을 **일부러** 안 남긴다
--       («변동 없음 — 원장에 남길 것이 없다», ProductCommandService.recordAdmin). 생긴 재고가 없으니 이 규칙을 안 어긴다.
--       이 줄이 그걸 위반으로 셌다 — 운영엔 07월 이후 재고 0 등록이 없어 안 보였고, 시드의 품절 표본이 처음 밟았다.
select 'stock 이력 없이 생긴 재고|'||count(*) from product_variant v
 where v.created_at >= (select min(created_at) from stock_history where reason='ADMIN_CREATE')
   and v.stock <> 0
   and not exists (select 1 from stock_history h where h.variant_id=v.id);

-- ⑫~⑲ 쿠폰·알림 원장 (2026-09-03). 09-02 이월이 «11개가 «다» 라는 근거는 없다 —
--    쿠폰 발급·알림은 아직 이런 각도로 안 봤다» 로 남긴 자리다.
--
-- ⚠ **DB 가 이미 막는 것은 여기 안 넣는다.** 실측으로 확인한 유니크 인덱스가 둘 있다:
--    `UX_MEMBER_COUPON_ONCE(member_id, coupon_id)` — 같은 쿠폰 중복 발급
--    `UX_COUPON_WELCOME` — 가입 쿠폰은 최대 하나
--    🔴 둘은 **절대 안 깨지므로 세면 «성립» 이 늘 뿐 아무것도 안 지킨다.** 여기 있는 여덟은
--    전부 **앱 코드만 지키는** 것들이다(리스너·서비스 가드).

-- ⑫⑬ 탈퇴 리스너가 남긴 것이 없다 — DB 에 FK/CASCADE 가 없어 **앱이 지우는 수밖에 없다**.
select 'coupon 고아 member_coupon|'||count(*) from member_coupon mc
 where not exists (select 1 from member m where m.id=mc.member_id);
select 'coupon 고아 notification|'||count(*) from notification n
 where not exists (select 1 from member m where m.id=n.member_id);

-- ⑭⑮ 주문 ↔ 발급쿠폰의 «사용됨» 이 서로 맞는다. 취소·반품은 `MemberCoupon.restore()` 로 되돌리므로
--    🔴 **살아 있는 주문이 건 쿠폰은 반드시 사용됨**이고, 거꾸로 **사용됨인데 그것을 쓰는 살아 있는
--    주문이 없으면 쿠폰이 «증발» 한 것**이다(고객은 못 쓰는데 원장엔 쓴 것으로 남는다).
select 'coupon 산 주문의 쿠폰이 미사용|'||count(*) from orders o
   join member_coupon mc on mc.id=o.member_coupon_id
 where o.status not in ('CANCELLED','RETURNED') and mc.used_at is null;
select 'coupon 사용표시인데 쓸 주문 없음|'||count(*) from member_coupon mc
 where mc.used_at is not null
   and not exists (select 1 from orders o
                    where o.member_coupon_id=mc.id and o.status not in ('CANCELLED','RETURNED'));

-- ⑯ 할인이 붙었으면 어느 발급쿠폰인지도 있어야 한다.
--    🔴 **V46 이후 주문만 본다** — 그 컬럼 자체가 V46(2026-08-11)에 생겼고, 컬럼 주석이
--    *«NULL 이면 미사용이거나 V46 이전 주문»* 이라고 적어 뒀다. ⚠ 안 좁히면 `20260728-0478`
--    한 건이 늘 걸린다(2026-09-03 에 그렇게 «위반 1» 이라는 허깨비를 만들었다).
--    ⚠ 날짜를 박지 않고 flyway 에게 묻는다 — ⑪ 과 같은 자기보정이다.
select 'coupon 할인>0 인데 쿠폰 NULL|'||count(*) from orders o
 where o.coupon_discount>0 and o.member_coupon_id is null
   and o.created_at > (select "installed_on" from "flyway_schema_history" where "version"='46');

-- ⑰⑱ 할인 금액의 경계. 쿠폰 상한은 결제 때 `Coupon.meetsMinOrder`·`maxDiscountAmount` 가 잡고,
--    회수분은 부분 취소·반품이 몫을 나눠 뗀다 — 둘 다 앱 계산이라 넘칠 수 있다.
select 'coupon 할인 > 상품합계|'||count(*) from orders where coupon_discount > total_price;
select 'coupon 회수몫 > 원래 할인|'||count(*) from orders
 where nvl(cancelled_coupon_discount,0)+nvl(returned_coupon_discount,0) > nvl(coupon_discount,0);

-- ⑲ 🔴 알림 링크에 «null» 이 박히지 않았다. TROUBLESHOOTING 「알림은 오는데 누르면 깨진 페이지」 —
--    링크를 문자열로 조립하는데 재료가 null 이면 `/products/null#…` 이 저장된다.
--    ⚠ **서버 로그에 아무것도 안 남는다**(예외도 에러도 아니다) — 그래서 이 각도 말고는 못 잡는다.
select 'notif 링크에 null 박힘|'||count(*) from notification where link like '%null%';

-- ⑳ 🔴 알림이 가리키는 **상품이 실제로 있다**(2026-09-10, BACKLOG §M-4).
--    ⚠ ⑲ 와 성질이 다르다 — ⑲ 는 «문자열이 잘못 조립됐나» 이고 이건 «가리키는 것이 아직 있나» 다.
--    ⑲ 가 0 인데 **55건이 깨져 있었다** — 링크는 멀쩡한 모양인데 대상이 사라진 것이라 ⑲ 로는 안 잡힌다.
--    `link` 에 FK 가 없어 DB 가 아무것도 안 막는다. 이 각도 말고는 볼 방법이 없다.
--    ⚠ 주문 링크(/orders/…)는 2026-09-10 실측 0건이라 안 센다 — **주문은 지우지 않기 때문**이다
--      (취소도 status 를 바꿀 뿐이다). 상품만 진짜로 사라진다(F-7 purge).
select 'notif 가 없는 상품을 가리킨다|'||count(*) from notification n
 where n.link like '/products/%'
   and not exists (select 1 from product p
                    where p.id = hextoraw(replace(regexp_substr(n.link,'[0-9a-f-]{36}$'),'-','')));
exit
SQL
)

if [ $? -ne 0 ]; then
  echo "⚠ DB 에 못 붙었거나 쿼리가 실패했다 — 판정 불가:"; echo "$OUT" | sed 's/^/    /'; exit 2
fi

# ⚠ 알려진 위반은 **없다**. 2026-09-02 실측: 열한 개 전부 0.
# 🔴 2026-09-10 에 ⑳ 을 더해 **스무 개**가 됐다 — 더하기 전에 V61 로 쌓인 55건을 치우고,
#    재발 경로 둘(운영 purge · 커밋하는 테스트)을 먼저 막았다. **순서가 그래야 0 에서 시작한다.**
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

# ⚠ 불변식을 다 읽었는가 — 덜 읽었으면 «성립» 이 아니라 «못 셌다» 다.
# 🔴 개수를 늘릴 때 이 숫자도 함께 고친다 — 안 고치면 새 줄이 조용히 «판정 불가» 를 만든다.
if [ "$SEEN" -ne 20 ]; then
  echo "  ⚠ 불변식 20개 중 ${SEEN}개만 읽혔다 — **판정 불가**."; exit 2
fi

if [ "$FAIL" -eq 1 ]; then
  echo "  → 새 위반이다. handoffs/2026-09-02-handoff.md §4 가 이 불변식들이 무엇인지 적어 뒀다."
  exit 1
fi
exit 0
