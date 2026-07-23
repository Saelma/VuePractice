<script setup>
/**
 * 주문 상세 — 읽는 화면이라 폭을 좁히고(page-narrow) 주문 정보 / 품목 / 액션을 세 덩어리로 나눈다(DESIGN.md §4·§7).
 */
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import {
  getOrder, payOrder, shipOrder, deliverOrder, cancelOrder,
  orderStatusText, orderStatusClass, DELIVERY_CARRIERS,
} from '../api/order';
import { priceText } from '../api/product';
import ItemThumb from '../components/ItemThumb.vue';
import { addressText } from '../api/shipping';
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
const onCancel = () => act(cancelOrder, '주문을 취소할까요? (재고가 복원됩니다)');
const onDeliver = () => act(deliverOrder, '이 주문을 배송완료로 처리할까요?');

/**
 * 발송 처리는 운송장 입력이 필요해 `window.confirm`으로 처리할 수 없다 — 그래서 인라인 폼을 연다.
 * 값이 null이면 닫힌 상태.
 */
const shipForm = ref(null);
function openShipForm() {
  shipForm.value = { carrier: 'CJ', trackingNo: '' };
}
async function submitShip() {
  const trackingNo = shipForm.value.trackingNo.trim();
  // 서버도 @NotBlank로 막지만, 화면에서 먼저 걸러 왕복을 아낀다(배송지 입력과 같은 방식).
  if (!trackingNo) {
    error.value = '송장번호를 입력해 주세요.';
    return;
  }
  error.value = '';
  try {
    await shipOrder(props.id, { carrier: shipForm.value.carrier, trackingNo });
    shipForm.value = null;
    await load();
  } catch (e) {
    error.value = e.message;
  }
}

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}
const shortId = computed(() => (order.value?.id ? `#${String(order.value.id).slice(0, 8)}` : ''));

/**
 * 주문 진행 스텝 — 커머스 주문 상세의 핵심 시각 요소. "지금 어디까지 왔나"를 한눈에 보여준다.
 * 취소된 주문은 진행이 멈춘 것이라 스텝 대신 별도 안내를 띄운다.
 */
