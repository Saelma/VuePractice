package com.glassvue.domain.catalog.service.command;

import com.glassvue.domain.catalog.config.CatalogProperties;
import com.glassvue.domain.catalog.dto.ProductCreateRequest;
import com.glassvue.domain.catalog.dto.ProductUpdateRequest;
import com.glassvue.domain.catalog.dto.VariantRequest;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductVariant;
import com.glassvue.domain.catalog.entity.StockChangeReason;
import com.glassvue.domain.catalog.entity.StockHistory;
import com.glassvue.domain.catalog.event.StockReplenishedEvent;
import com.glassvue.domain.catalog.event.StockRunningLowEvent;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.repository.ProductVariantRepository;
import com.glassvue.domain.catalog.repository.StockHistoryRepository;
import com.glassvue.domain.catalog.repository.VariantStockSnapshot;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 조작(관리자). 목록 캐시는 변경 시 무효화.
 *
 * <p>2026-07-24(C-8): 재고가 옵션(variant)으로 내려갔다. 상품을 만들 때 <b>옵션도 함께</b> 만들고,
 * 재고 차감·복원은 상품이 아니라 <b>옵션 단위</b>로 한다.
 *
 * <p>2026-08-04(B-19): <b>재고를 바꾸는 모든 경로가 {@link StockHistory} 를 남긴다.</b> 이 클래스가
 * 유일한 관문이라 여기서만 기록하면 빠지는 경로가 없다 — 주문·취소·반품은
 * {@code decreaseStock}/{@code increaseStock} 로, 관리자 등록·편집은 {@code create}/{@code update} 로 들어온다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final ImageService imageService;
    private final CatalogProperties catalogProperties;
    private final ApplicationEventPublisher eventPublisher;

    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public UUID create(ProductCreateRequest req, AuthUser actor) {
        Category category = findCategory(req.categoryId());
        UUID imageGroupId = imageService.createGroup(req.imageIds());
        Product product = Product.builder()
                .name(req.name())
                .tagline(req.tagline())
                .description(req.description())
                .price(req.price())
                .listPrice(req.listPrice())
                .status(req.status())
                .imageGroupId(imageGroupId)
                .category(category)
                .build();
        Product saved = productRepository.save(product);
        List<ProductVariant> variants = saveVariants(saved.getId(), req.variants());

        // 초기 재고를 원장의 첫 줄로 남긴다 — 안 남기면 SUM(quantity) 이 항상 초기재고만큼 모자라
        // "합계 = 현재 재고" 라는 원장의 성질이 깨진다(B-19).
        for (ProductVariant v : variants) {
            recordAdmin(saved.getId(), v, StockChangeReason.ADMIN_CREATE, v.getStock(), v.getStock(), actor);
        }
        log.info("Product created: {} ({} variants)", saved.getId(), variants.size());
        return saved.getId();
    }

    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void update(UUID id, ProductUpdateRequest req, AuthUser actor) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        Category category = findCategory(req.categoryId());
        UUID oldGroupId = product.getImageGroupId();
        UUID imageGroupId = imageService.createGroup(req.imageIds());
        long stockBefore = variantRepository.sumStockByProduct(id); // 옵션 교체 전 총재고(재입고 판단용)
        product.update(req.name(), req.tagline(), req.description(), req.price(), req.listPrice(),
                req.status(), imageGroupId, category);

        // 옵션은 통째로 교체한다(delete-all + insert). 관리 화면이 옵션 전체를 다시 보내므로
        // 부분 갱신(id 매칭·삭제 판정)보다 통째 교체가 단순하고 어긋날 여지가 없다.
        // ⚠ 이미 주문된 옵션을 지워도 order_item 은 스냅샷(variant_name)을 갖고 있어 과거 주문 표시는 멀쩡하다.
        //    재고 복원 대상 variant_id 는 사라질 수 있지만 increaseStock 이 0행이면 조용히 무시한다.
        // ⚠ 재고 이력도 같은 이유로 variant_id 가 아니라 **옵션명**으로 잇는다(B-19, StockHistory 참조) —
        //    이 교체가 곧 "모든 옵션의 id 가 바뀌는" 지점이다.
        List<ProductVariant> old = variantRepository.findByProductIdOrderBySortOrderAscCreatedAtAsc(id);
        Map<String, Long> stockByName = stockByName(old);
        variantRepository.deleteAll(old);
        variantRepository.flush(); // 삭제를 먼저 DB 에 보내 새 옵션과 섞이지 않게
        List<ProductVariant> saved = saveVariants(id, req.variants());

        recordEdit(id, stockByName, saved, actor);

        imageService.deleteGroup(oldGroupId);

        // 관리자 재고 편집도 재입고 경로다 — 실제 이커머스에서 품절이 풀리는 주된 경로.
        // 옵션을 통째로 교체하므로 옵션이 아니라 상품 총재고 0→양수로 판단한다. sumStockByProduct 는
        // JPQL 이라 위 save 들을 flush 한 뒤의 값을 본다(새 옵션 반영).
        publishIfReplenished(id, stockBefore, product.getName());
    }

    /**
     * 상품 삭제 — 🔴 <b>표시만 한다(2026-08-12, F-7).</b> 행은 그대로 있고
     * {@code deleted_at} 이 찍히며, <b>유예가 지나면 {@link ProductPurgeScheduler} 가 진짜로 지운다.</b>
     *
     * <p><b>왜 바꿨나</b>: 전에는 여기서 바로 행을 지웠고 FK CASCADE 로 <b>옵션·재고 이력까지</b>
     * 함께 사라졌다(실측 2026-08-12: 상품 6 · 옵션 6 · 재고이력 23). 취소 API 는 없었다 —
     * <b>오조작의 대가가 그 셋 전부인데 방어는 확인 대화 하나뿐</b>이었다.
     *
     * <p>⚠ <b>이미지 그룹도 여기서 안 지운다.</b> 지우면 복구해도 <b>사진이 안 돌아온다</b> —
     * 되돌릴 수 있게 만드는 것이 이 변경의 목적인데 그 절반을 스스로 깨는 셈이다.
     * 이미지 삭제는 진짜로 지우는 시점(배치)으로 옮겼다.
     *
     * <p>⚠ <b>멱등이다</b> — 이미 대기 중인 상품을 또 지워도 시각이 갱신되지 않는다
     * ({@link Product#softDelete}). 갱신되면 <b>누를 때마다 유예가 처음으로 되돌아가</b> 영영 안 지워진다.
     */
    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void delete(UUID id, String actorName) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.softDelete(actorName);
        log.info("[상품] 삭제 대기 — id={} name={} by={}", id, product.getName(), actorName);
    }

    /**
     * 삭제 대기를 되돌린다 (2026-08-12, F-7).
     *
     * <p>⚠ <b>대기 중이 아닌 상품에 부르면 아무 일도 없다</b>(멱등). 에러로 만들지 않는 이유는
     * 복구 화면에서 두 번 눌렀을 때 <b>«실패» 로 보이면 안 되기 때문</b>이다 — 원하는 상태(살아 있음)는
     * 이미 이뤄져 있다(반품 숨김 해제가 멱등인 것과 같은 판단).
     */
    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void restore(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.restore();
        log.info("[상품] 삭제 대기 해제 — id={} name={}", id, product.getName());
    }

    /**
     * 🔴 <b>진짜로 지운다</b> — 유예가 지난 상품만, 배치가 부른다 (2026-08-12, F-7).
     *
     * <p>여기서부터가 <b>되돌릴 수 없는 구간</b>이다. FK ON DELETE CASCADE 로 옵션(V22)·
     * 재고 이력(V39)이 함께 지워지고 이미지 그룹도 지운다 — 재고 이력은 상품에 종속된 기록이라
     * 상품이 없으면 볼 화면도 없다(감사 로그와 다르다).
     *
     * <p>⚠ <b>{@code public} 이지만 화면에서 부르지 않는다.</b> 부르는 곳은
     * {@link ProductPurgeScheduler} 하나이고, <b>컨트롤러에 이 경로를 열지 않는다</b> —
     * 열면 유예가 «건너뛸 수 있는 것» 이 되어 이 기능이 무의미해진다.
     */
    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void purge(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        UUID imageGroupId = product.getImageGroupId();
        productRepository.delete(product); // FK ON DELETE CASCADE 로 옵션도 함께 지워진다(V22)
        imageService.deleteGroup(imageGroupId);
        log.info("[상품] 영구 삭제 — id={} name={} (유예 경과)", id, product.getName());
    }

    /**
     * 옵션 목록을 정렬 순서대로 저장한다. 비어 있으면 상품이 주문 불가가 되므로 최소 1개를 요구한다.
     *
     * <p>저장된 엔티티를 <b>돌려준다</b> — 호출부가 재고 이력에 새 옵션 id 를 실어야 하기 때문이다(B-19).
     */
    private List<ProductVariant> saveVariants(UUID productId, List<VariantRequest> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_NO_VARIANT);
        }
        List<ProductVariant> saved = new ArrayList<>();
        int order = 0;
        for (VariantRequest v : variants) {
            saved.add(variantRepository.save(ProductVariant.of(
                    productId, v.name().trim(), v.priceDelta(), v.stock(), order++)));
        }
        return saved;
    }

    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    /**
     * 주문용 재고 차감(원자적) — 옵션 단위. 재고 부족이면 예외.
     * 예전 decreaseStock(productId)가 재고를 옵션으로 옮기며 variantId를 받게 됐다.
     *
     * <p>{@code orderId} 는 이력에 남길 근거다(B-19) — 주문 경로는 행위자를 따로 안 적고 이 값으로 되짚는다.
     */
    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void decreaseStock(UUID variantId, long quantity, UUID orderId) {
        if (variantRepository.decreaseStock(variantId, quantity) == 0) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        // 차감 후 값은 스칼라 프로젝션으로 읽는다(벌크 UPDATE 라 1차 캐시가 stale). 이력과 알림이
        // **같은 스냅샷**을 쓰므로 둘이 갈릴 수 없다.
        Optional<VariantStockSnapshot> snapshot = variantRepository.findStockSnapshot(variantId);
        snapshot.ifPresent(s -> stockHistoryRepository.save(StockHistory.ordered(
                s.productId(), s.variantName(), variantId, quantity, s.stock(), orderId)));
        publishIfRunningLow(snapshot);
    }

    /** 차감 후 잔여가 임계치 이하면 재고 부족 이벤트 발행(어느 옵션인지 함께). */
    private void publishIfRunningLow(Optional<VariantStockSnapshot> snapshot) {
        snapshot.filter(s -> s.stock() <= catalogProperties.lowStockThreshold())
                .ifPresent(s -> eventPublisher.publishEvent(new StockRunningLowEvent(
                        s.productId(), s.productName(), s.variantName(),
                        s.stock(), catalogProperties.lowStockThreshold())));
    }

    /**
     * 주문 취소·반품 시 재고 복원 — 옵션 단위. 옵션이 삭제됐거나 정보가 없으면 조용히 무시.
     *
     * <p>{@code reason} 은 {@link StockChangeReason#CANCEL} 또는 {@link StockChangeReason#RETURN} —
     * 재고 관점에선 같은 일이지만 원장에서 "왜 돌아왔는지" 가 구분돼야 값이 있다(B-19).
     */
    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void increaseStock(UUID variantId, long quantity, StockChangeReason reason, UUID orderId) {
        if (variantId == null) {
            return;
        }
        if (variantRepository.increaseStock(variantId, quantity) == 0) {
            return; // 옵션이 이미 삭제됨(관리자 편집 등) — 복원할 대상이 없다.
                    // ⚠ 이력도 남기지 않는다: 재고가 실제로 안 변했으므로 남기면 원장이 거짓이 된다.
        }
        // 이 옵션 증가로 상품 총재고가 0→양수가 됐으면 재입고. 상품 총재고에서 방금 더한 양을 빼면
        // 증가 직전 총재고이므로, 그게 0이었는지로 "품절이 풀렸는지"를 판단한다(옵션 여러 개여도 한 번만 발행).
        variantRepository.findStockSnapshot(variantId).ifPresent(s -> {
            stockHistoryRepository.save(StockHistory.restored(
                    s.productId(), s.variantName(), variantId, reason, quantity, s.stock(), orderId));
            publishIfReplenished(s.productId(),
                    variantRepository.sumStockByProduct(s.productId()) - quantity, s.productName());
        });
    }

    /** 상품 총재고가 {@code stockBefore}(0)에서 지금 양수가 됐으면 재입고 이벤트를 낸다. */
    private void publishIfReplenished(UUID productId, long stockBefore, String productName) {
        if (stockBefore != 0) {
            return; // 원래 재고가 있었으면 "재입고"가 아니다
        }
        if (variantRepository.sumStockByProduct(productId) > 0) {
            eventPublisher.publishEvent(new StockReplenishedEvent(productId, productName));
        }
    }

    /**
     * 관리자 편집의 재고 변동을 <b>옵션명 기준으로 대조</b>해 남긴다 (B-19).
     *
     * <p>옵션이 통째로 교체되므로 id 로는 전/후를 맞출 수 없다. 이름으로 맞추면 세 가지가 다 잡힌다:
     * <ul>
     *   <li>이름이 양쪽에 있고 재고가 다르면 → 그 차이({@code 후 − 전})</li>
     *   <li>새로 생긴 이름 → 후 재고만큼 증가</li>
     *   <li>사라진 이름 → 전 재고만큼 감소(변동 후 재고는 0)</li>
     * </ul>
     *
     * <p>⚠ <b>변동이 0이면 남기지 않는다.</b> 상품명·설명만 고쳐도 저장은 옵션을 통째로 다시 만드는데,
     * 그때마다 줄이 쌓이면 원장이 시끄러워져 진짜 변동이 묻힌다.
     */
    private void recordEdit(UUID productId, Map<String, Long> before,
                            List<ProductVariant> after, AuthUser actor) {
        Map<String, Long> afterByName = stockByName(after);
        Map<String, ProductVariant> variantByName = new LinkedHashMap<>();
        after.forEach(v -> variantByName.putIfAbsent(v.getName(), v));

        Set<String> names = new LinkedHashSet<>(afterByName.keySet());
        names.addAll(before.keySet());

        for (String name : names) {
            long from = before.getOrDefault(name, 0L);
            long to = afterByName.getOrDefault(name, 0L);
            // ⚠ 여기서 `from == to` 를 걸러내지 않는다 — 변동 0 을 막는 것은 recordAdmin 한 곳이다.
            //    처음엔 이 자리에도 같은 가드를 뒀는데, **변형 주입에서 뒤집어도 아무 테스트가 안
            //    빨개졌다**(2026-08-04 M2). 아래 가드가 흡수하고 있어서다 — 같은 규칙을 두 곳이
            //    지키면 한쪽은 죽은 코드이고, 죽은 코드는 "지키고 있다"는 착각만 만든다.
            recordAdmin(productId, variantByName.get(name), name, StockChangeReason.ADMIN_EDIT,
                    to - from, to, actor);
        }
    }

    /**
     * 옵션명 → 재고 합.
     *
     * <p>같은 이름이 둘이면 <b>합친다</b> — 이력을 이름으로 잇는 이상 이름이 겹치면 한 줄로 볼 수밖에 없고,
     * 합쳐야 "합계 = 현재 재고" 가 유지된다.
     */
    private Map<String, Long> stockByName(List<ProductVariant> variants) {
        Map<String, Long> byName = new LinkedHashMap<>();
        variants.forEach(v -> byName.merge(v.getName(), v.getStock(), Long::sum));
        return byName;
    }

    private void recordAdmin(UUID productId, ProductVariant variant, StockChangeReason reason,
                             long quantity, long stockAfter, AuthUser actor) {
        recordAdmin(productId, variant, variant.getName(), reason, quantity, stockAfter, actor);
    }

    /**
     * {@code variant} 는 <b>null 일 수 있다</b> — 삭제된 옵션의 마지막 줄이라 가리킬 대상이 없다.
     * 그때도 이름은 남으므로 이력은 끊기지 않는다.
     */
    private void recordAdmin(UUID productId, ProductVariant variant, String variantName,
                             StockChangeReason reason, long quantity, long stockAfter, AuthUser actor) {
        if (quantity == 0) {
            return; // 변동 없음 — 원장에 남길 것이 없다
        }
        stockHistoryRepository.save(StockHistory.byAdmin(
                productId, variantName, variant == null ? null : variant.getId(),
                reason, quantity, stockAfter,
                actor == null ? null : actor.id(),
                actor == null ? null : actor.nickname()));
    }
}
