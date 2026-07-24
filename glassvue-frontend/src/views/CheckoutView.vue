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
import { fetchMyCoupons } from '../api/coupon';
import { addressFromUser, hasAddress, validateAddress, trimAddress } from '../api/shipping';
import { fetchAddresses, addressToForm, addressSummary } from '../api/address';
import { authState } from '../stores/auth';
import ItemThumb from '../components/ItemThumb.vue';
import ShippingAddressFields from '../components/ShippingAddressFields.vue';

const router = useRouter();
const cart = ref({ items: [], totalQuantity: 0, totalPrice: 0, shippingFee: 0, payAmount: 0, amountUntilFree: 0 });

/**
 * 쿠폰 — 서버가 "지금 얼마 깎이는지"(discountPreview)와 "쓸 수 있는지"(usable)를 계산해 준다.
 * 화면은 할인 규칙(정액/정률·상한·최소주문금액)을 몰라도 된다.
 */
const coupons = ref([]);
const selectedCouponId = ref(null);
const selectedCoupon = computed(() => coupons.value.find((c) => c.id === selectedCouponId.value) || null);
const couponDiscount = computed(() => selectedCoupon.value?.discountPreview ?? 0);
// 배송비는 **할인 전** 상품합계로 정해지므로 쿠폰을 써도 안 바뀐다.
const payAmount = computed(() => cart.value.totalPrice - couponDiscount.value + cart.value.shippingFee);
const loading = ref(true);
const submitting = ref(false);
const error = ref('');

// 기본 배송지가 있으면 채워 둔다(없으면 빈 폼). 여기서 고쳐도 저장된 주소는 그대로다.
const form = reactive(addressFromUser(authState.user));
const hadDefault = hasAddress(authState.user);
// 기본 배송지가 없던 사람은 저장을 기본값으로 켜 둔다 — 다음 주문에서 다시 안 쓰게.
const saveAsDefault = ref(!hadDefault);

/**
 * 주소록(2026-07-24) — 저장해 둔 배송지를 골라 폼에 붓는다.
 * 고른 뒤에도 폼은 계속 고칠 수 있고, 고친 값은 **이번 주문에만** 적용된다
 * (주문에는 스냅샷으로 들어가고 주소록은 안 바뀐다). 주소록에도 반영하려면 아래 체크를 켠다.
 */
const addresses = ref([]);
const selectedAddressId = ref(null);

function pick(a) {
  selectedAddressId.value = a.id;
  Object.assign(form, addressToForm(a));
}

const unavailable = computed(() => cart.value.items.some((i) => !i.available));

async function load() {
  try {
    cart.value = await getCart();
    // 쿠폰은 상품합계를 알아야 "얼마 깎이는지"를 서버가 계산해 줄 수 있어 장바구니 뒤에 부른다.
    // 실패해도 주문은 되어야 하므로 막지 않는다(기본 배송지 저장과 같은 판단).
    coupons.value = await fetchMyCoupons(cart.value.totalPrice).catch(() => []);
    // 주소록도 마찬가지 — 못 읽어도 배송지를 직접 입력해 주문할 수 있어야 한다.
    addresses.value = await fetchAddresses().catch(() => []);
    // 기본 배송지를 미리 골라 둔다. 폼은 이미 같은 값으로 채워져 있으므로(authState) 표시만 맞추는 셈이다.
    const preset = addresses.value.find((a) => a.isDefault);
    if (preset) selectedAddressId.value = preset.id;
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
    // 쿠폰 id만 보낸다 — 할인액은 서버가 다시 계산한다(본문으로 받으면 위조 가능).
    const orderId = await apiCheckout({ ...address, memberCouponId: selectedCouponId.value });
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

        <!-- 저장해 둔 주소 고르기. 주소록이 비어 있으면 이 블록 자체가 안 나온다. -->
        <div v-if="addresses.length" class="mt-4 flex flex-wrap gap-2">
          <button
            v-for="a in addresses"
            :key="a.id"
            type="button"
            class="rounded-lg border px-3 py-2 text-left"
            :class="selectedAddressId === a.id ? 'border-brand-600' : 'border-line hover:border-ink-400'"
            @click="pick(a)"
          >
            <span class="flex items-center gap-2">
              <span class="text-sm font-medium text-ink-900">{{ a.alias }}</span>
              <span v-if="a.isDefault" class="badge badge-neutral">기본</span>
            </span>
            <span class="muted mt-0.5 block">{{ a.recipient }} · {{ addressSummary(a) }}</span>
          </button>
        </div>
        <p v-if="addresses.length" class="muted mt-2">
          고른 뒤 아래에서 고쳐도 됩니다. 고친 값은 이번 주문에만 적용됩니다.
        </p>

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

        <!-- 쿠폰 선택. 서버가 쿠폰마다 usable·discountPreview·reason을 계산해 주므로
             화면은 못 쓰는 이유를 그대로 보여주기만 하면 된다. -->
        <div v-if="coupons.length" class="mt-4 border-t border-line pt-4">
          <span class="muted mb-2 block">쿠폰</span>
          <label class="flex items-center gap-2 py-1 text-sm">
            <input v-model="selectedCouponId" type="radio" :value="null" />
            <span class="text-ink-700">사용 안 함</span>
          </label>
          <label
            v-for="c in coupons"
            :key="c.id"
            class="flex items-center gap-2 py-1 text-sm"
            :class="c.usable ? '' : 'opacity-50'"
          >
            <input v-model="selectedCouponId" type="radio" :value="c.id" :disabled="!c.usable" />
            <span class="min-w-0 flex-1 truncate text-ink-900">{{ c.name }}</span>
            <span v-if="c.usable" class="shrink-0 tabular-nums text-danger">−{{ priceText(c.discountPreview) }}</span>
            <span v-else class="muted shrink-0">{{ c.reason }}</span>
          </label>
        </div>

        <dl class="mt-4 space-y-2 border-t border-line pt-4 text-sm">
          <div class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">상품 금액</dt>
            <dd class="tabular-nums text-ink-700">{{ priceText(cart.totalPrice) }}</dd>
          </div>
          <div v-if="couponDiscount > 0" class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">쿠폰 할인</dt>
            <dd class="tabular-nums text-danger">−{{ priceText(couponDiscount) }}</dd>
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
          <span class="text-2xl font-bold tabular-nums text-ink-900">{{ priceText(payAmount) }}</span>
        </div>

        <button type="button" class="btn btn-primary mt-5 w-full" :disabled="submitting" @click="submit">
          {{ submitting ? '주문 중…' : '결제하기' }}
        </button>
        <button type="button" class="btn btn-secondary mt-2 w-full" @click="router.push('/cart')">장바구니로</button>
      </aside>
    </div>
  </section>
</template>
