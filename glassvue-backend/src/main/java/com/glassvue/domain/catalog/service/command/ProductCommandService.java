package com.glassvue.domain.catalog.service.command;

import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.event.AdminActionEvent;
import com.glassvue.domain.catalog.config.CatalogProperties;
import com.glassvue.domain.catalog.dto.ProductCreateRequest;
import com.glassvue.domain.catalog.dto.ProductUpdateRequest;
import com.glassvue.domain.catalog.dto.VariantRequest;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
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
import java.util.Objects;
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

    /**
     * 정가는 <b>할인 전 가격</b>이라 판매가보다 커야 뜻이 있다 — 비우면 «할인 없음» 이다.
     *
     * <p>🔴 <b>2026-08-13 까지 이 규칙은 화면에만 있었다.</b> DTO 는 {@code @PositiveOrZero} 뿐이라
     * API 로 부르면 <b>정가 0원·정가 &lt; 판매가</b> 가 그대로 저장됐고, 그러면 상세 화면이
     * <b>할인이 아닌데 취소선을 그린다</b>(뜻 없는 «할인» 표시).
     *
     * <p>⚠ 발견 경위는 화면 쪽이었다 — 정가 칸이 «지울 수 없는 0» 에 갇히는 버그를 보다가,
     * 그 0 을 <b>서버는 아무 말 없이 받는다</b> 는 걸 알았다.
     * ⚠ {@code null} 은 통과시킨다(그게 «할인 없음» 의 표현이다).
     */
    private static void validateListPrice(Long listPrice, long price) {
        if (listPrice != null && listPrice <= price) {
            throw new BusinessException(ErrorCode.PRODUCT_LIST_PRICE_NOT_HIGHER);
        }
    }

    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public UUID create(ProductCreateRequest req, AuthUser actor) {
        validateListPrice(req.listPrice(), req.price());
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
        // 등록은 «전» 이 없으니 스냅샷을 적는다 — 이름·판매가·옵션 수. 재고는 위 원장이 이미 갖고 있다.
        publishAudit(AuditAction.PRODUCT_CREATE, actor, saved,
                saved.getName() + " · 판매가 " + saved.getPrice() + "원 · 옵션 " + variants.size() + "개");
        log.info("Product created: {} ({} variants)", saved.getId(), variants.size());
        return saved.getId();
    }

    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void update(UUID id, ProductUpdateRequest req, AuthUser actor) {
        // ⚠ 등록에만 걸면 «만들 때는 막히고 고칠 때는 통과» 가 된다 — 규칙이 반쪽이면 없느니만 못하다.
        validateListPrice(req.listPrice(), req.price());
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        Category category = findCategory(req.categoryId());
        UUID oldGroupId = product.getImageGroupId();
        UUID imageGroupId = imageService.createGroup(req.imageIds());
        long stockBefore = variantRepository.sumStockByProduct(id); // 옵션 교체 전 총재고(재입고 판단용)
        // ⚠ 감사에 적을 «전» 은 update() 가 덮어쓰기 **전에** 붙잡아야 한다 — 뒤에서 읽으면 전후가 같아진다.
        ProductSnapshot before = ProductSnapshot.of(product, variantRepository.countByProductId(id));
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

        boolean stockChanged = recordEdit(id, stockByName, saved, actor);

        imageService.deleteGroup(oldGroupId);

        // 관리자 재고 편집도 재입고 경로다 — 실제 이커머스에서 품절이 풀리는 주된 경로.
        // 옵션을 통째로 교체하므로 옵션이 아니라 상품 총재고 0→양수로 판단한다. sumStockByProduct 는
        // JPQL 이라 위 save 들을 flush 한 뒤의 값을 본다(새 옵션 반영).
        publishIfReplenished(id, stockBefore, product.getName());
        publishIfCrossedIntoLow(id, product.getName(), stockByName, saved);

        publishAudit(AuditAction.PRODUCT_UPDATE, actor, product,
                describeChanges(before, ProductSnapshot.of(product, saved.size()), stockChanged));
    }

    /**
     * 감사 {@code detail} 에 적을 «전» 상태 (2026-08-20, V53).
     *
     * <p>⚠ <b>재고는 담지 않는다</b> — {@code stock_history}(B-19)가 이미 «누가·언제·얼마나» 를 갖는다.
     * 같은 사실을 두 곳에 적으면 한쪽만 고쳐져 어긋난다(CLAUDE.md).
     *
     * <p>⚠ <b>이미지도 담지 않는다</b> — {@code imageService.createGroup} 이 저장할 때마다 <b>새 그룹</b>을
     * 만들어서, 그룹 id 를 비교하면 «이미지 바뀜» 이 <b>항상 참</b>이 된다. 그건 정보가 아니라 소음이다.
     * 🔴 <b>«비교할 수 없으니 안 적는다» 를 적어 둔다</b> — 안 적으면 다음 사람이 «왜 이미지는 빠졌지» 를
     * 되짚어야 한다.
     */
    private record ProductSnapshot(String name, String tagline, String description, long price,
                                   Long listPrice, ProductStatus status, String categoryName,
                                   long variantCount) {

        static ProductSnapshot of(Product product, long variantCount) {
            return new ProductSnapshot(product.getName(), product.getTagline(), product.getDescription(),
                    product.getPrice(), product.getListPrice(), product.getStatus(),
                    product.getCategory() == null ? null : product.getCategory().getName(), variantCount);
        }
    }

    /**
     * «무엇이 바뀌었나» 를 한 줄로 (2026-08-20 사용자와 확정 — V50 이 미뤄 둔 결정).
     *
     * <p><b>바뀐 것만</b> 적는다. 매번 전부 적으면 그 안에서 바뀐 것을 눈으로 찾아야 해서
     * 원장을 읽는 이유가 사라진다.
     *
     * <p>⚠ 설명·태그라인은 <b>«바뀜» 만</b> 적는다 — 본문을 전/후로 다 실으면 {@code detail}(1000자)을
     * 넘긴다. 넘치면 잘리는데, <b>잘린 원장은 틀린 원장</b>이다.
     *
     * <p>⚠ 바뀐 것이 하나도 없으면 <b>«변경 없음»</b> 이다({@code null} 이 아니다).
     * 관리 화면이 상품 전체를 다시 보내 흔한 경우이고, 그래도 줄은 남긴다 —
     * «누가 언제 손댔나» 를 접근 기록으로 본다(2026-08-20 사용자와 확정).
     */
    private String describeChanges(ProductSnapshot before, ProductSnapshot after,
                                   boolean stockChanged) {
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(before.name(), after.name())) {
            changes.add("이름 " + before.name() + "→" + after.name());
        }
        if (before.price() != after.price()) {
            changes.add("판매가 " + before.price() + "→" + after.price());
        }
        if (!Objects.equals(before.listPrice(), after.listPrice())) {
            changes.add("정가 " + text(before.listPrice()) + "→" + text(after.listPrice()));
        }
        if (before.status() != after.status()) {
            changes.add("상태 " + before.status() + "→" + after.status());
        }
        if (!Objects.equals(before.categoryName(), after.categoryName())) {
            changes.add("분류 " + text(before.categoryName()) + "→" + text(after.categoryName()));
        }
        if (before.variantCount() != after.variantCount()) {
            changes.add("옵션 " + before.variantCount() + "→" + after.variantCount() + "개");
        }
        if (!Objects.equals(before.tagline(), after.tagline())) {
            changes.add("태그라인 바뀜");
        }
        if (!Objects.equals(before.description(), after.description())) {
            changes.add("설명 바뀜");
        }
        if (stockChanged) {
            // 🔴 **값이 아니라 «어디를 보라» 를 적는다**(2026-08-20 사용자 결정, 브라우저 검증에서 나왔다).
            //    수량을 적으면 stock_history 와 같은 사실이 두 곳에 남아 한쪽만 고쳐질 때 어긋난다 —
            //    그 판단은 그대로 두고, **갈라야 할 것은 값이 아니라 «일이 있었나»** 였다.
            //    ⚠ 실증: 재고만 5개 움직인 저장이 원장에 «변경 없음» 으로 남아,
            //      **정말 아무 일도 없던 저장 둘과 한 글자도 다르지 않았다**(10:59 세 줄).
            //      감사 화면만 보는 사람에게는 셋이 다 «헛저장» 으로 읽힌다.
            changes.add("재고 바뀜(이력 참조)");
        }
        return changes.isEmpty() ? "변경 없음" : String.join(" · ", changes);
    }

    private String text(Object value) {
        return value == null ? "없음" : String.valueOf(value);
    }

    /**
     * 관리자 편집으로 재고가 <b>임계 위 → 이하로 넘어간</b> 옵션에 재고 부족을 알린다
     * (2026-08-14, BACKLOG F-8).
     *
     * <p>🔴 <b>주문 경로와 규칙이 일부러 다르다.</b> 주문은 «차감 후 값이 임계 이하면» 발행이라
     * 5→4→3 으로 팔리면 <b>세 번</b> 온다(실측: 07-27 에 4·3·2·1 로 네 건). 그게 주문에는 맞다 —
     * 파는 사람은 재고가 줄고 있다는 것을 매번 알아야 한다.
     * <b>편집에 같은 규칙을 쓰면 관리자가 저장할 때마다 온다</b>(8→7 로 고쳐도 7이 임계 이하면 발송).
     * 관리자는 <b>자기가 그 값을 입력한 사람</b>이라 그건 알림이 아니라 소음이다.
     * → 편집은 <b>전이</b>일 때만 낸다(2026-08-14 사용자와 확정).
     *
     * <p>⚠ <b>새로 생긴 옵션은 내지 않는다.</b> 비교할 이전 상태가 없어 «넘어갔다» 가 성립하지 않는다
     * (재고 1짜리 옵션을 새로 만들어도 조용하다 — 방금 그 값을 입력한 사람이 안다).
     * ⚠ 🔴 <b>사라진 옵션도 내지 않는다.</b> {@code recordEdit} 은 이름 합집합을 돌며 사라진 옵션을
     * «0 으로 갔다» 로 세지만(이력에는 그게 맞다), <b>여기서 같은 셈을 하면 옵션을 지운 것이
     * 「재고 부족」으로 나간다.</b> 그 옵션은 이제 없으므로 채울 재고도 없다 —
     * 그래서 <b>편집 후 살아남은 옵션만</b> 본다.
     *
     * <p>⚠ 재입고와 동시에 날 수는 없다: 총재고가 0 이었다면 모든 옵션이 0 이라 «임계 위» 인 옵션이
     * 하나도 없다. 둘은 구조적으로 배타적이다.
     */
    private void publishIfCrossedIntoLow(UUID productId, String productName,
                                         Map<String, Long> before, List<ProductVariant> after) {
        long threshold = catalogProperties.lowStockThreshold();
        for (ProductVariant variant : after) {
            Long from = before.get(variant.getName());
            if (from == null) {
                continue; // 새 옵션 — 넘어온 것이 아니라 그렇게 태어났다
            }
            if (from > threshold && variant.getStock() <= threshold) {
                eventPublisher.publishEvent(new StockRunningLowEvent(
                        productId, productName, variant.getName(), variant.getStock(), threshold));
            }
        }
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
    public void delete(UUID id, AuthUser actor) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.softDelete(actor.nickname())) {
            return; // 이미 대기 중 — 조작이 없었으므로 감사도 남기지 않는다
        }
        log.info("[상품] 삭제 대기 — id={} name={} by={}", id, product.getName(), actor.nickname());
        publishAudit(AuditAction.PRODUCT_DELETE, actor, product);
    }

    /**
     * 삭제 대기를 되돌린다 (2026-08-12, F-7).
     *
     * <p>⚠ <b>대기 중이 아닌 상품에 부르면 아무 일도 없다</b>(멱등). 에러로 만들지 않는 이유는
     * 복구 화면에서 두 번 눌렀을 때 <b>«실패» 로 보이면 안 되기 때문</b>이다 — 원하는 상태(살아 있음)는
     * 이미 이뤄져 있다(반품 숨김 해제가 멱등인 것과 같은 판단).
     */
    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void restore(UUID id, AuthUser actor) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.restore()) {
            return; // 대기 중이 아니었다 — 두 번 눌러도 감사는 한 줄이다
        }
        log.info("[상품] 삭제 대기 해제 — id={} name={} by={}", id, product.getName(), actor.nickname());
        publishAudit(AuditAction.PRODUCT_RESTORE, actor, product);
    }

    /**
     * 상품 조작을 감사 원장에 잇는다 (2026-08-14).
     *
     * <p>🔴 <b>대상이 회원이 아닌 첫 자리다</b> — {@code targetId} 에 <b>상품 id</b> 를 넣고
     * {@code targetLogin} 은 {@code null} 로 둔다. «없는 회원» 을 가리키는 것이 아니라
     * <b>애초에 회원이 대상이 아니다</b>(뜻은 {@link AuditAction#PRODUCT_DELETE} 주석에 적었다).
     *
     * <p>⚠ 상품명은 <b>스냅샷</b>이라 {@code detail} 에 넣는다 — 상품은 유예가 지나면 진짜로 사라지고,
     * 그러면 id 만으로는 «무엇을 지웠는지» 를 영영 못 읽는다(감사는 대상보다 오래 산다).
     *
     * <p>⚠ audit 의 내부를 직접 부르지 않고 이벤트만 발행한다(도메인 간 직접 참조 금지 — CLAUDE.md).
     * 기본 {@code @EventListener} 라 <b>같은 트랜잭션</b>이다 — 감사가 실패하면 삭제도 롤백된다.
     */
    private void publishAudit(AuditAction action, AuthUser actor, Product product) {
        publishAudit(action, actor, product, product.getName());
    }

    /**
     * detail 을 따로 주는 갈래 — 등록·수정이 쓴다(삭제·복구는 상품명 하나로 충분하다).
     *
     * <p>⚠ {@code detail} 열은 <b>1000자</b>다. 넘칠 일이 없게 만들었지만
     * ({@link #describeChanges} 가 긴 필드를 «바뀜» 으로 접는다) <b>넘치면 저장이 통째로 실패</b>하므로
     * 여기서 한 번 더 자른다 — 🔴 <b>감사가 실패하면 조작도 롤백된다</b>(같은 트랜잭션).
     * 상품 하나 못 고치는 것보다 원장 한 줄이 잘리는 편이 낫다.
     */
    private void publishAudit(AuditAction action, AuthUser actor, Product product, String detail) {
        eventPublisher.publishEvent(new AdminActionEvent(
                action, actor.id(), actor.nickname(), product.getId(), null,
                detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail));
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

    /**
     * 차감 후 잔여가 임계치 이하면 재고 부족 이벤트 발행(어느 옵션인지 함께).
     *
     * <p>⚠ <b>이것은 「상태」 판정이다</b> — 차감할 때마다 값을 보므로 5→4→3 이면 <b>세 번</b> 나간다.
     * 파는 사람은 재고가 줄고 있다는 것을 매번 알아야 하므로 주문 경로에는 이게 맞다.
     * 🔴 <b>편집 경로는 「전이」로 갈렸다</b> — 이유는 {@link #publishIfCrossedIntoLow} 에 적었다.
     * 두 규칙이 다른 것은 실수가 아니다.
     */
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
     *
     * @return 재고 줄을 <b>하나라도 남겼는가</b>. 감사 {@code detail} 이 «재고가 움직였다» 를
     *         가리키는 데 쓴다(2026-08-20). ⚠ <b>가드를 여기로 옮긴 것이 아니다</b> —
     *         판정은 여전히 {@code recordAdmin} 한 곳이고, 여기서는 <b>그것이 한 일을 전할 뿐</b>이다
     *         (같은 규칙을 두 곳이 지키면 한쪽은 죽은 코드다 — 2026-08-04 M2 의 교훈).
     */
    private boolean recordEdit(UUID productId, Map<String, Long> before,
                               List<ProductVariant> after, AuthUser actor) {
        Map<String, Long> afterByName = stockByName(after);
        Map<String, ProductVariant> variantByName = new LinkedHashMap<>();
        after.forEach(v -> variantByName.putIfAbsent(v.getName(), v));

        Set<String> names = new LinkedHashSet<>(afterByName.keySet());
        names.addAll(before.keySet());

        boolean recorded = false;
        for (String name : names) {
            long from = before.getOrDefault(name, 0L);
            long to = afterByName.getOrDefault(name, 0L);
            // ⚠ 여기서 `from == to` 를 걸러내지 않는다 — 변동 0 을 막는 것은 recordAdmin 한 곳이다.
            //    처음엔 이 자리에도 같은 가드를 뒀는데, **변형 주입에서 뒤집어도 아무 테스트가 안
            //    빨개졌다**(2026-08-04 M2). 아래 가드가 흡수하고 있어서다 — 같은 규칙을 두 곳이
            //    지키면 한쪽은 죽은 코드이고, 죽은 코드는 "지키고 있다"는 착각만 만든다.
            // 🔴 `|=` 다(`||` 가 아니다) — `||` 로 쓰면 한 번 참이 된 뒤의 옵션은 **호출 자체가
            //    건너뛰어져** 재고 이력이 통째로 빠진다. 단축 평가가 부작용을 삼키는 자리다.
            recorded |= recordAdmin(productId, variantByName.get(name), name,
                    StockChangeReason.ADMIN_EDIT, to - from, to, actor);
        }
        return recorded;
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

    private boolean recordAdmin(UUID productId, ProductVariant variant, StockChangeReason reason,
                                long quantity, long stockAfter, AuthUser actor) {
        return recordAdmin(productId, variant, variant.getName(), reason, quantity, stockAfter, actor);
    }

    /**
     * {@code variant} 는 <b>null 일 수 있다</b> — 삭제된 옵션의 마지막 줄이라 가리킬 대상이 없다.
     * 그때도 이름은 남으므로 이력은 끊기지 않는다.
     */
    private boolean recordAdmin(UUID productId, ProductVariant variant, String variantName,
                                StockChangeReason reason, long quantity, long stockAfter,
                                AuthUser actor) {
        if (quantity == 0) {
            return false; // 변동 없음 — 원장에 남길 것이 없다
        }
        stockHistoryRepository.save(StockHistory.byAdmin(
                productId, variantName, variant == null ? null : variant.getId(),
                reason, quantity, stockAfter,
                actor == null ? null : actor.id(),
                actor == null ? null : actor.nickname()));
        return true;
    }
}
