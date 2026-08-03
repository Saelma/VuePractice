<script setup>
/**
 * 상품 상세 — 좌: 이미지 갤러리 / 우: 상품 정보의 2단 구성(DESIGN.md §7).
 * 정보를 한 줄에 늘어놓지 않고 카테고리 → 이름 → 가격 → 별점 → 재고 → 구매 순으로 읽히게 한다.
 */
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { DxNumberBox } from 'devextreme-vue/number-box';
import { getProduct, deleteProduct, statusText, priceText, hasDiscount, discountRate } from '../api/product';
import { addToCart } from '../api/cart';
import { loadCartCount } from '../stores/cart';
import { authState, isLoggedIn, isAdmin } from '../stores/auth';
import { loadWishlistIds } from '../stores/wishlist';
import { loadRestockIds } from '../stores/restock';
import { pushRecentlyViewed } from '../stores/recentlyViewed';
import StarRating from '../components/StarRating.vue';
import WishlistButton from '../components/WishlistButton.vue';
import RestockButton from '../components/RestockButton.vue';
import ProductReviews from '../components/ProductReviews.vue';
import ProductInquiries from '../components/ProductInquiries.vue';
import RelatedProducts from '../components/RelatedProducts.vue';

const props = defineProps({ id: { type: String, required: true } });
const route = useRoute();
const router = useRouter();

const product = ref(null);
const error = ref('');
const loading = ref(true);
const qty = ref(1);
const cartMsg = ref('');

/**
 * 옵션(variant) 선택 — 재고·가격이 옵션마다 다르다(2026-07-24 C-8).
 * 옵션이 2개 이상이면 사용자가 골라야 하고, 1개(기본)면 자동 선택돼 UI 를 감춘다.
 */
const variants = computed(() => product.value?.variants ?? []);
const hasOptions = computed(() => variants.value.length > 1);
const selectedVariantId = ref(null);
const selectedVariant = computed(() =>
  variants.value.find((v) => v.id === selectedVariantId.value) || (hasOptions.value ? null : variants.value[0]) || null);

/** 갤러리 표시용 로컬 상태 — 썸네일을 누르면 대표 이미지가 바뀐다(서버 데이터는 건드리지 않는다). */
const images = computed(() => product.value?.images ?? []);
const selected = ref(0);
const mainImage = computed(() => images.value[selected.value] ?? images.value[0] ?? null);

/** 담기 전에 얼마인지 바로 보이게 — 옵션 가격 × 수량. 옵션 미선택이면 기본가로 미리 보여준다. */
const unitPrice = computed(() => selectedVariant.value?.price ?? product.value?.price ?? 0);
const lineTotal = computed(() => unitPrice.value * (qty.value || 1));

/**
 * 상품 전체가 품절이면 재입고 알림 신청을 권한다(B-9). 부분 품절(다른 옵션은 살 수 있음)은
 * 신청 대상이 아니다 — 재입고 이벤트가 상품 총재고 0→양수에서만 나므로 기준을 맞춘다.
 */
const soldOutAll = computed(() => product.value?.totalStock === 0);

/**
 * 하단 스티키 서브탭(무신사식) — 상세정보·리뷰·문의를 잇는다. 탭을 누르면 해당 섹션으로 스크롤하고,
 * 스크롤하면 현재 보고 있는 섹션이 탭에 활성 표시된다(IntersectionObserver 스크롤스파이).
 */
const detailSec = ref(null);
const reviewSec = ref(null);
const inquirySec = ref(null);
const activeSection = ref('detail');
let observer = null;

function scrollTo(el) {
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// 앵커 정렬을 포기하는 신호 — 사용자가 직접 움직였으면 그 순간 손을 뗀다(스크롤 하이재킹 방지).
const ANCHOR_CANCEL = ['wheel', 'touchstart', 'keydown'];
let stopAnchor = null;

/**
 * 알림에서 `#inquiries` 로 들어왔을 때 그 섹션까지 데려간다.
 *
 * ⚠ **한 번 부르는 것으로는 안 된다.** 위쪽 {@code ProductReviews} 가 자기 데이터를 나중에 받아
 * 렌더되면서 문의 섹션을 아래로 밀어낸다 — 스크롤은 시작할 때 계산한 위치에서 멈추므로
 * **리뷰 중간에 선다.** 그래서 레이아웃이 잠잠해질 때까지(최대 2초) 다시 맞춘다.
 */
function scrollToAnchor(el) {
  if (!el) return;
  scrollTo(el);
  const ro = new ResizeObserver(() => scrollTo(el)); // 본문 높이가 변할 때마다 다시 맞춘다
  ro.observe(document.body);
  stopAnchor = () => {
    ro.disconnect();
    clearTimeout(timer);
    ANCHOR_CANCEL.forEach((t) => window.removeEventListener(t, stopAnchor));
    stopAnchor = null;
  };
  const timer = setTimeout(() => stopAnchor?.(), 2000);
  ANCHOR_CANCEL.forEach((t) => window.addEventListener(t, () => stopAnchor?.(), { once: true, passive: true }));
}
function tabClass(key) {
  return activeSection.value === key
    ? 'border-brand-600 font-semibold text-ink-900'
    : 'border-transparent text-ink-500 hover:text-ink-900';
}
function setupSpy() {
  const map = new Map([
    [detailSec.value, 'detail'],
    [reviewSec.value, 'reviews'],
    [inquirySec.value, 'inquiries'],
  ]);
  // 헤더(56px)+서브탭 아래에서 섹션이 상단 40%에 들어오면 활성으로 본다.
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) activeSection.value = map.get(e.target);
      });
    },
    { rootMargin: '-120px 0px -60% 0px', threshold: 0 },
  );
  map.forEach((_, el) => el && observer.observe(el));
}
onBeforeUnmount(() => {
  observer?.disconnect();
  stopAnchor?.(); // 2초 안에 다른 화면으로 가면 관측·타이머가 남지 않게
});

