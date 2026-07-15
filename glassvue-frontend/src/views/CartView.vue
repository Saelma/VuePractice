<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxButton } from 'devextreme-vue/button';
import { getCart, updateCartItem, removeCartItem, clearCart } from '../api/cart';
import { checkout as apiCheckout } from '../api/order';
import { priceText } from '../api/product';

const router = useRouter();
const cart = ref({ items: [], totalQuantity: 0, totalPrice: 0 });
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
      await removeCartItem(item.productId);
    } else {
      await updateCartItem(item.productId, next);
    }
    await load();
  } catch (e) {
    error.value = e.message;
  }
}

async function onRemove(item) {
  try {
    await removeCartItem(item.productId);
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

async function checkout() {
  error.value = '';
  if (!cart.value.items.length) return;
  if (cart.value.items.some((i) => !i.available)) {
    error.value = '구매할 수 없는 상품이 있어요. (품절/판매중지) 해당 항목을 빼주세요.';
    return;
  }
  if (!window.confirm('주문하시겠어요?')) return;
  try {
    const orderId = await apiCheckout();
    router.push(`/orders/${orderId}`);
  } catch (e) {
    error.value = e.message;
  }
}
</script>

<template>
  <section class="mx-auto max-w-3xl p-6">
    <h2 class="mb-4 text-xl font-semibold text-slate-800">장바구니</h2>
    <div v-if="error" class="mb-4 rounded bg-red-50 p-3 text-red-600">{{ error }}</div>
    <div v-else-if="loading" class="text-slate-500">불러오는 중…</div>

    <template v-else>
      <div v-if="!cart.items.length" class="rounded-lg border bg-white p-8 text-center text-slate-500">
        장바구니가 비어 있어요.
        <div class="mt-3">
          <DxButton text="상품 보러가기" type="default" styling-mode="contained" @click="router.push('/products')" />
        </div>
      </div>

      <template v-else>
        <ul class="divide-y rounded-lg border bg-white">
          <li v-for="item in cart.items" :key="item.productId" class="flex items-center gap-4 px-4 py-3">
            <div class="flex-1">
              <div class="font-medium text-slate-800">{{ item.name }}</div>
              <div class="text-sm text-slate-500">
                {{ priceText(item.price) }}
                <span v-if="!item.available" class="ml-2 text-red-500">· 재고 부족/판매중지</span>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <button class="h-7 w-7 rounded border hover:bg-slate-50" @click="changeQty(item, item.quantity - 1)">−</button>
              <span class="w-8 text-center">{{ item.quantity }}</span>
              <button class="h-7 w-7 rounded border hover:bg-slate-50" @click="changeQty(item, item.quantity + 1)">＋</button>
            </div>
            <div class="w-24 text-right font-semibold text-slate-800">{{ priceText(item.lineTotal) }}</div>
            <button class="text-sm text-slate-400 hover:text-red-500" @click="onRemove(item)">삭제</button>
          </li>
        </ul>

        <div class="mt-4 flex items-center justify-between rounded-lg border bg-white px-4 py-4">
          <span class="text-slate-600">총 {{ cart.totalQuantity }}개</span>
          <span class="text-lg font-bold text-slate-800">{{ priceText(cart.totalPrice) }}</span>
        </div>

        <div class="mt-4 flex justify-end gap-2">
          <DxButton text="비우기" styling-mode="outlined" @click="onClear" />
          <DxButton text="주문하기" type="default" styling-mode="contained" @click="checkout" />
        </div>
      </template>
    </template>
  </section>
</template>
