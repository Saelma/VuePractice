<script setup>
/**
 * 주문서 — 장바구니와 주문 사이에 배송지를 받는 단계(커머스 표준 흐름).
 * 왼쪽에 배송지 입력, 오른쪽에 주문 요약을 고정해 "무엇을 얼마에 어디로" 한 화면에서 확인하게 한다.
 * 배송지는 주문에 스냅샷되므로(주문 후 기본 배송지를 바꿔도 과거 주문은 그대로), 여기 값이 곧 배송지다.
 */
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getCart } from '../api/cart';
import { checkout as apiCheckout } from '../api/order';
import { updateShippingAddress } from '../api/member';
import { priceText } from '../api/product';
import { addressFromUser, hasAddress, validateAddress, trimAddress } from '../api/shipping';
import { authState } from '../stores/auth';
import ItemThumb from '../components/ItemThumb.vue';
import ShippingAddressFields from '../components/ShippingAddressFields.vue';

const router = useRouter();
const cart = ref({ items: [], totalQuantity: 0, totalPrice: 0 });
const loading = ref(true);
const submitting = ref(false);
const error = ref('');

// 기본 배송지가 있으면 채워 둔다(없으면 빈 폼). 여기서 고쳐도 기본 배송지는 그대로다.
const form = reactive(addressFromUser(authState.user));
const hadDefault = hasAddress(authState.user);
// 기본 배송지가 없던 사람은 저장을 기본값으로 켜 둔다 — 다음 주문에서 다시 안 쓰게.
const saveAsDefault = ref(!hadDefault);

const unavailable = computed(() => cart.value.items.some((i) => !i.available));

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

async function submit() {
  error.value = '';
  if (!cart.value.items.length) {
    error.value = '장바구니가 비어 있어요.';
    return;
  }
  if (unavailable.value) {
    error.value = '구매할 수 없는 상품이 있어요. (품절/판매중지) 장바구니에서 빼주세요.';
    return;
  }
  const invalid = validateAddress(form);
  if (invalid) {
    error.value = invalid;
    return;
  }

  submitting.value = true;
  const address = trimAddress(form);
  try {
    // 기본 배송지 저장은 부가 기능이라 실패해도 주문을 막지 않는다(순서는 주문 먼저).
    const orderId = await apiCheckout(address);
    if (saveAsDefault.value) {
      await updateShippingAddress(address).catch(() => {});
    }
    router.push(`/orders/${orderId}`);
  } catch (e) {
    error.value = e.message;
    submitting.value = false;
  }
}
</script>

<template>
  <section class="page">
    <div class="mb-5">
      <h1 class="page-title">주문서</h1>
      <p class="muted mt-1">배송지를 확인하고 주문을 완료하세요.</p>
    </div>

    <div v-if="error" class="alert-error mb-5">{{ error }}</div>

    <div v-if="loading" class="grid items-start gap-6 lg:grid-cols-3">
      <div class="card space-y-3 p-5 lg:col-span-2">
        <div class="skeleton h-5 w-24"></div>
        <div class="skeleton h-9 w-full"></div>
        <div class="skeleton h-9 w-full"></div>
        <div class="skeleton h-9 w-2/3"></div>
      </div>
      <div class="card space-y-3 p-5">
        <div class="skeleton h-5 w-24"></div>
        <div class="skeleton h-3 w-full"></div>
        <div class="skeleton h-7 w-32"></div>
      </div>
    </div>

    <div v-else class="grid items-start gap-6 lg:grid-cols-3">
      <!-- 배송지 -->
      <div class="card p-5 lg:col-span-2">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <h2 class="section-title">배송지</h2>
          <span v-if="hadDefault" class="badge badge-neutral">기본 배송지 불러옴</span>
        </div>

        <div class="mt-4">
          <ShippingAddressFields :form="form" />
        </div>

        <label class="mt-4 flex items-center gap-2 text-sm text-ink-700">
          <input v-model="saveAsDefault" type="checkbox" class="h-4 w-4 accent-brand-600" />
          이 주소를 기본 배송지로 저장
        </label>
      </div>

      <!-- 주문 요약 -->
      <aside class="card p-5 lg:sticky lg:top-20">
        <h2 class="section-title">주문 요약</h2>

        <ul class="mt-4 space-y-3">
          <li v-for="item in cart.items" :key="item.productId" class="flex items-center gap-3">
            <ItemThumb :src="item.thumbUrl" :alt="item.name" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-ink-900">{{ item.name }}</p>
              <p class="muted mt-0.5 tabular-nums">{{ priceText(item.price) }} × {{ item.quantity }}</p>
            </div>
            <span class="text-sm tabular-nums text-ink-700">{{ priceText(item.lineTotal) }}</span>
          </li>
        </ul>

        <div class="mt-4 flex items-end justify-between gap-4 border-t border-line pt-4">
          <span class="text-sm font-medium text-ink-700">합계</span>
          <span class="text-2xl font-bold tabular-nums text-ink-900">{{ priceText(cart.totalPrice) }}</span>
        </div>

        <button type="button" class="btn btn-primary mt-5 w-full" :disabled="submitting" @click="submit">
          {{ submitting ? '주문 중…' : '결제하기' }}
        </button>
        <button type="button" class="btn btn-secondary mt-2 w-full" @click="router.push('/cart')">장바구니로</button>
      </aside>
    </div>
  </section>
</template>
