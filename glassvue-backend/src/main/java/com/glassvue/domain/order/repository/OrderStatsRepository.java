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
 *   <li><b>상태</b>: {@code PAID · SHIPPED · DELIVERED} 를 <b>명시적으로 열거</b>한다.
 *       {@code <> CANCELLED} 로 쓰면 나중에 추가되는 상태(환불 등)가 자동으로 매출에 섞인다
 *       ({@code existsPurchase} 와 같은 판단).
 *       <b>{@code paid_at} 유무로 거르지 않는다</b> — 결제 후 취소된 주문이 실제로 있고(2026-07-24 기준 1건),
 *       그건 환불이라 매출이 아니다.</li>
 *   <li><b>시각</b>: {@code created_at} 이 아니라 <b>{@code paid_at}</b>. 주문한 시점은 영영 결제되지 않을 수도
 *       있고, 매출은 돈이 들어온 시점에 잡는다.</li>
 *   <li><b>금액</b>: 상품매출({@code total_price - coupon_discount})과 배송비({@code shipping_fee})를
 *       <b>합치지 않는다</b>. 배송비는 그대로 택배비로 나가는 돈이라 상품매출과 섞으면
 *       장사가 잘되는지 알 수 없어진다(사용자 결정, 2026-07-24).</li>
 * </ul>
 */
public interface OrderStatsRepository extends Repository<Order, UUID> {

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
    @Query(value = """
            SELECT COUNT(*),
                   NVL(SUM(o.total_price - o.coupon_discount), 0),
                   NVL(SUM(o.shipping_fee), 0),
                   NVL(SUM(o.coupon_discount), 0)
              FROM orders o
             WHERE o.status IN (:statuses)
               AND o.paid_at >= :from
               AND o.paid_at <  :to
            """, nativeQuery = true)
    List<Object[]> summarize(@Param("statuses") Collection<String> statuses,
                             @Param("from") Instant from,
                             @Param("to") Instant to);

    /**
     * 일별 매출 — <b>KST 기준</b>으로 묶는다.
     *
     * <p>매출이 0인 날은 <b>행 자체가 없다.</b> 화면이 빈 날을 0으로 채워야 차트에 구멍이 안 생긴다
     * (호출부에서 채운다 — SQL 로 날짜를 생성하면 쿼리가 훨씬 복잡해진다).
     *
     * @return {@code [yyyy-MM-dd, 주문수, 상품매출, 배송비]} 여러 행, 날짜 오름차순
     */
    @Query(value = """
            SELECT TO_CHAR(o.paid_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD'),
                   COUNT(*),
                   NVL(SUM(o.total_price - o.coupon_discount), 0),
                   NVL(SUM(o.shipping_fee), 0)
              FROM orders o
             WHERE o.status IN (:statuses)
               AND o.paid_at >= :from
             GROUP BY TO_CHAR(o.paid_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD')
             ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> daily(@Param("statuses") Collection<String> statuses,
                         @Param("from") Instant from);

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
     * @return {@code [product_id(RAW), 상품명, 판매수량, 판매액]} 판매수량 내림차순
     */
    @Query(value = """
            SELECT oi.product_id,
                   MAX(oi.product_name) KEEP (DENSE_RANK LAST ORDER BY o.paid_at),
                   SUM(oi.quantity),
                   SUM(oi.line_total)
              FROM order_item oi
              JOIN orders o ON o.id = oi.order_id
             WHERE o.status IN (:statuses)
               AND o.paid_at >= :from
             GROUP BY oi.product_id
             ORDER BY SUM(oi.quantity) DESC, SUM(oi.line_total) DESC
             FETCH FIRST :limit ROWS ONLY
            """, nativeQuery = true)
    List<Object[]> topProducts(@Param("statuses") Collection<String> statuses,
                               @Param("from") Instant from,
                               @Param("limit") int limit);
}
