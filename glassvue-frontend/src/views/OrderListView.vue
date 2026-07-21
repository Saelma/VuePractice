<script setup>
/**
 * 주문 내역 — 커머스 표준 구성(DESIGN.md §7):
 *   상태 탭(전체/결제대기/결제완료/발송완료/취소) + **주문 단위 카드**.
 * 한 줄 요약("A 외 2건") 대신 카드 안에 품목을 실제로 펼쳐 보여준다 — 무엇을 샀는지가 목록의 존재 이유다.
 * 상태 필터는 서버(`GET /api/orders?status=`)가 지원하므로 클라이언트에서 거르지 않는다.
 */
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { fetchOrders, orderStatusText, orderStatusClass, ORDER_STATUS_TEXT } from '../api/order';
import { priceText } from '../api/product';

const router = useRouter();
const orders = ref([]);
const pageInfo = ref({ page: 0, totalPages: 0, last: true });
const error = ref('');
const loading = ref(true);
const status = ref(null); // null = 전체

const TABS = [{ value: null, text: '전체' },
  ...Object.entries(ORDER_STATUS_TEXT).map(([value, text]) => ({ value, text }))];

async function load(p = 0) {
  loading.value = true;
  error.value = '';
  try {
    // 응답이 List → PageResponse로 바뀌었다(2026-07-20 묶음 3). content를 꺼내 써야 한다.
    const data = await fetchOrders({ status: status.value, page: p, size: 10 });
    orders.value = data.content;
    pageInfo.value = { page: data.page, totalPages: data.totalPages, last: data.last };
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
onMounted(() => load(0));

function pickTab(v) {
  status.value = v;
  load(0);
}

const fmt = (v) => (v ? new Date(v).toLocaleDateString('ko-KR') : '');
const shortId = (id) => (id ? `#${String(id).slice(0, 8)}` : '');
</script>

<template>
  <section class="page">
    <h1 class="page-title mb-5">주문 내역</h1>

    <!-- 상태 탭 -->
    <div class="mb-5 flex flex-wrap gap-1 border-b border-line">
      <button
        v-for="t in TABS"
        :key="t.value ?? 'all'"
        type="button"
        class="-mb-px border-b-2 px-3 py-2 text-sm transition-colors focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand-600"
        :class="status === t.value
          ? 'border-brand-600 font-medium text-ink-900'
          : 'border-transparent text-ink-500 hover:text-ink-900'"
        :aria-current="status === t.value"
        @click="pickTab(t.value)"
      >{{ t.text }}</button>
    </div>

    <div v-if="error" class="alert-error mb-5">{{ error }}</div>

    <!-- 로딩: 주문 카드 모양 스켈레톤 -->
    <div v-if="loading" class="space-y-4">
      <div v-for="n in 3" :key="n" class="card p-5">
        <div class="flex justify-between gap-4">
          <div class="skeleton h-3 w-40"></div>
          <div class="skeleton h-5 w-16"></div>
        </div>
        <div class="mt-4 space-y-2">
          <div class="skeleton h-4 w-2/5"></div>
          <div class="skeleton h-3 w-24"></div>
        </div>
        <div class="mt-4 flex justify-end"><div class="skeleton h-6 w-28"></div></div>
      </div>
    </div>

    <!-- 빈 상태: 탭 때문에 빈 것과 정말 없는 것을 구분 -->
    <div v-else-if="!orders.length" class="flex flex-col items-center gap-3 py-16 text-center">
      <span class="text-4xl">🧾</span>
      <p class="text-sm text-ink-500">
        {{ status ? `‘${orderStatusText(status)}’ 상태인 주문이 없어요.` : '아직 주문 내역이 없어요.' }}
      </p>
      <button v-if="status" type="button" class="btn btn-secondary" @click="pickTab(null)">전체 보기</button>
      <button v-else type="button" class="btn btn-primary" @click="router.push('/products')">상품 보러 가기</button>
    </div>

    <!-- 주문 카드 -->
    <ul v-else class="space-y-4">
      <li v-for="o in orders" :key="o.id" class="card">
        <!-- 카드 머리: 주문일 · 주문번호 / 상태 -->
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-3">
          <div class="flex flex-wrap items-center gap-2">
            <span class="text-sm font-medium tabular-nums text-ink-900">{{ fmt(o.createdAt) }}</span>
            <span class="muted tabular-nums">{{ shortId(o.id) }}</span>
          </div>
          <span class="badge" :class="orderStatusClass(o.status)">{{ orderStatusText(o.status) }}</span>
        </div>

        <!-- 품목: 무엇을 샀는지 실제로 보여준다 -->
        <ul class="divide-y divide-line">
          <li v-for="item in o.items" :key="item.productId" class="flex items-center gap-4 px-5 py-3">
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm text-ink-900">{{ item.productName }}</p>
              <p class="muted mt-0.5 tabular-nums">{{ priceText(item.price) }} × {{ item.quantity }}</p>
            </div>
            <span class="shrink-0 text-sm tabular-nums text-ink-700">{{ priceText(item.lineTotal) }}</span>
          </li>
        </ul>

        <!-- 카드 발: 합계 + 액션 -->
        <div class="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-3">
          <div class="flex items-baseline gap-2">
            <span class="muted">합계</span>
            <span class="text-lg font-bold tabular-nums text-ink-900">{{ priceText(o.totalPrice) }}</span>
          </div>
          <button type="button" class="btn btn-secondary btn-sm" @click="router.push(`/orders/${o.id}`)">
            주문 상세
          </button>
        </div>
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