const STEPS = [
  { key: 'ORDERED', label: '주문 접수', at: (o) => o.createdAt },
  { key: 'PAID', label: '결제 완료', at: (o) => o.paidAt },
  { key: 'SHIPPED', label: '발송 완료', at: (o) => o.shippedAt },
  { key: 'DELIVERED', label: '배송 완료', at: (o) => o.deliveredAt },
];
const currentStep = computed(() => {
  const s = order.value?.status;
  if (s === 'DELIVERED') return 3;
  if (s === 'SHIPPED') return 2;
  if (s === 'PAID') return 1;
  return 0; // ORDERED
});
const isCancelled = computed(() => order.value?.status === 'CANCELLED');
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

      <!-- 주문 진행 상태: 지금 어디까지 왔는지 -->
      <div class="card mb-6 p-5">
        <p v-if="isCancelled" class="flex flex-wrap items-center gap-2 text-sm text-ink-500">
          <span class="badge badge-danger">취소됨</span>
          <span>이 주문은 취소되어 진행이 멈췄어요.</span>
          <!-- 취소 시각은 V10부터 기록된다. 그 이전 주문은 값이 없어 시각을 감춘다(지어내지 않는다). -->
          <span v-if="order.cancelledAt" class="tabular-nums">{{ fmt(order.cancelledAt) }}</span>
        </p>
        <ol v-else class="flex items-start">
          <li v-for="(st, i) in STEPS" :key="st.key" class="flex flex-1 flex-col items-center text-center">
            <!-- 연결선 + 점 -->
            <div class="flex w-full items-center">
              <span class="h-px flex-1" :class="i === 0 ? 'bg-transparent' : i <= currentStep ? 'bg-brand-600' : 'bg-line'"></span>
              <span
                class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border text-xs font-medium"
                :class="i <= currentStep
                  ? 'border-brand-600 bg-brand-600 text-white'
                  : 'border-line bg-surface text-ink-400'"
                :aria-current="i === currentStep"
              >{{ i < currentStep ? '✓' : i + 1 }}</span>
              <span class="h-px flex-1" :class="i === STEPS.length - 1 ? 'bg-transparent' : i < currentStep ? 'bg-brand-600' : 'bg-line'"></span>
            </div>
            <span class="mt-2 text-xs font-medium" :class="i <= currentStep ? 'text-ink-900' : 'text-ink-400'">
              {{ st.label }}
            </span>
            <span v-if="st.at(order)" class="muted mt-0.5 tabular-nums">{{ fmt(st.at(order)) }}</span>
          </li>
        </ol>
      </div>

      <!-- 관리자가 남의 주문을 볼 때만 "누구 주문인지"(주문 시점 스냅샷).
           일시는 위 진행 스텝에 이미 있어 여기서 반복하지 않는다. -->
      <div v-if="isAdmin && !isMine" class="card flex items-center justify-between gap-4 p-5">
        <span class="muted">구매자</span>
        <span class="text-sm font-medium text-ink-900">{{ order.buyerNickname }}</span>
      </div>

      <!-- 배송지(주문 시점 스냅샷). V11 이전 주문은 값이 없어 아예 감춘다 — 빈 칸을 보여주느니. -->
      <div v-if="order.shipAddress1" class="card mt-6 p-5">
        <h2 class="section-title">배송지</h2>
        <dl class="mt-3 space-y-2 text-sm">
          <div class="flex gap-4">
            <dt class="w-20 shrink-0 text-ink-500">수령인</dt>
            <dd class="text-ink-900">{{ order.shipRecipient }}</dd>
          </div>
          <div class="flex gap-4">
            <dt class="w-20 shrink-0 text-ink-500">연락처</dt>
            <dd class="tabular-nums text-ink-900">{{ order.shipPhone }}</dd>
          </div>
          <div class="flex gap-4">
            <dt class="w-20 shrink-0 text-ink-500">주소</dt>
            <dd class="text-ink-900">{{ addressText(order) }}</dd>
          </div>
        </dl>
      </div>

      <!-- 배송 추적(V13). 운송장 도입 이전 주문은 값이 없어 아예 감춘다 — 배송지 카드와 같은 규칙. -->
      <div v-if="order.shipTrackingNo" class="card mt-6 p-5">
        <h2 class="section-title">배송 추적</h2>
        <dl class="mt-3 space-y-2 text-sm">
          <div class="flex gap-4">
            <dt class="w-20 shrink-0 text-ink-500">택배사</dt>
            <dd class="text-ink-900">{{ order.shipCarrierName }}</dd>
          </div>
          <div class="flex gap-4">
            <dt class="w-20 shrink-0 text-ink-500">송장번호</dt>
            <dd class="tabular-nums text-ink-900">{{ order.shipTrackingNo }}</dd>
          </div>
        </dl>
        <!-- 조회 링크는 서버가 택배사별 형식으로 완성해 준다(화면은 택배사 지식을 갖지 않는다).
             '기타'처럼 조회 형식이 없는 택배사는 trackingUrl이 null이라 버튼이 안 나오고,
             송장번호는 위에 그대로 있어 고객이 직접 조회할 수는 있다. -->
        <a
          v-if="order.trackingUrl"
          :href="order.trackingUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="btn btn-secondary mt-4 inline-flex"
        >배송 조회 ↗</a>
      </div>

      <!-- 품목 + 합계 -->
      <div class="card mt-6">
        <h2 class="section-title border-b border-line px-5 py-4">주문 품목</h2>
        <ul class="divide-y divide-line">
          <li v-for="item in order.items" :key="item.productId" class="flex items-center gap-4 px-5 py-4">
            <ItemThumb :src="item.productImageUrl" :alt="item.productName" />
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

          <!-- 관리자 액션: 결제완료 → 발송(운송장 입력), 발송완료 → 배송완료 -->
          <button
            v-if="isAdmin && order.status === 'PAID' && !shipForm"
            type="button"
            class="btn btn-primary"
            @click="openShipForm"
          >발송 처리</button>
          <button
            v-if="isAdmin && order.status === 'SHIPPED'"
            type="button"
            class="btn btn-primary"
            @click="onDeliver"
          >배송완료 처리</button>
        </div>
      </div>

      <!-- 발송 처리 폼(관리자). 운송장이 필수라 confirm 대화상자로는 처리할 수 없어 인라인 폼으로 받는다. -->
      <div v-if="shipForm" class="card mt-4 p-5">
        <h2 class="section-title">운송장 등록</h2>
        <p class="muted mt-1">
          등록하면 주문이 발송완료로 바뀌고 고객이 배송을 조회할 수 있습니다.
        </p>
        <div class="mt-4 grid gap-3 sm:grid-cols-2">
          <label class="block">
            <span class="muted mb-1 block">택배사</span>
            <select v-model="shipForm.carrier" class="field">
              <option v-for="c in DELIVERY_CARRIERS" :key="c.value" :value="c.value">{{ c.text }}</option>
            </select>
          </label>
          <label class="block">
            <span class="muted mb-1 block">송장번호</span>
            <input v-model="shipForm.trackingNo" class="field" placeholder="숫자만 입력" />
          </label>
        </div>
        <div class="mt-4 flex justify-end gap-2">
          <button type="button" class="btn btn-secondary" @click="shipForm = null">취소</button>
          <button type="button" class="btn btn-primary" @click="submitShip">발송 처리</button>
        </div>
      </div>
    </template>
  </section>
</template>
