package com.glassvue.domain.order.repository;

import com.glassvue.domain.order.entity.Order;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 매출 집계 (2026-07-24, 백로그 C-11).
 *
 * <p>{@link OrderRepository} 와 나눠 둔 이유는 <b>성격이 다르기</b> 때문이다 — 저쪽은 주문 한 건을
 * 다루는 CRUD·검색이고 여기는 <b>전부 집계</b>다. 셋 다 "무엇을 매출로 보는가"라는 같은 정의를
 * 공유하므로 한자리에 모아 두면 정의가 어긋나는 걸 눈으로 잡을 수 있다.
 *
 * <h3>왜 전부 네이티브 쿼리인가</h3>
 * <p>일별 집계가 <b>KST 기준</b>이어야 하는데 JPQL 에는 타임존 변환이 없다.
 * {@code paid_at} 은 UTC(TIMESTAMP WITH TIME ZONE)로 저장돼 있어서 그냥 자르면
 * <b>한국 시간 00:00~09:00 결제가 전날로 찍힌다</b> — V15 주문번호에서 똑같이 걸린 함정이다.
 * 요약·상품별은 JPQL 로도 되지만, 세 쿼리가 같은 "매출 대상" 정의를 쓰는지 대조하기 쉽도록
 * <b>같은 언어로 통일</b>했다.
 *
 * <h3>무엇을 매출로 보는가</h3>
 * <ul>
 *   <li><b>상태</b>: 판정은 {@code OrderStatus.isRevenue()} <b>한 곳</b>에 있다 —
 *       <b>여기에 목록을 옮겨 적지 않는다.</b> ⚠ 2026-08-11 이전에는 {@code PAID·SHIPPED·DELIVERED} 를
 *       이 주석과 호출부에 <b>두 번</b> 적어 뒀는데, {@code RETURN_REQUESTED} 가 생겼을 때
 *       <b>둘 다 안 자랐다</b>(08-10 §16-4 8번). 복사본이 있으면 그 복사본이 낡는다.
 *       <b>{@code paid_at} 유무로 거르지 않는다</b> — 결제 후 취소된 주문이 실제로 있고(2026-07-24 기준 1건),
 *       그건 환불이라 매출이 아니다.</li>
 *   <li><b>시각</b>: {@code created_at} 이 아니라 <b>{@code paid_at}</b>. 주문한 시점은 영영 결제되지 않을 수도
 *       있고, 매출은 돈이 들어온 시점에 잡는다.</li>
 *   <li>🔴 <b>금액</b>: 판정은 {@link #ITEM_SALES} <b>한 곳</b>에 있다 — <b>여기에도, 테스트에도
 *       옮겨 적지 않는다.</b> 상품매출은 원본이 아니라 <b>«남은 것»</b> 이고, 취소분과 반품분을 둘 다 뺀다.
 *       ⚠ 안 빼면 <b>부분 취소·부분 반품을 해도 매출이 그대로</b>다: 부분 취소된 주문은 {@code PAID} 로,
 *       부분 반품된 주문은 <b>{@code DELIVERED} 로 되돌아가</b> 둘 다 매출 상태에 남기 때문이다.
 *       전량이 빠져야 {@code CANCELLED}·{@code RETURNED} 로 떨어져 상태 필터에서 빠진다.
 *       ⚠ 상태 목록에서 겪은 사고가 <b>금액 식에서 되풀이됐다</b>(2026-08-25, §I-1) —
 *       <b>복사본이 있으면 그 복사본이 낡는다.</b></li>
 *   <li><b>금액</b>: 상품매출({@code total_price - coupon_discount})과 배송비({@code shipping_fee})를
 *       <b>합치지 않는다</b>. 배송비는 그대로 택배비로 나가는 돈이라 상품매출과 섞으면
 *       장사가 잘되는지 알 수 없어진다(사용자 결정, 2026-07-24).</li>
 * </ul>
 */
public interface OrderStatsRepository extends Repository<Order, UUID> {

    /* ═══════════ 🔴 매출의 «금액» 정의 — 여기 한 곳에만 적는다 (2026-08-25, BACKLOG §I-1) ═══════════
     *
     * ⚠ **상태 목록을 `OrderStatus.isRevenue()` 한 곳에 둔 것과 같은 이유**다. 2026-08-11 에 상태를
     *   여러 곳에 적어 뒀다가 «다 안 자란» 사고가 났고, 그때는 **상태만** 옮겼다 —
     *   **금액 식은 계속 손으로 베낀 채였다.**
     *
     * 🔴 **그래서 같은 사고가 금액 식에서 되풀이됐다**: 08-24 가 부분 취소분을 빼며 이 식을 손으로
     *   넣었고(쿼리 둘 + 클래스 주석 + 통합 테스트 = **사본 넷**), 08-25 가 부분 반품(`returned_*`)을
     *   만들면서 **그 넷을 안 열었다.** 운영 매출이 **56,000원 부풀려진 채** 돌았다.
     *   ⚠ 더 나쁜 것: 테스트 사본도 **같이** 틀려서 **아무것도 안 빨개졌다.**
     *   WA §1-2-1: *「손으로 열거하는 코드를 손으로 열거하는 테스트로 지키면 둘이 같이 어긋난다」*.
     *
     * → **상수로 뽑아 쿼리도 테스트도 «읽어 가게» 한다.** `@Query` 값은 컴파일 상수 식이어야 하므로
     *   `static final String` 연결로만 조립한다(런타임 조립 불가 — 그래서 텍스트 블록을 포기했다).
     * ⚠ 🔴 **이 상수가 지키는 것은 «식이 옳은가» 가 아니라 «사본이 안 갈리는가» 다.**
     *   식이 옳은지는 **값으로 보는 테스트**가 지킨다(`AdminSalesStatsIntegrationTest` 의 부분 반품 절).
     */

    /** 아직 살아 있는 상품합계 — 취소분과 반품분을 <b>둘 다</b> 뺀다. */
    String REMAINING_ITEMS = "(o.total_price - o.cancelled_items_total - o.returned_items_total)";

    /** 아직 걸려 있는 쿠폰 할인 — 회수된 몫을 뺀다. */
    String REMAINING_DISCOUNT =
            "(o.coupon_discount - o.cancelled_coupon_discount - o.returned_coupon_discount)";

    /**
     * 🔴 <b>상품매출</b> = 남은 상품합계 − 남은 쿠폰할인.
     *
     * <p>⚠ <b>배송비는 안 섞는다</b> — 그대로 택배비로 나가는 돈이라 합치면 «얼마 팔았나» 를 알 수
     * 없어진다(2026-07-24 결정). ⚠ <b>빼지도 않는다</b> — 부분 취소로 안 움직이고(G-4 결정 2)
     * 부분 반품도 안 돌려준다(G-10 결정 3).
     */
    String ITEM_SALES = REMAINING_ITEMS + " - " + REMAINING_DISCOUNT;

    /* ─────────── 품목 단위의 «남은 것» — 상품별 TOP 이 쓴다 (2026-08-26, BACKLOG §I-5) ───────────
     *
     * ⚠ 위 셋은 <b>주문(o.)</b> 단위이고 아래 둘은 <b>품목(oi.)</b> 단위다. 상품별 TOP 은
     *   {@code order_item} 을 묶으므로 주문 합계로는 «어느 상품이» 를 못 가른다.
     * 🔴 <b>같은 식이 엔티티에도 있다</b> — {@code OrderItem.remainingQuantity()}·{@code remainingAmount()}.
     *   SQL 에서 그 메서드를 부를 수 없어 <b>사본이 하나 생기는 것을 피할 수 없다.</b>
     *   그래서 이름을 <b>메서드와 같게</b> 두고, 어긋나면 잡히도록 값으로 보는 테스트를 붙였다
     *   ({@code AdminSalesStatsIntegrationTest} 의 TOP 절). ⚠ 셋 중 하나를 고치면 나머지 둘을 연다.
     */

    /** 아직 살아 있는 품목 수량 — {@code OrderItem.remainingQuantity()} 와 같은 식. */
    String REMAINING_QUANTITY = "(oi.quantity - oi.cancelled_quantity - oi.returned_quantity)";

    /**
     * 아직 살아 있는 품목 금액 = <b>단가 × 남은 수량</b> ({@code OrderItem.remainingAmount()} 와 같은 식).
     *
     * <p>⚠ <b>{@code line_total} 을 깎지 않는다</b> — 그건 «몇 개를 얼마에 샀나» 라는 주문 시점
     * 스냅샷이라 안 줄어든다({@code OrderItem.cancelledQuantity} 주석과 같은 판단).
     */
    String REMAINING_AMOUNT = "(oi.price * " + REMAINING_QUANTITY + ")";

    /**
     * 기간 요약 — 주문 수 · 상품매출 · 배송비 · 할인액.
     *
     * <p>기간 경계는 <b>Java 가 KST 로 계산해</b> {@code Instant} 로 넘긴다. SQL 에서 타임존을 다루는 건
     * 일별 그룹핑에만 필요하고, 경계 계산까지 SQL 에 넣으면 읽기 어려워진다.
     * {@code SUM} 은 대상이 0건이면 NULL 이므로 {@code NVL} 로 0을 보장한다 — 호출부가 null 을
     * 신경 쓰지 않게.
     *
     * <p>⚠ 반환 타입이 {@code List<Object[]>} 인 이유: 집계라 <b>항상 한 행</b>이지만 {@code Object[]} 로
     * 선언하면 Spring Data 가 "한 행의 여러 컬럼"과 "여러 행"을 구분하지 못해 <b>조용히 0 을 돌려준다</b>
     * (2026-07-24 에 실제로 겪었다 — SQL 은 6건/60,000 을 내는데 API 는 0 이었고, 통합테스트의 델타
     * 비교가 0-0 으로 통과해 하마터면 안 보일 뻔했다). 호출부가 첫 행을 꺼낸다.
     *
     * @return {@code [[주문수, 상품매출, 배송비, 할인액]]} — 항상 한 행
     */
    @Query(value = "SELECT COUNT(*),"
            + " NVL(SUM(" + ITEM_SALES + "), 0),"
            + " NVL(SUM(o.shipping_fee), 0),"
            + " NVL(SUM(" + REMAINING_DISCOUNT + "), 0)"
            + " FROM orders o"
            + " WHERE o.status IN (:statuses)"
            + "   AND o.paid_at >= :from"
            + "   AND o.paid_at <  :to",
            nativeQuery = true)
    List<Object[]> summarize(@Param("statuses") Collection<String> statuses,
                             @Param("from") Instant from,
                             @Param("to") Instant to);

    /**
     * 일별 매출 — <b>KST 기준</b>으로 묶는다.
     *
     * <p>매출이 0인 날은 <b>행 자체가 없다.</b> 화면이 빈 날을 0으로 채워야 차트에 구멍이 안 생긴다
     * (호출부에서 채운다 — SQL 로 날짜를 생성하면 쿼리가 훨씬 복잡해진다).
     *
     * <p>⚠ <b>{@code to} 는 배타적</b>이다({@code < :to}) — 세 쿼리가 <b>같은 경계 규약</b>을 쓴다.
     * 한쪽만 포함으로 두면 마지막 날 매출이 요약과 추이에서 갈린다(B-26, 2026-08-13).
     *
     * @return {@code [yyyy-MM-dd, 주문수, 상품매출, 배송비]} 여러 행, 날짜 오름차순
     */
    @Query(value = "SELECT TO_CHAR(o.paid_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD'),"
            + " COUNT(*),"
            + " NVL(SUM(" + ITEM_SALES + "), 0),"
            + " NVL(SUM(o.shipping_fee), 0)"
            + " FROM orders o"
            + " WHERE o.status IN (:statuses)"
            + "   AND o.paid_at >= :from"
            + "   AND o.paid_at <  :to"
            + " GROUP BY TO_CHAR(o.paid_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD')"
            + " ORDER BY 1",
            nativeQuery = true)
    List<Object[]> daily(@Param("statuses") Collection<String> statuses,
                         @Param("from") Instant from,
                         @Param("to") Instant to);

    /**
     * 상품별 판매량 TOP N.
     *
     * <p><b>상품명은 주문 시점 스냅샷</b>({@code order_item.product_name})이라 상품 이름이 바뀌면
     * 같은 상품이 두 이름으로 갈린다. 그래서 {@code product_id} 로 묶고 이름은
     * <b>가장 최근 결제 건의 것</b>을 쓴다({@code KEEP DENSE_RANK LAST}).
     * catalog 를 조회하지 않는 이유이기도 하다 — 스냅샷만으로 충분해서 도메인 의존이 안 생긴다.
     *
     * <p>⚠ 여기 매출은 <b>쿠폰 할인 전</b>이다. 쿠폰은 <b>주문 단위</b>로 붙어서 어느 상품이 얼마를
     * 깎았는지 나눌 근거가 없다. 안분하면 그럴듯하지만 <b>지어낸 숫자</b>가 된다.
     * 그래서 요약의 상품매출(할인 후)과 이 값의 합계는 <b>일부러 다르다</b> — 화면에도 그렇게 적는다.
     *
     * <p>🔴 <b>수량·금액은 «남은 것» 이다</b> (2026-08-26, BACKLOG §I-5) — {@link #REMAINING_QUANTITY}·
     * {@link #REMAINING_AMOUNT}. ⚠ 08-24 가 부분 취소분을 빼며 {@code summarize}·{@code daily} 만 고쳤고
     * 08-25 가 부분 반품분을 빼며 <b>같은 파일의 세 번째 쿼리를 안 열었다</b> — 그래서 여기만
     * <b>원본 스냅샷을 합산한 채</b> 남아 있었다. 상태 필터는 <b>전량</b> 취소·반품만 걸러내므로
     * (부분은 {@code PAID}·{@code DELIVERED} 로 남는다) <b>부분으로 빠진 몫이 그대로 TOP 에 잡혔다.</b>
     * 🔴 그 사이 {@code product.sold_count}(상점 인기순)는 이벤트로 줄고 있어서
     * <b>관리자 TOP 과 상점 인기순이 서로 다른 말을 했다.</b>
     * ⚠ 🔴 <b>그렇다고 이걸 고치면 둘이 «같은 값» 이 되는 것은 아니다</b>(2026-08-26 배포 검증 실측) —
     * <b>세는 창이 다르다</b>(여기는 고른 기간 · 매출 상태, 저쪽은 <b>전체 기간 · 전체 상태</b> 누적).
     * <b>고쳐진 것은 «TOP 이 원본을 세던 것» 이지 «둘의 값이 일치하는 것» 이 아니다.</b>
     *
     * <p>⚠ <b>2026-09-02 정정</b>: 여기 «게다가 {@code sold_count} 에 08-25 이전 구멍의 이력이 남아
     * 있다» 고 적혀 있었다(근거로 «지바 TOP 5 / {@code sold_count} 12» 를 들었다).
     * 🔴 <b>그 근거는 잔재의 증거가 아니라 «창이 다르다» 의 증거였다</b> — 바로 위 문단이 이미
     * 그렇게 말하고 있는데 같은 실측을 두 결론에 썼다. 전 상품을 세어 보니
     * {@code sold_count} 는 <b>일곱 개 모두 정확했다</b>(위반 0 · 백로그 §K-1).
     * ⚠ 대조식은 <b>주문 상태를 봐야 한다</b> — 전량 취소·반품은 {@code order_item} 의 수량 칸을
     * 안 건드리므로(그 칸은 «부분» 전용) 품목만 빼면 전량 취소분이 «팔린 것» 으로 남는다.
     * 점검은 {@code scripts/check-money-invariants.sh} ⑧ 에 있다.
     *
     * <p>⚠ <b>남은 수량이 0 인 상품은 행 자체가 없다</b>({@code HAVING}). 안 걸러내면 전량이 빠진
     * 상품이 <b>0개·0원으로 TOP 자리를 차지</b>한다 — 매출 0인 날에 행을 안 내는 {@code daily} 와 같은 판단이고,
     * 「이 기간에 안 팔렸다」를 「0개 팔렸다」로 보여 주지 않기 위해서다.
     *
     * @return {@code [product_id(RAW), 상품명, 남은 수량, 남은 금액]} 수량 내림차순
     */
    @Query(value = "SELECT oi.product_id,"
            + " MAX(oi.product_name) KEEP (DENSE_RANK LAST ORDER BY o.paid_at),"
            + " SUM(" + REMAINING_QUANTITY + "),"
            + " SUM(" + REMAINING_AMOUNT + ")"
            + " FROM order_item oi"
            + " JOIN orders o ON o.id = oi.order_id"
            + " WHERE o.status IN (:statuses)"
            + "   AND o.paid_at >= :from"
            + "   AND o.paid_at <  :to"
            + " GROUP BY oi.product_id"
            + " HAVING SUM(" + REMAINING_QUANTITY + ") > 0"
            + " ORDER BY SUM(" + REMAINING_QUANTITY + ") DESC, SUM(" + REMAINING_AMOUNT + ") DESC"
            + " FETCH FIRST :limit ROWS ONLY",
            nativeQuery = true)
    List<Object[]> topProducts(@Param("statuses") Collection<String> statuses,
                               @Param("from") Instant from,
                               @Param("to") Instant to,
                               @Param("limit") int limit);
}
