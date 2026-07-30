package com.glassvue.domain.restock.service.command;

import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.restock.entity.RestockSubscription;
import com.glassvue.domain.restock.repository.RestockSubscriptionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재입고 알림 신청·취소 (B-9). 위시리스트 추가·해제와 같은 멱등 규약을 따른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RestockSubscriptionCommandService {

    private final RestockSubscriptionRepository subscriptionRepository;
    private final ProductQueryService productQueryService;

    /**
     * 재입고 알림 신청.
     *
     * <p><b>이미 신청했으면 조용히 성공</b>(멱등). 화면은 토글이라 중복 신청은 더블클릭·재시도 같은 사고이고,
     * 원하는 최종 상태("신청되어 있음")는 어느 쪽이든 같다. 검사와 INSERT 사이가 벌어져도
     * UNIQUE(member_id, product_id) 가 막는다. 없는 상품이면 PRODUCT-404.
     */
    public void subscribe(UUID memberId, UUID productId) {
        productQueryService.ensureExists(productId);
        if (subscriptionRepository.existsByMemberIdAndProductId(memberId, productId)) {
            return;
        }
        subscriptionRepository.save(RestockSubscription.of(memberId, productId));
        log.info("Restock subscribed: member={} product={}", memberId, productId);
    }

    /**
     * 신청 취소.
     *
     * <p>신청한 적 없어도 성공(멱등). 삭제된 상품도 취소할 수 있어야 하므로 상품 존재를 확인하지 않는다
     * (위시리스트 해제와 같은 이유).
     */
    public void unsubscribe(UUID memberId, UUID productId) {
        long deleted = subscriptionRepository.deleteByMemberIdAndProductId(memberId, productId);
        if (deleted > 0) {
            log.info("Restock unsubscribed: member={} product={}", memberId, productId);
        }
    }

    /** 회원 삭제 정리(F-1) — 재입고 구독 전체 삭제. */
    public void deleteAllForMember(UUID memberId) {
        long deleted = subscriptionRepository.deleteByMemberId(memberId);
        log.info("Restock subscriptions deleted for member {}: {}", memberId, deleted);
    }
}
