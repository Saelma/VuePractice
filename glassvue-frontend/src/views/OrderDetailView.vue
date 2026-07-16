<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxButton } from 'devextreme-vue/button';
import { getOrder, payOrder, shipOrder, cancelOrder, orderStatusText, orderStatusClass } from '../api/order';
import { priceText } from '../api/product';
import { authState } from '../stores/auth';

const props = defineProps({ id: { type: String, required: true } });
const router = useRouter();

const order = ref(null);
const error = ref('');
const loading = ref(true);
const isAdmin = computed(() => authState.user?.role === 'ADMIN');

async function load() {
  loading.value = true;
  try {
    order.value = await getOrder(props.id);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
onMounted(load);

async function act(fn, confirmMsg) {
  if (confirmMsg && !window.confirm(confirmMsg)) return;
  error.value = '';
  try {
    await fn(props.id);
    await load();
  } catch (e) {
    error.value = e.message;
  }
}
const onPay = () => act(payOrder, '결제를 진행할까요? (실제 결제 없이 상태만 결제완료로)');
const onShip = () => act(shipOrder, '이 주문을 발송 처리할까요?');
const onCancel = () => act(cancelOrder, '주문을 취소할까요? (재고가 복원됩니다)');

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}
</script>

<template>
  <section class="mx-auto max-w-2xl p-6">
    <div v-if="error" class="mb-4 rounded bg-red-50 p-3 text-red-600">{{ error }}</div>
    <div v-else-if="loading" class="text-slate-500">불러오는 중…</div>

    <article v-else-if="order" class="rounded-lg border bg-white p-6">
      <div class="mb-4 flex items-start justify-between border-b pb-3">
        <div>
          <h2 class="text-lg font-bold text-slate-800">주문 상세</h2>
          <div class="text-sm text-slate-500">주문 {{ fmt(order.createdAt) }}</div>
          <div v-if="order.paidAt" class="text-sm text-slate-500">결제 {{ fmt(order.paidAt) }}</div>
          <div v-if="order.shippedAt" class="text-sm text-slate-500">발송 {{ fmt(order.shippedAt) }}</div>
        </div>
        <span class="rounded px-2 py-1 text-sm" :class="orderStatusClass(order.status)">
          {{ orderStatusText(order.status) }}
        </span>
      </div>

      <ul class="divide-y">
        <li v-for="item in order.items" :key="item.productId" class="flex items-center gap-4 py-3">
          <div class="flex-1">
            <div class="text-slate-800">{{ item.productName }}</div>
            <div class="text-sm text-slate-500">{{ priceText(item.price) }} × {{ item.quantity }}</div>
          </div>
          <div class="font-semibold text-slate-800">{{ priceText(item.lineTotal) }}</div>
        </li>
      </ul>

      <div class="mt-4 flex items-center justify-between border-t pt-4">
        <span class="text-slate-600">합계</span>
        <span class="text-lg font-bold text-slate-800">{{ priceText(order.totalPrice) }}</span>
      </div>

      <div class="mt-6 flex flex-wrap gap-2">
        <DxButton text="주문 목록" styling-mode="outlined" @click="router.push('/orders')" />

        <!-- 구매자 액션 -->
        <template v-if="!isAdmin">
          <DxButton
            v-if="order.status === 'ORDERED'"
            text="결제하기" type="success" styling-mode="contained" @click="onPay"
          />
          <DxButton
            v-if="order.status === 'ORDERED' || order.status === 'PAID'"
            text="주문 취소" type="danger" styling-mode="contained" @click="onCancel"
          />
        </template>

        <!-- 관리자 액션: 결제완료 주문 발송 처리 -->
        <DxButton
          v-if="isAdmin && order.status === 'PAID'"
          text="발송 처리" type="default" styling-mode="contained" @click="onShip"
        />
      </div>
    </article>
  </section>
</template>
