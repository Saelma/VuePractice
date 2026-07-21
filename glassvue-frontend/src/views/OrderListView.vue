<script setup>
/**
 * 주문 내역 — 표가 아니라 읽기 좋은 행 리스트(DESIGN.md §7).
 * 상품 요약이 먼저 읽히고 주문번호·날짜는 보조로 물러난다. 금액과 상태만 강조한다.
 */
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
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
function shortId(id) {
  return id ? `#${String(id).slice(0, 8)}` : '';
}
</script>

<template>
  <section class="page">
    <h1 class="page-title mb-5">주문 내역</h1>

    <div v-if="error" class="alert-error mb-5">{{ error }}</div>

    <!-- 로딩: 스켈레톤으로 목록 레이아웃을 미리 잡는다 (DESIGN.md §5) -->
    <div v-if="loading" class="card divide-y divide-line">
      <div v-for="n in 5" :key="n" class="flex items-center justify-between gap-4 px-5 py-4">
        <div class="flex-1 space-y-2">
          <div class="skeleton h-4 w-2/5"></div>
          <div class="skeleton h-3 w-40"></div>
        </div>
        <div class="skeleton h-5 w-16"></div>
        <div class="skeleton h-4 w-20"></div>
      </div>
    </div>

    <!-- 빈 상태 -->
    <div v-else-if="!orders.length" class="flex flex-col items-center gap-3 py-16 text-center">
      <span class="text-4xl">🧾</span>
      <p class="text-sm text-ink-500">아직 주문 내역이 없어요.</p>
      <button type="button" class="btn btn-primary" @click="router.push('/products')">상품 보러 가기</button>
    </div>

    <!-- 목록 -->
    <ul v-else class="card divide-y divide-line">
      <li v-for="o in orders" :key="o.id">
        <button
          type="button"
          class="flex w-full flex-wrap items-center gap-4 px-5 py-4 text-left transition-colors hover:bg-canvas focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand-600"
          @click="router.push(`/orders/${o.id}`)"
        >
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-ink-900">{{ summary(o) }}</p>
            <p class="muted mt-1 tabular-nums">{{ shortId(o.id) }} · {{ fmt(o.createdAt) }}</p>
          </div>
          <span class="badge shrink-0" :class="orderStatusClass(o.status)">{{ orderStatusText(o.status) }}</span>
          <span class="w-24 shrink-0 text-right text-sm font-semibold tabular-nums text-ink-900">
            {{ priceText(o.totalPrice) }}
          </span>
        </button>
      </li>
    </ul>

    <!-- 페이지 이동 -->
    <div v-if="!loading && pageInfo.totalPages > 1" class="mt-8 flex items-center justify-center gap-4">
      <button type="button" class="btn btn-secondary" :disabled="pageInfo.page === 0" @click="load(pageInfo.page - 1)">
        이전
      </button>
      <span class="text-sm tabular-nums text-ink-500">{{ pageInfo.page + 1 }} / {{ pageInfo.totalPages }}</span>
      <button type="button" class="btn btn-secondary" :disabled="pageInfo.last" @click="load(pageInfo.page + 1)">
        다음
      </button>
    </div>
  </section>
</template>
