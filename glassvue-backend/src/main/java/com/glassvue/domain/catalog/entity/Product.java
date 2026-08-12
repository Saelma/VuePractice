package com.glassvue.domain.catalog.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import java.time.Instant;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Column(nullable = false, length = 200)
    private String name;

    /**
     * 목록 카드에 얹는 <b>한 줄 카피</b>(V33, 2026-07-29). null 이면 카드가 그 줄을 감춘다.
     *
     * <p>{@code description} 을 잘라 쓰지 않는다 — 설명은 문단이라 앞 N자를 자르면 문장이 끊긴다.
     * "카드에 보여줄 한 줄"은 상세 설명과 <b>목적이 다른 글</b>이다.
     *
     * <p>⚠ length 를 명시한다. 안 주면 기본 255 로 검증돼 {@code ddl-auto=validate} 가
     * DDL(100)과 어긋난다며 부팅을 막는다(V12 교훈 — admin_audit_log 에서 겪은 자리).
     */
    @Column(length = 100)
    private String tagline;

    @Lob
    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private long price; // 원 — **실제 판매가**. 장바구니·주문·배송비 무료 기준이 전부 이 값을 쓴다.

    /**
     * 정가(할인 전 가격). null 이면 <b>할인 없음</b>이다.
     *
     * <p>{@code price} 를 정가로 바꾸지 않은 이유: 그 값은 실제로 청구되는 금액이라
     * 의미를 바꾸면 합계·무료배송 기준 계산이 전부 어긋난다. 할인율은 두 값에서 계산하고 저장하지 않는다
     * (저장하면 가격을 바꿀 때 어긋날 여지가 생긴다).
     */
    @Column(name = "list_price")
    private Long listPrice;

    // 재고는 여기 없다 (2026-07-24, C-8). product.stock 컬럼은 아직 DB 에 있지만(운영 구 jar 가 매핑)
    // 신 코드는 재고를 product_variant 로만 다룬다 — 엔티티 매핑을 걷어내 "구조로" 못 쓰게 했다
    // (V18 의 expand/contract 와 같은 방식). 컬럼 DROP 은 이후 버전(구 jar 배포 후).
    // ddl-auto=validate 는 매핑되지 않은 여분 컬럼을 문제 삼지 않는다(V18 에서 실측).

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    // 이미지 묶음 참조(느슨한 UUID). 이미지 도메인(image_group)을 가리킨다.
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "image_group_id", columnDefinition = "RAW(16)")
    private UUID imageGroupId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", columnDefinition = "RAW(16)", nullable = false)
    private Category category;

    // 리뷰 집계 비정규화 — review 도메인이 ReviewRatingChangedEvent로 밀어넣는다(catalog는 review를 모른다).
    // 목록 조회에서 조인/추가쿼리 없이 읽으려는 것. 상품 생성/수정으로는 바뀌지 않는다.
    @Column(name = "avg_rating", nullable = false)
    private double avgRating;

    @Column(name = "review_count", nullable = false)
    private long reviewCount;

    // 판매량 비정규화 — order 도메인이 주문/취소/반품 이벤트로 밀어넣는다(catalog는 order를 모른다).
    // 목록의 "인기순" 정렬을 조인 없이 하려는 것(avg_rating 과 같은 방식, V25). 주문/취소로 바뀌고
    // 상품 생성/수정으로는 바뀌지 않는다. @Async best-effort 라 근사값이다 — 인기 랭킹이라 허용된다.
    @Column(name = "sold_count", nullable = false)
    private long soldCount;

    /**
     * 삭제 대기 시각 (2026-08-12, F-7). <b>NULL 이면 살아 있는 상품</b>이다.
     *
     * <p>🔴 <b>상태({@link ProductStatus})가 아니라 시각인 것이 요점이다.</b> 「숨김」은
     * «계속 팔 생각이 있다» 이고 삭제 대기는 «시한이 있다» 인데, <b>시한은 상태로 표현할 수 없다</b> —
     * 이 값 하나로 «지워졌나» 와 «언제까지 되돌릴 수 있나» 가 둘 다 나온다.
     *
     * <p>⚠ <b>이 필드를 직접 보지 말고 {@link #isDeleted()} 를 쓴다.</b> 조회 갈래마다
     * {@code getDeletedAt() != null} 을 손으로 적으면 <b>한 곳이 빠졌을 때 조용하다</b>
     * (2026-08-11 §13 ⓪ 이 그 모양을 일곱 번 보여줬다).
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** 삭제한 관리자 이름(스냅샷) — 복구 화면이 «누가 지웠나» 에 답한다. 주문의 `cancelled_by_name` 과 같은 자리. */
    @Column(name = "deleted_by_name", length = 50)
    private String deletedByName;

    @Builder
    private Product(String name, String tagline, String description, long price, Long listPrice,
                    ProductStatus status, UUID imageGroupId, Category category) {
        this.name = name;
        this.tagline = tagline;
        this.description = description;
        this.price = price;
        this.listPrice = listPrice;
        this.status = (status != null) ? status : ProductStatus.SELLING;
        this.imageGroupId = imageGroupId;
        this.category = category;
    }

    public void update(String name, String tagline, String description, long price, Long listPrice,
                       ProductStatus status, UUID imageGroupId, Category category) {
        this.name = name;
        this.tagline = tagline;
        this.description = description;
        this.price = price;
        this.listPrice = listPrice;
        this.status = status;
        this.imageGroupId = imageGroupId;
        this.category = category;
    }

    // ── 삭제 유예 (2026-08-12, F-7) ────────────────────────────

    /**
     * 🔴 <b>「지워졌나」의 판정은 여기 하나다.</b> 조회 갈래가 여럿이라(목록·검색·상세·장바구니·
     * 리뷰 목록·재고) 각자 {@code deletedAt != null} 을 적으면 <b>한 곳이 빠졌을 때 조용하다</b> —
     * 빠진 화면은 <b>이미 지운 상품을 계속 판다.</b>
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * 삭제 대기로 표시한다. <b>행을 지우지 않는다</b> — 유예가 지나면 배치가 진짜로 지운다.
     *
     * <p>⚠ <b>이미 대기 중이면 시각을 다시 쓰지 않는다.</b> 다시 누를 때마다 갱신되면
     * <b>유예가 영원히 안 끝난다</b>(누를 때마다 D-7 로 되돌아간다).
     */
    public void softDelete(String actorName) {
        if (deletedAt != null) {
            return;
        }
        this.deletedAt = Instant.now();
        this.deletedByName = actorName;
    }

    /**
     * 되살린다. ⚠ <b>{@code deletedByName} 도 함께 지운다</b> — 남겨 두면 살아 있는 상품에
     * «누가 지웠다» 가 붙어 다음 사람이 «지금 삭제 대기인가?» 로 읽는다.
     * (반품 재요청이 이전 거절 기록을 지우는 것과 같은 판단 — 2026-08-11 V47.)
     */
    public void restore() {
        this.deletedAt = null;
        this.deletedByName = null;
    }
}
