<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxButton } from 'devextreme-vue/button';
import { getOrder, cancelOrder, orderStatusText } from '../api/order';
import { priceText } from '../api/product';

const props = defineProps({ id: { type: String, required: true } });
const router = useRouter();

const order = ref(null);
const error = ref('');
const loading = ref(true);

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

async function onCancel() {
  if (!window.confirm('주문을 취소할까요? (재고가 복원됩니다)')) return;
  try {
    await cancelOrder(props.id);
    await load();
  } catch (e) {
    error.value = e.message;
  }
}

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}
</script>

<template>
  <section class="mx-auto max-w-2xl p-6">
    <div v-if="error" class="mb-4 rounded bg-red-50 p-3 text-red-600">{{ error }}</div>
    <div v-else-if="loading" class="text-slate-500">불러오는 중…</div>

    <article v-else-if="order" class="rounded-lg border bg-white p-6">
      <div class="mb-4 flex items-center justify-between border-b pb-3">
        <div>
          <h2 class="text-lg font-bold text-slate-800">주문 상세</h2>
          <div class="text-sm text-slate-500">{{ fmt(order.createdAt) }}</div>
        </div>
        <span
          class="rounded px-2 py-1 text-sm"
          :class="order.status === 'CANCELLED' ? 'bg-slate-100 text-slate-500' : 'bg-blue-50 text-blue-600'"
        >{{ orderStatusText(order.status) }}</span>
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

      <div class="mt-6 flex gap-2">
        <DxButton text="주문 목록" styling-mode="outlined" @click="router.push('/orders')" />
        <DxButton
          v-if="order.status === 'ORDERED'"
          text="주문 취소"
          type="danger"
          styling-mode="contained"
          @click="onCancel"
        />
      </div>
    </article>
  </section>
</template>
