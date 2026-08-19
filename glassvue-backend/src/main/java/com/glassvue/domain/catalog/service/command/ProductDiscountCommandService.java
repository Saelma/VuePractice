package com.glassvue.domain.catalog.service.command;

import com.glassvue.domain.catalog.dto.ProductDiscountRequest;
import com.glassvue.domain.catalog.entity.ProductDiscount;
import com.glassvue.domain.catalog.repository.ProductDiscountRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기간 할인 조작(관리자) — <b>"이번 주말만 20%"</b> (2026-08-19, BACKLOG G-5).
 *
 * <p>🔴 <b>이 클래스가 지키는 규칙은 하나다: 한 상품에 같은 순간 유효한 할인이 둘일 수 없다.</b>
 * Oracle 유니크로는 기간 겹침을 막을 수 없어(V52 주석) <b>앱이 유일한 방어</b>다 —
 * G-8(이벤트 쿠폰 발급 창)에서 겪은 그 자리와 같다. 그래서 이 가드는 테스트로 못 박는다.
 *
 * <p>⚠ <b>DB 도 뒤에서 한 번 더 막지만 뜻이 다르다.</b> {@code ck_product_discount_period} 는
 * «끝이 시작보다 뒤» 만 보고 겹침은 못 본다. 그리고 DB 제약에 걸리면 <b>500</b> 이 나가므로,
 * 관리자에게 이유를 말해 주는 것은 여기서 던지는 4xx 다.
 *
 * <p>⚠ <b>목록 캐시를 비운다.</b> 할인을 걸면 가격이 바뀌는데 {@code products:list} 는 60초를
 * 들고 있어서, 안 비우면 <b>관리자가 세일을 걸고 새로고침해도 최대 1분간 원가가 보인다</b>
 * (자기가 방금 한 일이 안 보이면 한 번 더 건다). 🔴 <b>세일이 저절로 시작·종료하는 순간은
 * 이 갈래가 아니다</b> — 그때는 아무 조작도 없으므로 TTL 60초가 지나야 반영된다.
 * 그 60초를 감수하기로 했다(evict 배선을 넣을 값어치가 없다, 2026-08-19).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductDiscountCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 신규 등록이 «자기 자신을 빼고» 겹침을 볼 때 쓰는 자리표시. 아직 id 가 없어서 필요하다 —
     * {@code null} 을 넘기면 JPQL 의 {@code d.id <> :excludeId} 가 <b>항상 거짓</b>이 돼
     * <b>겹침 검사가 통째로 무력화된다</b>(NULL 비교는 UNKNOWN 이다).
     */
    private static final UUID NO_EXCLUDE = new UUID(0L, 0L);

    private final ProductDiscountRepository discountRepository;
    private final ProductRepository productRepository;

    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public UUID create(UUID productId, ProductDiscountRequest req) {
        ensureProductAlive(productId);
        Instant startsAt = startBoundary(req);
        Instant endsAt = endBoundary(req);
        validatePeriod(productId, startsAt, endsAt, NO_EXCLUDE);
        return discountRepository.save(
                ProductDiscount.of(productId, req.rate(), startsAt, endsAt)).getId();
    }

    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void update(UUID productId, UUID discountId, ProductDiscountRequest req) {
        ProductDiscount discount = findOwned(productId, discountId);
        Instant startsAt = startBoundary(req);
        Instant endsAt = endBoundary(req);
        // ⚠ 자기 자신은 겹침에서 뺀다 — 안 그러면 기간을 그대로 두고 **할인율만 고치는 것이 불가능**하다.
        validatePeriod(productId, startsAt, endsAt, discountId);
        discount.update(req.rate(), startsAt, endsAt);
    }

    /**
     * 할인을 지운다.
     *
     * <p>⚠ <b>진행 중인 것도 지울 수 있다</b> — 세일을 잘못 걸었을 때 되돌릴 방법이 이것뿐이다.
     * 지우면 그 순간부터 원가로 돌아간다. 🔴 <b>이미 팔린 주문의 금액은 안 변한다</b>(B-7 스냅샷) —
     * 그 토대가 있어서 이 조작이 안전하다.
     */
    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void delete(UUID productId, UUID discountId) {
        discountRepository.delete(findOwned(productId, discountId));
    }

    /**
     * 시작 경계 — 시작일 00:00 KST(<b>포함</b>).
     *
     * <p>🔴 <b>경계를 서버가 만드는 것이 B-26 에서 세운 규약이다.</b> 화면이 {@code Instant} 를 만들어
     * 보내면 KST 경계가 두 곳에서 계산되고, <b>하루가 어긋나도 화면은 멀쩡해 보인다.</b>
     */
    private Instant startBoundary(ProductDiscountRequest req) {
        return req.startDate().atStartOfDay(KST).toInstant();
    }

    /**
     * 종료 경계 — 종료일 <b>다음 날</b> 00:00 KST(<b>배타</b>).
     *
     * <p>⚠ 관리자가 적은 종료일은 <b>포함</b>이다("8/24 까지" 면 24일이 끝날 때까지). 그래서 하루를
     * 더해 배타 경계로 바꾼다 — 그 변환이 여기 한 곳에 있어야 관리자가 «하루 빼서 적는» 일을 안 한다.
     */
    private Instant endBoundary(ProductDiscountRequest req) {
        return req.endDate().plusDays(1).atStartOfDay(KST).toInstant();
    }

    private void validatePeriod(UUID productId, Instant startsAt, Instant endsAt, UUID excludeId) {
        if (!endsAt.isAfter(startsAt)) {
            // 종료일이 시작일보다 앞이면 여기 온다(같은 날은 하루짜리 세일이라 정상이다 —
            // endBoundary 가 하루를 더하므로 startsAt < endsAt 가 성립한다).
            throw new BusinessException(ErrorCode.DISCOUNT_PERIOD_INVALID);
        }
        List<ProductDiscount> overlapping =
                discountRepository.findOverlapping(productId, startsAt, endsAt, excludeId);
        if (!overlapping.isEmpty()) {
            // ⚠ 무엇과 겹쳤는지 로그에 남긴다 — 화면은 «겹칩니다» 만 말하고, 관리자는 목록에서
            //    어느 줄인지 눈으로 찾아야 한다. 지원 요청이 오면 이 줄이 답이다.
            ProductDiscount first = overlapping.get(0);
            log.info("[상품] 할인 기간 겹침으로 거절 — productId={} 요청={}~{} 기존={}%({}~{}) 외 {}건",
                    productId, startsAt, endsAt, first.getRate(), first.getStartsAt(), first.getEndsAt(),
                    overlapping.size() - 1);
            throw new BusinessException(ErrorCode.DISCOUNT_PERIOD_OVERLAP);
        }
    }

    /**
     * ⚠ <b>삭제 대기 상품에는 할인을 못 건다</b>(2026-08-12 F-7 의 «새 리뷰·문의를 막는다» 와 같은 판단).
     * 곧 사라질 상품에 세일을 걸어 봐야 목록에 안 나온다 — 관리자만 «걸었는데 아무 일도 안 난다» 를 본다.
     */
    private void ensureProductAlive(UUID productId) {
        if (!productRepository.existsAliveById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    /**
     * ⚠ <b>상품 소속까지 확인한다.</b> id 만으로 찾으면 «다른 상품의 할인» 을 이 상품 경로로 지울 수 있고,
     * 그러면 관리자는 <b>자기가 안 건드린 상품의 세일이 사라진 것</b>을 나중에야 본다.
     */
    private ProductDiscount findOwned(UUID productId, UUID discountId) {
        return discountRepository.findById(discountId)
                .filter(d -> d.getProductId().equals(productId))
                .orElseThrow(() -> new BusinessException(ErrorCode.DISCOUNT_NOT_FOUND));
    }
}
