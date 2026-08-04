package com.glassvue.domain.catalog.service.query;

import com.glassvue.domain.catalog.dto.StockHistoryResponse;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.repository.StockHistoryRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 변동 이력 조회(관리자) — 2026-08-04, 백로그 B-19.
 *
 * <p>권한은 경로({@code /api/admin/**})가 건다(WA §2-4).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockHistoryQueryService {

    private final StockHistoryRepository stockHistoryRepository;
    private final ProductRepository productRepository;

    /**
     * 이 상품의 재고 이력(최신순).
     *
     * <p>⚠ 상품 존재를 <b>먼저 확인</b>한다 — 없는 상품 id 로 물으면 빈 목록이 아니라 404 여야 한다.
     * 빈 목록으로 답하면 "이력이 아직 없다"(정상)와 "상품이 없다"(오타)를 화면이 구분할 수 없다.
     */
    public PageResponse<StockHistoryResponse> byProduct(UUID productId, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return PageResponse.from(stockHistoryRepository
                .findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(StockHistoryResponse::from));
    }
}
