<script setup>
/**
 * 주문 상세 — 읽는 화면이라 폭을 좁히고(page-narrow) 주문 정보 / 품목 / 액션을 세 덩어리로 나눈다(DESIGN.md §4·§7).
 */
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getOrder, payOrder, shipOrder, cancelOrder, orderStatusText, orderStatusClass } from '../api/order';
import { priceText } from '../api/product';
import { authState } from '../stores/auth';

const props = defineProps({ id: { type: String, required: true } });
const router = useRouter();

const order = ref(null);
const error = ref('');
const loading = ref(true);
const isAdmin = computed(() => authState.user?.role === 'ADMIN');
// 결제·취소는 역할이 아니라 **소유 여부**로 갈린다 — 백엔드 pay/cancel이 findByIdAndMemberId로
// 본인만 허용하는 것과 같은 규칙. 관리자도 직접 구매하므로 !isAdmin으로 가르면
// 자기 주문인데 버튼이 사라진다(2026-07-20 실제로 발생한 버그).
const isMine = computed(() => !!order.value && order.value.memberId === authState.user?.id);

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
const shortId = computed(() => (order.value?.id ? `#${String(order.value.id).slice(0, 8)}` : ''));
</script>

<template>
  <section class="page-narrow">
    <div v-if="error" class="alert-error mb-5">{{ error }}</div>

    <!-- 로딩: 스켈레톤으로 레이아웃을 미리 잡는다 (DESIGN.md §5) -->
    <div v-if="loading" class="space-y-6">
      <div class="space-y-2">
        <div class="skeleton h-7 w-32"></div>
        <div class="skeleton h-3 w-24"></div>
      </div>
      <div class="card space-y-3 p-5">
        <div class="skeleton h-3 w-40"></div>
        <div class="skeleton h-3 w-32"></div>
      </div>
      <div class="card divide-y divide-line">
        <div v-for="n in 3" :key="n" class="flex items-center gap-4 px-5 py-4">
          <div class="flex-1 space-y-2">
            <div class="skeleton h-4 w-2/5"></div>
            <div class="skeleton h-3 w-24"></div>
          </div>
          <div class="skeleton h-4 w-20"></div>
        </div>
      </div>
    </div>

    <template v-else-if="order">
      <!-- 머리: 제목 + 상태 -->
      <div class="mb-5 flex items-start justify-between gap-4">
        <div>
          <h1 class="page-title">주문 상세</h1>
          <p class="muted mt-1 tabular-nums">{{ shortId }}</p>
        </div>
        <span class="badge shrink-0" :class="orderStatusClass(order.status)">{{ orderStatusText(order.status) }}</span>
      </div>

      <!-- 주문 정보 -->
      <div class="card p-5">
        <dl class="grid gap-3 text-sm sm:grid-cols-2">
          <!-- 관리자가 남의 주문을 볼 때 "누구 주문인지" 표시(주문 시점 스냅샷). 본인 주문엔 불필요. -->
          <div v-if="isAdmin && !isMine" class="flex flex-col gap-1">
            <dt class="muted">구매자</dt>
            <dd class="font-medium text-ink-900">{{ order.buyerNickname }}</dd>
          </div>
          <div class="flex flex-col gap-1">
            <dt class="muted">주문</dt>
            <dd class="text-ink-700 tabular-nums">{{ fmt(order.createdAt) }}</dd>
          </div>
          <div v-if="order.paidAt" class="flex flex-col gap-1">
            <dt class="muted">결제</dt>
            <dd class="text-ink-700 tabular-nums">{{ fmt(order.paidAt) }}</dd>
          </div>
          <div v-if="order.shippedAt" class="flex flex-col gap-1">
            <dt class="muted">발송</dt>
            <dd class="text-ink-700 tabular-nums">{{ fmt(order.shippedAt) }}</dd>
          </div>
        </dl>
      </div>

      <!-- 품목 + 합계 -->
      <div class="card mt-6">
        <h2 class="section-title border-b border-line px-5 py-4">주문 품목</h2>
        <ul class="divide-y divide-line">
          <li v-for="item in order.items" :key="item.productId" class="flex items-center gap-4 px-5 py-4">
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-ink-900">{{ item.productName }}</p>
              <p class="muted mt-1 tabular-nums">{{ priceText(item.price) }} × {{ item.quantity }}</p>
            </div>
            <span class="text-sm font-semibold tabular-nums text-ink-900">{{ priceText(item.lineTotal) }}</span>
          </li>
        </ul>
        <div class="flex items-end justify-between gap-4 border-t border-line px-5 py-4">
          <span class="text-sm font-medium text-ink-700">합계</span>
          <span class="text-2xl font-bold tabular-nums text-ink-900">{{ priceText(order.totalPrice) }}</span>
        </div>
      </div>

      <!-- 액션: 조건은 그대로, 스타일만 정리 -->
      <div class="mt-6 flex flex-wrap items-center justify-between gap-2">
        <button type="button" class="btn btn-secondary" @click="router.push('/orders')">주문 목록</button>

        <div class="flex flex-wrap gap-2">
          <!-- 구매자 액션: 본인 주문일 때만(관리자도 본인 주문이면 보인다) -->
          <template v-if="isMine">
            <button
              v-if="order.status === 'ORDERED' || order.status === 'PAID'"
              type="button"
              class="btn btn-danger"
              @click="onCancel"
            >주문 취소</button>
            <button
              v-if="order.status === 'ORDERED'"
              type="button"
              class="btn btn-primary"
              @click="onPay"
            >결제하기</button>
          </template>

          <!-- 관리자 액션: 결제완료 주문 발송 처리 -->
          <button
            v-if="isAdmin && order.status === 'PAID'"
            type="button"
            class="btn btn-primary"
            @click="onShip"
          >발송 처리</button>
        </div>
      </div>
    </template>
  </section>
</template>
