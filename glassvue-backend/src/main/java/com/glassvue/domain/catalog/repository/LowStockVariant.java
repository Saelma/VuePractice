package com.glassvue.domain.catalog.repository;

import java.util.UUID;

/**
 * 재고가 임계치 이하로 떨어진 옵션 한 줄 (2026-08-03, 백로그 B-16 대시보드).
 *
 * <p>{@link VariantStockSnapshot} 과 모양이 같지만 <b>쓰임이 다르다</b> — 저건 차감 <i>직후 한 건</i>을
 * 읽어 알림을 낼지 판단하는 것이고, 이건 <b>지금 부족한 것 전부</b>를 훑어 관리자에게 보여주는 것이다.
 * 합치면 "왜 여기에 벌크 UPDATE 우회 주석이 붙어 있지?" 가 되므로 따로 둔다.
 */
public record LowStockVariant(UUID productId, String productName, String variantName, long stock) {
}
