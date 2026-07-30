package com.glassvue.domain.wishlist.service.command;

import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.wishlist.entity.Wishlist;
import com.glassvue.domain.wishlist.repository.WishlistRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 찜 추가·해제. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WishlistCommandService {

    private final WishlistRepository wishlistRepository;
    private final ProductQueryService productQueryService;

    /**
     * 찜 추가.
     *
     * <p><b>이미 찜한 상품이면 조용히 성공</b>한다(멱등). 409 로 막을 수도 있지만 얻는 게 없다 —
     * 화면은 토글이라 사용자가 "중복 찜"을 의도할 수 없고, 중복 요청은 더블클릭·재시도 같은
     * <b>사고</b>다. 그때 에러를 띄우면 사용자는 원인을 알 수 없고 화면 상태만 어긋난다.
     * 원하는 최종 상태("찜되어 있음")는 어느 쪽이든 같으므로 그대로 성공으로 답한다.
     *
     * <p>동시 요청으로 검사와 INSERT 사이가 벌어져도 DB 의 UNIQUE(member_id, product_id) 가 막는다.
     */
    public void add(UUID memberId, UUID productId) {
        productQueryService.ensureExists(productId); // 없는 상품이면 PRODUCT-404
        if (wishlistRepository.existsByMemberIdAndProductId(memberId, productId)) {
            return;
        }
        wishlistRepository.save(Wishlist.of(memberId, productId));
        log.info("Wishlist added: member={} product={}", memberId, productId);
    }

    /**
     * 찜 해제.
     *
     * <p>추가와 대칭으로 <b>찜한 적 없어도 성공</b>한다. "지워달라"는 요청의 목적은 "없는 상태"이고
     * 이미 그렇다면 할 일이 없는 것이지 오류가 아니다. 상품이 이미 삭제됐어도 해제는 되어야 하므로
     * 여기서는 상품 존재를 확인하지 않는다(확인하면 삭제된 상품을 찜 목록에서 <b>영영 못 뺀다</b>).
     */
    public void remove(UUID memberId, UUID productId) {
        long deleted = wishlistRepository.deleteByMemberIdAndProductId(memberId, productId);
        if (deleted > 0) {
            log.info("Wishlist removed: member={} product={}", memberId, productId);
        }
    }

    /** 회원 삭제 정리(F-1) — 찜 전체 삭제. */
    public void deleteAllForMember(UUID memberId) {
        long deleted = wishlistRepository.deleteByMemberId(memberId);
        log.info("Wishlist deleted for member {}: {}", memberId, deleted);
    }
}