async function onAddToCart() {
  cartMsg.value = ''; error.value = '';
  if (!selectedVariant.value) {
    error.value = '옵션을 선택하세요.';
    return;
  }
  if (selectedVariant.value.soldOut) {
    error.value = '품절된 옵션이에요.';
    return;
  }
  try {
    await addToCart(selectedVariant.value.id, qty.value);
    cartMsg.value = '장바구니에 담았어요.';
    loadCartCount(true); // 헤더 🛒 배지 갱신
  } catch (e) {
    error.value = e.message;
  }
}

onMounted(async () => {
  if (isLoggedIn.value) {
    loadWishlistIds(); // 하트를 채우려면 내 찜 id가 필요하다
    loadRestockIds(); // 재입고 버튼 상태(신청함/안함)를 채운다
  }
  try {
    product.value = await getProduct(props.id);
    pushRecentlyViewed(product.value); // 홈 "최근 본 상품" 에 남긴다(localStorage, B-8)
    // ⚠ 스켈레톤을 **먼저** 내린다. 본문이 v-if="loading" 뒤에 있어서, 이걸 finally 로 미루면
    //    아래 nextTick 뒤에도 화면은 여전히 스켈레톤이고 섹션 ref 가 전부 null 이다
    //    → setupSpy 는 아무것도 관측하지 못하고(탭 밑줄이 스크롤을 안 따라감),
    //      앵커 스크롤도 조용히 no-op 이 된다(2026-07-31 실제로 그랬다).
    loading.value = false;
    await nextTick(); // 하단 섹션이 렌더된 뒤에 스크롤스파이를 건다
    setupSpy();
    // 문의 답변 알림(B-15)에서 /products/{id}#inquiries 로 들어오면 그 섹션까지 데려간다.
    // ⚠ 라우터 scrollBehavior 로 하지 않았다 — 그 시점엔 상품을 아직 못 받아 섹션이 렌더되기 전이라
    //    앵커 요소가 없다. 데이터가 온 **뒤**인 여기가 맞는 자리다.
    if (route.hash === '#inquiries') scrollToAnchor(inquirySec.value);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false; // 실패 경로(스켈레톤에 갇히지 않게)
  }
});

async function onDelete() {
  if (!window.confirm('이 상품을 삭제할까요?')) return;
  try {
    await deleteProduct(props.id);
    router.push('/products');
  } catch (e) {
    error.value = e.message;
  }
}
</script>

