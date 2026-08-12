package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.entity.Product;
import java.time.Instant;
import java.util.UUID;

/**
 * 삭제 대기 상품 한 줄 — 관리자 복구 화면 (2026-08-12, BACKLOG F-7).
 *
 * <p>{@link ProductResponse} 를 재사용하지 않는다. 그쪽은 «이 상품을 파는 데 필요한 것» 을 담아
 * 옵션·이미지·별점·판매량까지 싣는데, 이 화면이 답할 질문은 <b>«무엇이 · 언제 사라지나 · 누가 지웠나»</b>
 * 셋뿐이다. ⚠ 재사용하면 줄마다 옵션·이미지 조회가 따라붙는다 —
 * {@code AdminReviewResponse} 가 이미지 안 싣는 것과 같은 판단.
 *
 * @param deletedAt   삭제 대기가 된 시각.
 * @param purgeAt     🔴 <b>언제 진짜로 사라지나</b> — 서버가 계산해서 준다. 화면이
 *                    {@code deletedAt + 7일} 을 직접 더하면 <b>유예 설정을 바꿨을 때 화면만 낡는다</b>
 *                    (혜택 문구를 서버가 주는 것과 같은 규칙).
 * @param deletedBy   삭제한 관리자 이름(스냅샷). 없을 수 있다 — 이 컬럼이 생기기 전에 지운 것은 없지만,
 *                    구 jar 가 만든 행이 섞일 여지를 남겨 둔다.
 */
public record DeletedProductResponse(
        UUID id,
        String name,
        String categoryName,
        Instant deletedAt,
        Instant purgeAt,
        String deletedBy
) {
    public static DeletedProductResponse from(Product p, Instant purgeAt) {
        return new DeletedProductResponse(
                p.getId(), p.getName(), p.getCategory().getName(),
                p.getDeletedAt(), purgeAt, p.getDeletedByName());
    }
}
