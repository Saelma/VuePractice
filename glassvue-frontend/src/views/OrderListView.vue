<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxButton } from 'devextreme-vue/button';
import { fetchOrders, orderStatusText, orderStatusClass } from '../api/order';
import { priceText } from '../api/product';

const router = useRouter();
const orders = ref([]);
const pageInfo = ref({ page: 0, totalPages: 0, last: true });
const error = ref('');
const loading = ref(true);

async function load(p = 0) {
  loading.value = true;
  error.value = '';
  try {
    // 응답이 List → PageResponse로 바뀌었다(2026-07-20 묶음 3). content를 꺼내 써야 한다.
    const data = await fetchOrders({ page: p, size: 10 });
    orders.value = data.content;
    pageInfo.value = { page: data.page, totalPages: data.totalPages, last: data.last };
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

onMounted(() => load(0));

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}
function summary(o) {
  const first = o.items[0]?.productName || '';
  return first + (o.items.length > 1 ? ` 외 ${o.items.length - 1}건` : '');
}
</script>

<template>
  <section class="mx-auto max-w-3xl p-6">
    <h2 class="mb-4 text-xl font-semibold text-slate-800">주문 내역</h2>
    <div v-if="error" class="mb-4 rounded bg-red-50 p-3 text-red-600">{{ error }}</div>
    <div v-else-if="loading" class="text-slate-500">불러오는 중…</div>

    <template v-else>
      <div v-if="!orders.length" class="rounded-lg border bg-white p-8 text-center text-slate-500">
        주문 내역이 없어요.
        <div class="mt-3">
          <DxButton text="상품 보러가기" type="default" styling-mode="contained" @click="router.push('/products')" />
        </div>
      </div>

      <ul v-else class="divide-y rounded-lg border bg-white">
        <li
          v-for="o in orders"
          :key="o.id"
          class="flex cursor-pointer items-center gap-4 px-4 py-4 hover:bg-slate-50"
          @click="router.push(`/orders/${o.id}`)"
        >
          <div class="flex-1">
            <div class="font-medium text-slate-800">{{ summary(o) }}</div>
            <div class="text-sm text-slate-500">{{ fmt(o.createdAt) }}</div>
          </div>
          <span class="rounded px-2 py-0.5 text-sm" :class="orderStatusClass(o.status)">{{ orderStatusText(o.status) }}</span>
          <div class="w-24 text-right font-semibold text-slate-800">{{ priceText(o.totalPrice) }}</div>
        </li>
      </ul>

      <div v-if="pageInfo.totalPages > 1" class="mt-3 flex items-center justify-center gap-3 text-sm">
        <button class="text-slate-500 disabled:text-slate-300" :disabled="pageInfo.page === 0" @click="load(pageInfo.page - 1)">이전</button>
        <span class="text-slate-500">{{ pageInfo.page + 1 }} / {{ pageInfo.totalPages }}</span>
        <button class="text-slate-500 disabled:text-slate-300" :disabled="pageInfo.last" @click="load(pageInfo.page + 1)">다음</button>
      </div>
    </template>
  </section>
</template>
