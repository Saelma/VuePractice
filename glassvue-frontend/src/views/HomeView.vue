<script setup>
/**
 * 스토어프론트 홈 (2026-07-24, B-8). 첫 화면을 공지에서 상품 중심으로 바꾼 것.
 *
 * 구성: 히어로(정적) → 카테고리 바로가기 → 인기(판매량)·신상품·할인 섹션 → 공지 요약 3건.
 * 각 섹션은 상품 목록 API 를 조건만 달리해 재사용한다(인기순=soldCount, 신상품=createdAt).
 * "더보기"는 같은 조건을 쿼리로 실어 /products 로 넘긴다 — 목록 화면이 그 쿼리를 읽어 이어서 보여준다.
 *
 * 할인 섹션만 예외다: 서버에 "할인만" 필터가 없어(정가−판매가 비교는 화면 계산) 최근 상품을 넉넉히
 * 받아 화면에서 hasDiscount 로 거른 티저다. 없으면 섹션 자체를 숨긴다.
 *
 * 색은 DESIGN.md 를 따른다 — 강조색(near-black brand)은 히어로·CTA 에만, 장식용 그라디언트는 쓰지 않는다.
 */
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { fetchProducts, hasDiscount, priceText } from '../api/product';
import { fetchCategories } from '../api/category';
import { fetchNotices } from '../api/notice';
import { fetchOrders, orderStatusText, orderStatusClass } from '../api/order';
import { fetchPointAccount, gradeText } from '../api/point';
import { addToCart } from '../api/cart';
import { isLoggedIn } from '../stores/auth';
import { loadWishlistIds } from '../stores/wishlist';
import { recentlyViewed } from '../stores/recentlyViewed';
import ProductCard from '../components/ProductCard.vue';

const SECTION_SIZE = 4;

const router = useRouter();
const categories = ref([]);
const popular = ref([]);
const fresh = ref([]);
const discounted = ref([]);
const notices = ref([]);
const loading = ref(true);

// 로그인 개인화 — 적립금·등급 요약 + 최근 주문 1건("다시 담기").
const pointAccount = ref(null);
const recentOrder = ref(null);
const reorderMsg = ref('');

const fmtDate = (iso) => (iso ? new Date(iso).toLocaleDateString('ko-KR') : '');

onMounted(async () => {
  if (isLoggedIn.value) loadWishlistIds(); // 카드의 찜 하트 채우기(실패해도 홈은 동작)

  // 섹션은 서로 독립이라 하나가 실패해도 나머지는 보여준다(allSettled).
  const [cat, pop, recent, forDiscount, notice] = await Promise.allSettled([
    fetchCategories(),
    fetchProducts({ sort: 'soldCount,desc', size: SECTION_SIZE }),
    fetchProducts({ sort: 'createdAt,desc', size: SECTION_SIZE }),
    fetchProducts({ sort: 'createdAt,desc', size: 24 }), // 할인 티저용 풀
    fetchNotices({ size: 3 }),
  ]);

  if (cat.status === 'fulfilled') categories.value = cat.value;
  if (pop.status === 'fulfilled') popular.value = pop.value.content;
  if (recent.status === 'fulfilled') fresh.value = recent.value.content;
  if (forDiscount.status === 'fulfilled') {
    discounted.value = forDiscount.value.content.filter(hasDiscount).slice(0, SECTION_SIZE);
  }
  if (notice.status === 'fulfilled') notices.value = notice.value.content;

  loading.value = false;

  // 개인화는 로그인 시에만. 위 섹션 로딩을 막지 않게 뒤에서 따로 받는다(실패해도 홈은 그대로).
  if (isLoggedIn.value) {
    const [point, orders] = await Promise.allSettled([
      fetchPointAccount(),
      fetchOrders({ size: 1 }),
    ]);
    if (point.status === 'fulfilled') pointAccount.value = point.value;
    if (orders.status === 'fulfilled') recentOrder.value = orders.value.content[0] ?? null;
  }
});

/**
 * 최근 주문 "다시 담기" — 그 주문의 품목을 장바구니에 다시 넣는다(재구매).
 * 옵션(variant)이 그새 삭제됐을 수 있어 품목별로 시도하고, 담긴 것만 세어 안내한다.
 * variantId 가 없는 옛 주문(옵션 도입 전) 품목은 건너뛴다.
 */