<template>
  <section class="page">
    <div v-if="error" class="alert-error mb-6">{{ error }}</div>

    <!-- 로딩: 텍스트 대신 스켈레톤으로 2단 레이아웃을 미리 잡는다 (DESIGN.md §5) -->
    <div v-if="loading" class="grid gap-8 lg:grid-cols-5">
      <div class="lg:col-span-3">
        <div class="skeleton aspect-square w-full rounded-card"></div>
        <div class="mt-8 space-y-2">
          <div class="skeleton h-5 w-24"></div>
          <div class="skeleton h-3 w-full"></div>
          <div class="skeleton h-3 w-4/5"></div>
        </div>
      </div>
      <div class="lg:col-span-2">
        <div class="card space-y-4 p-5">
          <div class="skeleton h-3 w-20"></div>
          <div class="skeleton h-6 w-2/3"></div>
          <div class="skeleton h-4 w-28"></div>
          <div class="skeleton h-9 w-40"></div>
          <div class="skeleton h-20 w-full"></div>
          <div class="skeleton h-10 w-full"></div>
        </div>
      </div>
    </div>

    <template v-else-if="product">
      <!-- 좌 3 : 우 2 — 좌측에 갤러리+설명(긴 콘텐츠), 우측은 구매 패널 하나로 묶어 따라오게 한다.
           우측이 짧으면 이미지가 만든 세로 공간이 텅 비어 보이므로 정보를 한 덩어리로 모았다. -->
      <div class="grid gap-8 lg:grid-cols-5">
        <!-- 좌: 이미지 갤러리 + 설명 -->
        <div class="lg:col-span-3">
          <div class="card aspect-square overflow-hidden bg-canvas">
            <img
              v-if="mainImage"
              :src="mainImage.mediumUrl"
              :alt="product.name"
              class="h-full w-full object-cover"
            />
            <div v-else class="flex h-full items-center justify-center text-5xl text-ink-400">🖼️</div>
          </div>

          <!-- 여러 장일 때만 썸네일 줄 -->
          <div v-if="images.length > 1" class="mt-3 flex flex-wrap gap-2">
            <button
              v-for="(img, i) in images"
              :key="img.id"
              type="button"
              class="h-16 w-16 overflow-hidden rounded-control border bg-canvas transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
              :class="i === selected ? 'border-brand-600' : 'border-line hover:border-ink-400'"
              :aria-label="`${i + 1}번째 이미지 보기`"
              :aria-current="i === selected"
              @click="selected = i"
            >
              <img :src="img.thumbUrl" :alt="`${product.name} ${i + 1}`" class="h-full w-full object-cover" />
            </button>
          </div>
        </div>

        <!-- 우: 구매 패널 — 한 덩어리 카드로 묶고 스크롤을 따라오게 한다 -->
        <div class="lg:col-span-2">
          <div class="card p-5 lg:sticky lg:top-20">
            <p class="muted">{{ product.categoryName }}</p>
            <h1 class="mt-1 text-xl font-bold tracking-tight text-ink-900">{{ product.name }}</h1>

            <button
              type="button"
              class="mt-3 inline-flex rounded-control transition-opacity hover:opacity-70 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
              aria-label="리뷰 보기"
              @click="scrollTo(reviewSec)"
            >
              <StarRating :model-value="product.averageRating" :count="product.reviewCount" />
            </button>

            <!-- 할인 중이면 정가(취소선) + 할인율을 함께 보여준다. 아니면 판매가만. -->
            <p v-if="hasDiscount(product)" class="muted mt-4 tabular-nums line-through">{{ priceText(product.listPrice) }}</p>
            <p class="tabular-nums text-ink-900" :class="hasDiscount(product) ? 'text-3xl font-semibold' : 'mt-4 text-3xl font-semibold'">
              {{ priceText(product.price) }}
              <span v-if="hasDiscount(product)" class="ml-2 text-xl font-semibold text-danger">{{ discountRate(product) }}%</span>
            </p>

            <!-- 상품 정보 요약 -->
            <dl class="mt-5 space-y-2 border-t border-line pt-5 text-sm">
              <div class="flex justify-between gap-4">
                <dt class="text-ink-500">재고</dt>
                <dd class="tabular-nums text-ink-900">{{ product.totalStock }}개</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-ink-500">상태</dt>
                <dd>
                  <span
                    class="badge"
                    :class="product.status === 'SELLING' ? 'badge-success'
                      : product.status === 'SOLD_OUT' ? 'badge-warning' : 'badge-neutral'"
                  >{{ statusText(product.status) }}</span>
                </dd>
              </div>
            </dl>

            <!-- 옵션 선택 — 옵션이 2개 이상일 때만. 단일(기본)이면 자동 선택돼 감춘다(2026-07-24 C-8). -->
            <div v-if="hasOptions" class="mt-5 border-t border-line pt-5">
              <p class="field-label mb-2">옵션</p>
              <div class="flex flex-col gap-2">
                <button
                  v-for="v in variants"
                  :key="v.id"
                  type="button"
                  class="flex items-center justify-between gap-3 rounded-lg border px-3 py-2 text-left transition-colors"
                  :class="[
                    selectedVariantId === v.id ? 'border-brand-600' : 'border-line hover:border-ink-400',
                    v.soldOut ? 'cursor-not-allowed opacity-50' : '',
                  ]"
                  :disabled="v.soldOut"
                  @click="selectedVariantId = v.id"
                >
                  <span class="text-sm text-ink-900">{{ v.name }}</span>
                  <span class="flex items-center gap-2">
                    <span v-if="v.priceDelta" class="muted tabular-nums">
                      {{ v.priceDelta > 0 ? '+' : '' }}{{ priceText(v.priceDelta) }}
                    </span>
                    <span class="text-sm font-medium tabular-nums text-ink-900">{{ priceText(v.price) }}</span>
                    <span v-if="v.soldOut" class="badge badge-warning">품절</span>
                  </span>
                </button>
              </div>
            </div>

            <!-- 구매 액션: 이 화면의 주 행동 -->
            <template v-if="isLoggedIn">
              <div class="mt-5 flex items-end justify-between gap-3 border-t border-line pt-5">
                <label class="field">
                  <span class="field-label">수량</span>
                  <DxNumberBox v-model:value="qty" :min="1" :width="90" />
                </label>
                <div class="text-right">
                  <p class="muted">합계</p>
                  <p class="text-xl font-bold tabular-nums text-ink-900">{{ priceText(lineTotal) }}</p>
                </div>
              </div>
              <div class="mt-4 flex gap-2">
                <button type="button" class="btn btn-primary flex-1" @click="onAddToCart">장바구니 담기</button>
                <!-- 찜은 품절이어도 할 수 있다 — 재입고를 기다리는 게 찜의 용도다 -->
                <WishlistButton :product-id="id" size="md" />
              </div>
              <p v-if="cartMsg" class="alert-success mt-3">{{ cartMsg }}</p>
            </template>
            <div v-else class="mt-5 border-t border-line pt-5">
              <p class="text-sm text-ink-500">구매하려면 로그인이 필요해요.</p>
              <!-- 비로그인에게도 하트는 보여준다. 누르면 로그인으로 보내므로 유입 경로가 된다. -->
              <WishlistButton :product-id="id" size="md" class="mt-3" />
            </div>

            <!-- 상품 전체가 품절이면 재입고 알림 신청 (B-9). 다시 들어오면(총재고 0→양수) 알림이 온다. -->
            <div v-if="soldOutAll" class="mt-4 rounded-lg border border-line bg-canvas p-4">
              <p class="mb-3 text-sm text-ink-700">지금은 품절이에요. 다시 들어오면 알려드릴게요.</p>
              <RestockButton :product-id="id" />
            </div>

            <!-- 보조 행동 -->
            <div class="mt-5 flex flex-wrap gap-2 border-t border-line pt-5">
              <button type="button" class="btn btn-secondary btn-sm" @click="router.push('/products')">목록</button>
              <template v-if="isAdmin">
                <button type="button" class="btn btn-secondary btn-sm" @click="router.push(`/products/${id}/edit`)">
                  수정
                </button>
                <button type="button" class="btn btn-danger btn-sm" @click="onDelete">삭제</button>
              </template>
            </div>
          </div>
        </div>
      </div>

      <!-- 하단 스티키 서브탭(무신사식) — 헤더(top-0, h-14=56px) 아래에 붙어 상세정보·리뷰·문의를 잇는다.
           탭 클릭 → 해당 섹션 스크롤, 스크롤 → 현재 섹션이 활성 표시(스크롤스파이). -->
      <nav
        class="sticky top-14 z-30 mt-10 flex gap-6 border-b border-line bg-surface/90 backdrop-blur"
        aria-label="상품 하위 정보"
      >
        <button type="button" class="-mb-px border-b-2 py-3 text-sm transition-colors" :class="tabClass('detail')" @click="scrollTo(detailSec)">상세정보</button>
        <button type="button" class="-mb-px border-b-2 py-3 text-sm transition-colors" :class="tabClass('reviews')" @click="scrollTo(reviewSec)">
          리뷰 <span class="tabular-nums">{{ product.reviewCount ?? 0 }}</span>
        </button>
        <button type="button" class="-mb-px border-b-2 py-3 text-sm transition-colors" :class="tabClass('inquiries')" @click="scrollTo(inquirySec)">문의</button>
      </nav>

      <section id="detail" ref="detailSec" class="scroll-mt-28 pt-8">
        <h2 class="section-title">상세정보</h2>
        <p
          v-if="product.description"
          class="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-ink-700"
        >{{ product.description }}</p>
        <p v-else class="muted mt-3">등록된 설명이 없어요.</p>
      </section>

      <!-- 섹션 사이는 여백만으로는 안 갈린다 → 구분선(border-t)으로 리뷰·문의 경계를 분명히 긋는다. -->
      <section id="reviews" ref="reviewSec" class="mt-12 scroll-mt-28 border-t border-line pt-12">
        <ProductReviews :product-id="id" />
      </section>
      <section id="inquiries" ref="inquirySec" class="mt-12 scroll-mt-28 border-t border-line pt-12">
        <ProductInquiries :product-id="id" />
      </section>

      <!--
        연관 상품(B-23) — **맨 아래**에 둔다. 리뷰·문의를 다 보고 "그래서 다른 건 뭐가 있지" 가
        나오는 자리라, 위에 두면 상품 정보를 읽기도 전에 이탈 경로를 먼저 보여주는 셈이 된다.
        ⚠ 섹션 구분선(border-t)은 리뷰·문의와 같은 규칙을 따른다.
      -->
      <div class="mt-12 border-t border-line pt-12">
        <RelatedProducts :product-id="id" :category-id="product.categoryId" />
      </div>
    </template>
  </section>
</template>
