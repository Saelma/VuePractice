package com.glassvue.domain.catalog.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 상품 기간 할인(타임세일) — <b>"이번 주말만 20%"</b> 를 사람이 앉아 있지 않아도 되게 만드는 자리
 * (2026-08-19, BACKLOG G-5).
 *
 * <p>그전까지 할인은 관리자가 {@code product.list_price} 를 손으로 넣고 <b>손으로 되돌리는</b> 방식이라
 * 시작과 끝이 사람의 기억에 달려 있었다. 여기서부터 <b>기간이 값</b>이 된다.
 *
 * <p>🔴 <b>정가와 세일은 다른 것이다.</b> {@code list_price}(B-7)는 «원래 이 값어치» 라는 <b>표시</b>고,
 * 이 엔티티는 «이 기간만 싸게 판다» 는 <b>사건</b>이다. 그래서 세일이 정가를 덮어쓰지 않는다 —
 * 덮어쓰면 세일이 끝났을 때 되돌릴 원본이 사라진다.
 *
 * <p>⚠ <b>상품당 행이 여럿</b>이다(컬럼이 아니라 테이블인 이유). 다음 세일을 미리 등록해 둘 수 있어야
 * 하고, 끝난 세일도 «지난주에 얼마에 팔았나» 로 남아야 한다.
 *
 * <p>🔴 <b>기간이 겹치지 않게 하는 것은 이 클래스가 아니라 앱의 책임이다</b> — Oracle 유니크로는
 * 기간 겹침을 막을 수 없다(G-8/V49 에서 겪은 그 자리). 등록·수정에서 막고 테스트가 못 박는다.
 * 그래도 뚫렸을 때를 위해 조회는 <b>여러 건을 받아</b> 할인율이 가장 높은 것을 쓴다.
 */
@Entity
@Getter
@Table(name = "product_discount")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductDiscount extends BaseTimeEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "product_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID productId;

    /** 할인율 %. DB CHECK 로 1~99 만 들어온다(0은 할인이 아니고 100은 공짜라 계산 경로가 갈린다). */
    @Column(nullable = false)
    private int rate;

    /** 세일 시작 시각 — <b>포함</b>이다. */
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    /**
     * 세일 종료 시각 — <b>배타</b>다. {@code 23:59:59} 로 두면 그 사이 {@code 23:59:59.5} 결제가
     * 원가로 나가는데 <b>초 미만은 눈에 안 보여 더 나쁘다</b>(B-26 의 날짜 경계와 같은 판단).
     */
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    private ProductDiscount(UUID productId, int rate, Instant startsAt, Instant endsAt) {
        this.productId = productId;
        this.rate = rate;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public static ProductDiscount of(UUID productId, int rate, Instant startsAt, Instant endsAt) {
        return new ProductDiscount(productId, rate, startsAt, endsAt);
    }

    public void update(int rate, Instant startsAt, Instant endsAt) {
        this.rate = rate;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    /** 이 시각에 이 할인이 유효한가. 시작은 포함, 종료는 배타. */
    public boolean isActiveAt(Instant at) {
        return !at.isBefore(startsAt) && at.isBefore(endsAt);
    }

    /** 아직 시작하지 않았나 — 화면이 「예고」와 「진행 중」을 가르는 값이다. */
    public boolean isUpcomingAt(Instant at) {
        return at.isBefore(startsAt);
    }

    /**
     * 할인가를 만든다 — <b>이 제품에서 세일가를 계산하는 유일한 자리다.</b>
     *
     * <p>🔴 인자는 <b>옵션 가격차까지 더한 값</b>이어야 한다. 즉
     * {@code (product.price + price_delta) × (100 - rate) / 100} 이지
     * {@code product.price × (100 - rate) / 100 + price_delta} 가 아니다 —
     * 후자면 "L +2000" 옵션만 할인이 덜 먹어 <b>옵션마다 체감 할인율이 달라진다.</b>
     *
     * <p>⚠ <b>반올림이 여기 한 자리에만 있는 것이 중요하다.</b> 목록 쿼리(정렬·가격필터)도 SQL 에서
     * 같은 식을 쓰는데, 반올림 규칙이 갈리면 <b>목록에 뜬 가격과 장바구니 가격이 1원 어긋난다</b> —
     * 눈에 잘 안 띄고 결제에서 터진다.
     *
     * <p>🔴 <b>일부러 정수 연산이다.</b> {@code Math.round(price * (100.0 - rate) / 100.0)} 로 쓰면
     * 계산이 double 을 거치는데, SQL 쪽은 Oracle NUMBER(십진 고정소수)로 계산한다 —
     * <b>두 반올림이 어느 값에서 갈리는지 알 수 없다.</b> {@code (x * (100 - rate) + 50) / 100} 은
     * 정수만으로 «0.5 올림» 을 만들고, 그것이 Oracle {@code ROUND(x, 0)} 과 <b>정확히 같다</b>
     * (가격이 음수가 아니므로 «절반은 0에서 먼 쪽» 과 «절반은 위로» 가 일치한다).
     */
    public long applyTo(long price) {
        return (price * (100L - rate) + 50L) / 100L;
    }
}
