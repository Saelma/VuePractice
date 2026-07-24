<script setup>
/**
 * 장바구니 — 항목 리스트와 결제 요약을 시각적으로 분리한다(DESIGN.md §7).
 * 넓은 화면에서는 요약을 우측 컬럼에 고정해 합계·주문 버튼이 항상 눈에 닿게 한다.
 */
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getCart, updateCartItem, removeCartItem, clearCart } from '../api/cart';
import { priceText, hasDiscount, discountRate } from '../api/product';
import ItemThumb from '../components/ItemThumb.vue';
import EmptyState from '../components/EmptyState.vue';

const router = useRouter();
const cart = ref({ items: [], totalQuantity: 0, totalPrice: 0, shippingFee: 0, payAmount: 0, amountUntilFree: 0 });
const error = ref('');
const loading = ref(true);

async function load() {
  try {
    cart.value = await getCart();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
onMounted(load);

async function changeQty(item, next) {
  try {
    if (next < 1) {
      await removeCartItem(item.variantId);
    } else {
      await updateCartItem(item.variantId, next);
    }
    await load();
  } catch (e) {
    error.value = e.message;
  }
}

async function onRemove(item) {
  try {
    await removeCartItem(item.variantId);
    await load();
  } catch (e) {
    error.value = e.message;
  }
}

async function onClear() {
  if (!window.confirm('장바구니를 비울까요?')) return;
  try {
    await clearCart();
    await load();
  } catch (e) {
    error.value = e.message;
  }
}

// 여기서 바로 주문하지 않고 주문서로 넘긴다 — 배송지를 받아야 하기 때문(커머스 표준 흐름).
// 살 수 없는 항목 검사는 넘어가기 전에 해서, 주문서까지 갔다가 되돌아오는 일이 없게 한다.
function goCheckout() {
  error.value = '';
  if (!cart.value.items.length) return;
  if (cart.value.items.some((i) => !i.available)) {
    error.value = '구매할 수 없는 상품이 있어요. (품절/판매중지) 해당 항목을 빼주세요.';
    return;
  }
  router.push('/checkout');
}
</script>

<template>
  <section class="page">
    <div class="mb-5">
      <h1 class="page-title">장바구니</h1>
      <p v-if="!loading && cart.items.length" class="muted mt-1 tabular-nums">{{ cart.totalQuantity }}개 담김</p>
    </div>

    <div v-if="error" class="alert-error mb-5">{{ error }}</div>

    <!-- 로딩: 텍스트 대신 스켈레톤으로 레이아웃을 미리 잡는다 (DESIGN.md §5) -->
    <div v-if="loading" class="grid items-start gap-6 lg:grid-cols-3">
      <div class="card divide-y divide-line lg:col-span-2">
        <div v-for="n in 3" :key="n" class="flex items-center gap-4 px-5 py-4">
          <div class="flex-1 space-y-2">
            <div class="skeleton h-4 w-2/5"></div>
            <div class="skeleton h-3 w-24"></div>
          </div>
          <div class="skeleton h-8 w-24"></div>
          <div class="skeleton h-4 w-16"></div>
        </div>
      </div>
      <div class="card space-y-3 p-5">
        <div class="skeleton h-5 w-24"></div>
        <div class="skeleton h-3 w-full"></div>
        <div class="skeleton h-3 w-2/3"></div>
        <div class="skeleton h-7 w-32"></div>
        <div class="skeleton h-9 w-full"></div>
      </div>
    </div>

    <!-- 빈 상태: 회색 한 줄로 끝내지 않고 다음 행동을 준다 (DESIGN.md §5) -->
    <EmptyState v-else-if="!cart.items.length" icon="🛒" message="장바구니가 비어 있어요.">
      <button type="button" class="btn btn-primary" @click="router.push('/products')">상품 보러 가기</button>
    </EmptyState>

    <div v-else class="grid items-start gap-6 lg:grid-cols-3">
      <!-- 항목 -->
      <ul class="card divide-y divide-line lg:col-span-2">
        <li v-for="item in cart.items" :key="item.variantId" class="flex flex-wrap items-center gap-4 px-5 py-4">
          <ItemThumb :src="item.thumbUrl" :alt="item.name" />
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-ink-900">{{ item.name }}</p>
            <p v-if="item.optionName" class="muted truncate">{{ item.optionName }}</p>
            <p class="muted mt-1 tabular-nums">{{ priceText(item.price) }}</p>
            <span v-if="!item.available" class="badge badge-danger mt-2">재고 부족 · 판매중지</span>
          </div>

          <!-- 수량 조절: −가 1 미만이면 항목이 빠진다(기존 동작 유지) -->
          <div class="flex items-center rounded-control border border-line">
            <button
              type="button"
              class="h-8 w-8 rounded-control text-ink-500 transition-colors hover:bg-canvas hover:text-ink-900 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand-600"
              aria-label="수량 줄이기"
              @click="changeQty(item, item.quantity - 1)"
            >−</button>
            <span class="w-9 text-center text-sm tabular-nums text-ink-900">{{ item.quantity }}</span>
            <button
              type="button"
              class="h-8 w-8 rounded-control text-ink-500 transition-colors hover:bg-canvas hover:text-ink-900 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand-600"
              aria-label="수량 늘리기"
              @click="changeQty(item, item.quantity + 1)"
            >＋</button>
          </div>

          <div class="w-24 text-right text-sm font-semibold tabular-nums text-ink-900">
            {{ priceText(item.lineTotal) }}
          </div>

          <button type="button" class="btn btn-ghost" @click="onRemove(item)">삭제</button>
        </li>
      </ul>

      <!-- 결제 요약: 합계가 이 화면에서 가장 큰 숫자다 -->
      <aside class="card p-5 lg:sticky lg:top-20">
        <h2 class="section-title">결제 요약</h2>
        <dl class="mt-4 space-y-2 text-sm">
          <div class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">상품 수</dt>
            <dd class="tabular-nums text-ink-700">{{ cart.totalQuantity }}개</dd>
          </div>
          <div class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">상품 금액</dt>
            <dd class="tabular-nums text-ink-700">{{ priceText(cart.totalPrice) }}</dd>
          </div>
          <div class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">배송비</dt>
            <dd class="tabular-nums" :class="cart.shippingFee ? 'text-ink-700' : 'text-emerald-700'">
              {{ cart.shippingFee ? priceText(cart.shippingFee) : '무료' }}
            </dd>
          </div>
        </dl>

        <!-- 무료배송까지 남은 금액 — 얼마를 더 담아야 하는지 알려주는 게 "배송비 3,000원"보다 유용하다.
             서버가 계산해 내려준다(화면이 정책을 알 필요가 없다). -->
        <p v-if="cart.amountUntilFree > 0" class="muted mt-3">
          {{ priceText(cart.amountUntilFree) }} 더 담으면 <strong class="text-ink-700">무료배송</strong>
        </p>

        <div class="mt-4 flex items-end justify-between gap-4 border-t border-line pt-4">
          <span class="text-sm font-medium text-ink-700">결제 금액</span>
          <span class="text-2xl font-bold tabular-nums text-ink-900">{{ priceText(cart.payAmount) }}</span>
        </div>

        <button type="button" class="btn btn-primary mt-5 w-full" @click="goCheckout">주문하기</button>
        <button type="button" class="btn btn-secondary mt-2 w-full" @click="onClear">장바구니 비우기</button>
      </aside>
    </div>
  </section>
</template>
