package com.glassvue.domain.restock.service.query;

import com.glassvue.domain.restock.repository.RestockSubscriptionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 재입고 신청 조회 (B-9). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestockSubscriptionQueryService {

    private final RestockSubscriptionRepository subscriptionRepository;

    /**
     * 내가 재입고 신청한 상품 id 목록 — 상품 상세에서 버튼 상태를 판단하는 데 쓴다.
     * 위시리스트의 myProductIds 와 같은 감각(상품 응답에 신청 여부를 넣지 않아 도메인 순환을 피한다).
     */
    public List<UUID> myProductIds(UUID memberId) {
        return subscriptionRepository.findProductIdsByMemberId(memberId);
    }
}