async function reorder() {
  const items = (recentOrder.value?.items ?? []).filter((it) => it.variantId);
  if (!items.length) {
    reorderMsg.value = '다시 담을 수 있는 상품이 없어요.';
    return;
  }
  let added = 0;
  for (const it of items) {
    try {
      await addToCart(it.variantId, it.quantity);
      added += 1;
    } catch (e) {
      /* 삭제·품절된 옵션 — 건너뛴다 */
    }
  }
  if (added === 0) {
    reorderMsg.value = '품절되었거나 판매가 끝난 상품이라 담지 못했어요.';
    return;
  }
  const skipped = items.length - added;
  if (skipped) {
    // 일부만 담긴 경우 — 장바구니로 넘어가면 왜 적은지 알 수 없으니 먼저 알린다.
    window.alert(`${added}개를 장바구니에 담았어요. ${skipped}개는 품절되었거나 판매가 끝나 담지 못했어요.`);
  }
  router.push('/cart');
}
</script>

<template>
  <section class="page space-y-12">
    <!-- 히어로 (정적). near-black 강조색 카드 — 장식이 아니라 첫인상 대비용. -->
    <div class="rounded-card bg-brand-600 px-6 py-14 text-white sm:px-12 sm:py-20">
      <p class="text-xs font-medium uppercase tracking-widest text-white/60">Glassvue Store</p>
      <h1 class="mt-3 max-w-2xl text-3xl font-bold leading-tight sm:text-4xl">
        매일 쓰는 것들을, 조금 더 좋은 것으로.
      </h1>
      <p class="mt-3 max-w-xl text-white/70">엄선한 상품을 골라 담았어요. 지금 인기 있는 것부터 둘러보세요.</p>
      <RouterLink
        to="/products"
        class="mt-6 inline-flex rounded-control bg-white px-5 py-2.5 text-sm font-medium text-ink-900 transition-colors hover:bg-white/90"
      >전체 상품 보기</RouterLink>
    </div>

    <!-- 로그인 개인화 — 적립금·등급 + 최근 주문 다시 담기 (있을 때만) -->
    <section v-if="isLoggedIn && (pointAccount || recentOrder)" class="grid gap-4 sm:grid-cols-2">
      <!-- 적립금·등급 -->
      <RouterLink
        v-if="pointAccount"
        to="/settings"
        class="card card-link flex items-center justify-between gap-4 p-5"
      >
        <div>
          <p class="muted">내 적립금</p>
          <p class="mt-1 text-xl font-bold tabular-nums text-ink-900">{{ priceText(pointAccount.balance) }}</p>
        </div>
        <span class="badge badge-neutral">{{ gradeText(pointAccount.grade) }}</span>
      </RouterLink>

      <!-- 최근 주문 + 다시 담기 -->
      <div v-if="recentOrder" class="card flex flex-col gap-3 p-5">
        <div class="flex items-center justify-between gap-2">
          <div class="min-w-0">
            <p class="muted">최근 주문</p>
            <RouterLink :to="`/orders/${recentOrder.id}`" class="mt-1 block truncate text-sm font-medium text-ink-900 hover:underline">
              #{{ recentOrder.orderNo }}
            </RouterLink>
          </div>
          <span class="badge shrink-0" :class="orderStatusClass(recentOrder.status)">{{ orderStatusText(recentOrder.status) }}</span>
        </div>
        <div class="flex items-center justify-between gap-2">
          <span class="muted">{{ fmtDate(recentOrder.createdAt) }} · {{ recentOrder.items.length }}개 상품</span>
          <button type="button" class="btn btn-secondary btn-sm" @click="reorder">다시 담기</button>
        </div>
        <p v-if="reorderMsg" class="field-error">{{ reorderMsg }}</p>
      </div>
    </section>

    <!-- 카테고리 바로가기 -->
    <section v-if="categories.length" aria-label="카테고리 바로가기">
      <div class="flex flex-wrap gap-2">
        <RouterLink
          v-for="c in categories"
          :key="c.id"
          :to="{ path: '/products', query: { categoryId: c.id } }"
          class="badge badge-neutral hover:bg-brand-100"
        >{{ c.name }}</RouterLink>
      </div>
    </section>

    <!-- 최근 본 상품 (localStorage, 로그인 무관) — 있을 때만. 가로 스크롤 스트립. -->
    <section v-if="recentlyViewed.length" aria-label="최근 본 상품">
      <h2 class="section-title mb-4">최근 본 상품</h2>
      <div class="flex gap-4 overflow-x-auto pb-2">
        <RouterLink
          v-for="p in recentlyViewed"
          :key="p.id"
          :to="`/products/${p.id}`"
          class="w-32 shrink-0"
        >
          <div class="aspect-square overflow-hidden rounded-card border border-line bg-canvas">
            <img v-if="p.thumbUrl" :src="p.thumbUrl" :alt="p.name" class="h-full w-full object-cover" />
            <div v-else class="flex h-full items-center justify-center text-2xl text-ink-400">🖼️</div>
          </div>
          <p class="mt-2 line-clamp-1 text-xs text-ink-700">{{ p.name }}</p>
          <p class="text-sm font-semibold tabular-nums text-ink-900">{{ priceText(p.price) }}</p>
        </RouterLink>
      </div>
    </section>

    <!-- 로딩 스켈레톤(섹션 하나 분량) -->
    <div v-if="loading" class="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
      <div v-for="n in 4" :key="n" class="overflow-hidden rounded-card border border-line bg-surface shadow-card">
        <div class="skeleton aspect-square rounded-none"></div>
        <div class="space-y-2 p-4">
          <div class="skeleton h-3 w-16"></div>
          <div class="skeleton h-4 w-3/4"></div>
          <div class="skeleton h-5 w-24"></div>
        </div>
      </div>
    </div>

    <template v-else>
      <!-- 인기 상품 -->
      <section v-if="popular.length">
        <div class="mb-4 flex items-end justify-between">
          <h2 class="section-title">인기 상품</h2>
          <RouterLink :to="{ path: '/products', query: { sort: 'soldCount,desc' } }" class="text-sm text-ink-500 hover:text-ink-900">
            더보기 →
          </RouterLink>
        </div>
        <div class="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
          <ProductCard v-for="p in popular" :key="p.id" :product="p" />
        </div>
      </section>

      <!-- 신상품 -->
      <section v-if="fresh.length">
        <div class="mb-4 flex items-end justify-between">
          <h2 class="section-title">신상품</h2>
          <RouterLink :to="{ path: '/products', query: { sort: 'createdAt,desc' } }" class="text-sm text-ink-500 hover:text-ink-900">
            더보기 →
          </RouterLink>
        </div>
        <div class="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
          <ProductCard v-for="p in fresh" :key="p.id" :product="p" />
        </div>
      </section>

      <!-- 할인 상품 (있을 때만) -->
      <section v-if="discounted.length">
        <div class="mb-4 flex items-end justify-between">
          <h2 class="section-title">할인 중</h2>
          <RouterLink to="/products" class="text-sm text-ink-500 hover:text-ink-900">더보기 →</RouterLink>
        </div>
        <div class="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
          <ProductCard v-for="p in discounted" :key="p.id" :product="p" />
        </div>
      </section>

      <!-- 공지 요약 -->
      <section v-if="notices.length">
        <div class="mb-4 flex items-end justify-between">
          <h2 class="section-title">공지</h2>
          <RouterLink to="/notices" class="text-sm text-ink-500 hover:text-ink-900">전체 보기 →</RouterLink>
        </div>
        <ul class="card divide-y divide-line">
          <li v-for="n in notices" :key="n.id">
            <RouterLink
              :to="`/notices/${n.id}`"
              class="flex items-center gap-3 px-5 py-4 transition-colors hover:bg-canvas"
            >
              <span v-if="n.pinned" class="shrink-0 text-sm" title="상단 고정">📌</span>
              <span class="min-w-0 flex-1 truncate text-sm font-medium text-ink-900">{{ n.title }}</span>
              <span class="muted shrink-0 tabular-nums">{{ fmtDate(n.createdAt) }}</span>
            </RouterLink>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>
