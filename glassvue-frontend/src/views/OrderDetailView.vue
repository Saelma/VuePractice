<script setup>
/**
 * 주문 상세 — 읽는 화면이라 폭을 좁히고(page-narrow) 주문 정보 / 품목 / 액션을 세 덩어리로 나눈다(DESIGN.md §4·§7).
 */
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import {
  getOrder, payOrder, shipOrder, deliverOrder, cancelOrder,
  requestReturn, approveReturn, rejectReturn,
  orderStatusText, orderStatusClass, DELIVERY_CARRIERS,
} from '../api/order';
import { priceText, hasDiscount, discountRate, strikePrice } from '../api/product';
import ItemThumb from '../components/ItemThumb.vue';
import { addressText } from '../api/shipping';
import { authState, isAdmin } from '../stores/auth';

const props = defineProps({ id: { type: String, required: true } });
const router = useRouter();

const order = ref(null);
const error = ref('');
const loading = ref(true);
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
const onDeliver = () => act(deliverOrder, '이 주문을 배송완료로 처리할까요?');

// 취소(2026-08-04 B-17). 사유를 **선택**으로 받으려고 인라인 폼으로 바꿨다(전엔 window.confirm).
// 폼 자체가 확인 단계다 — 되돌릴 수 없는 조작이라 확인을 없애지 않는다(DESIGN §7).
// ⚠ 반품 폼과 달리 **빈 사유로도 진행**된다. 강제하면 취소에 마찰이 생기는데, 취소는 고객이
//   빨리 끝내고 싶은 조작이라 그 마찰이 사유를 얻는 값보다 크다.
const cancelForm = ref(null);
function openCancelForm() { cancelForm.value = { reason: '' }; }
async function submitCancel() {
  error.value = '';
  try {
    await cancelOrder(props.id, (cancelForm.value?.reason || '').trim());
    cancelForm.value = null;
    await load();
  } catch (e) {
    error.value = e.message;
  }
}

// 반품(2026-07-24 C-9). 요청은 사유 입력이 필요해 인라인 폼으로 받는다(취소·발송과 같은 이유).
const returnForm = ref(null);
function openReturnForm() { returnForm.value = { reason: '' }; }
async function submitReturn() {
  const reason = (returnForm.value?.reason || '').trim();
  if (!reason) { error.value = '반품 사유를 입력하세요.'; return; }
  error.value = '';
  try {
    await requestReturn(props.id, reason);
    returnForm.value = null;
    await load();
  } catch (e) {
    error.value = e.message;
  }
}
const onApproveReturn = () => act(approveReturn,
  '반품을 승인할까요? 재고가 복원되고 결제금액이 적립금으로 환불됩니다(적립·등급은 회수).');

/**
 * 반품 카드의 상태 문구. 세 갈래다 — ⚠ **거절이 세 번째로 뒤늦게 생겼다**(2026-08-11).
 * 거절은 주문 상태를 DELIVERED 로 되돌리므로 `order.status` 만으로는 «거절됨» 을 말할 수 없고,
 * `returnRejectedAt` 이 유일한 근거다(그게 없던 동안 화면에서 반품 이야기가 통째로 사라졌다).
 */
const returnStatusText = computed(() => {
  if (!order.value) return '';
  if (order.value.status === 'RETURNED') return '반품 완료 (환불됨)';
  if (order.value.status === 'RETURN_REQUESTED') return '반품 요청됨 (관리자 처리 대기)';
  return '반품 요청이 거절됨';
});

// 거절은 **사유가 필수**라 confirm 으로 못 받는다 — 인라인 폼을 연다(발송·반품요청과 같은 이유).
// ⚠ 2026-08-11 이전에는 confirm 한 번으로 끝났고, 그래서 고객은 «왜 거절됐는지» 를 알 방법이 없었다.
//   알림은 "주문 상세에서 확인해 주세요" 라고 했는데 **그 상세에도 아무것도 없었다.**
const rejectForm = ref(null);
function openRejectForm() { rejectForm.value = { reason: '' }; }
async function submitReject() {
  const reason = (rejectForm.value?.reason || '').trim();
  if (!reason) { error.value = '거절 사유를 입력하세요.'; return; }
  error.value = '';
  try {
    await rejectReturn(props.id, reason);
    rejectForm.value = null;
    await load();
  } catch (e) {
    error.value = e.message;
  }
}

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
// 주문번호(V15) — CS에서 고객이 불러줄 수 있는 값이다(UUID 앞자리 대신).
const orderNoText = computed(() => order.value?.orderNo || '');

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
          <p class="muted mt-1 tabular-nums">{{ orderNoText }}</p>
        </div>
        <span class="badge shrink-0" :class="orderStatusClass(order.status)">{{ orderStatusText(order.status) }}</span>
      </div>

      <!-- 주문 진행 상태: 지금 어디까지 왔는지 -->
      <div class="card mb-6 p-5">
        <template v-if="isCancelled">
          <p class="flex flex-wrap items-center gap-2 text-sm text-ink-500">
            <span class="badge badge-danger">취소됨</span>
            <span>이 주문은 취소되어 진행이 멈췄어요.</span>
            <!-- 취소 시각은 V10부터 기록된다. 그 이전 주문은 값이 없어 시각을 감춘다(지어내지 않는다). -->
            <span v-if="order.cancelledAt" class="tabular-nums">{{ fmt(order.cancelledAt) }}</span>
          </p>
          <!--
            취소 사유(V40, B-17). 시각과 **같은 규칙**으로 다룬다 — 값이 없으면 줄 자체를 감춘다.
            사유는 선택이고 V40 이전 취소 주문은 전부 없으므로 **비어 있는 게 흔한 정상**이다.
            "사유 없음" 같은 문구를 지어 넣지 않는다(없는 것을 있는 것처럼 그리지 않는다).
          -->
          <p v-if="order.cancelReason" class="mt-2 flex gap-3 text-sm">
            <span class="shrink-0 text-ink-500">사유</span>
            <span class="text-ink-900">{{ order.cancelReason }}</span>
          </p>
          <!--
            누가 취소했는지(V43, B-25). **관리자가 대신 취소한 경우에만** 뜬다 —
            `cancelledByName` 이 null 이면 본인이 취소한 것이라 줄을 그리지 않는다.

            ⚠ 이 줄이 없으면 고객은 **자기가 안 한 취소를 자기가 한 것으로 읽는다.**
              화면이 「취소됨」 하나만 말하고 주체를 말하지 않으면, 기본값처럼 읽히는 주체는 본인이다.
              (2026-08-07 취소 폼이 적립금 환불을 «말하지 않아서» 안 하는 줄 알았던 것의 반대 방향이다 —
               그때는 침묵이 기능 부재를 가렸고, 여기선 침묵이 행위자를 가린다.)
          -->
          <p v-if="order.cancelledByName" class="mt-2 flex gap-3 text-sm">
            <span class="shrink-0 text-ink-500">처리</span>
            <span class="text-ink-900">고객센터에서 대신 취소했어요 ({{ order.cancelledByName }})</span>
          </p>
        </template>
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
          <!--
            배송 요청사항(V38, B-20). ⚠ **없으면 줄 자체를 안 그린다** — 요청 없는 주문이 대부분이라
            빈 칸을 남기면 배송지 카드가 매번 들쭉날쭉해진다(배송지·추적 카드와 같은 규칙).
          -->
          <div v-if="order.shipMemo" class="flex gap-4">
            <dt class="w-20 shrink-0 text-ink-500">요청사항</dt>
            <dd class="text-ink-900">{{ order.shipMemo }}</dd>
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
          <li v-for="item in order.items" :key="item.variantId || item.productId" class="flex items-center gap-4 px-5 py-4">
            <ItemThumb :src="item.productImageUrl" :alt="item.productName" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-ink-900">{{ item.productName }}</p>
              <!-- 옵션명 스냅샷(2026-07-24 C-8). 단일 옵션/옵션 이전 주문이면 null이라 안 뜬다. -->
              <p v-if="item.optionName" class="muted truncate">{{ item.optionName }}</p>
              <p class="muted mt-1 tabular-nums">
                <!--
                  주문 시점 스냅샷에 줄을 긋는다. 🔴 **무엇을 그을지는 strikePrice 가 정한다**(G-9) —
                  세일로 샀으면 `regularPrice`(세일 전 판매가), 아니면 `listPrice`(정가).
                  ⚠ 예전엔 여기서 `item.listPrice` 를 직접 읽었는데, 그러면 **정가 칸이 빈 상품을
                  세일가로 산 주문**에 흔적이 아예 안 남는다(실측 2026-08-20, 20260820-4733).
                  ⚠ 둘 다 없으면 `hasDiscount` 가 거짓이라 이 줄 자체가 안 그려진다.
                -->
                <span v-if="hasDiscount(item)" class="line-through">{{ priceText(strikePrice(item)) }}</span>
                {{ priceText(item.price) }} × {{ item.quantity }}
                <span v-if="hasDiscount(item)" class="font-medium text-danger">{{ discountRate(item) }}%</span>
              </p>
            </div>
            <span class="text-sm font-semibold tabular-nums text-ink-900">{{ priceText(item.lineTotal) }}</span>
          </li>
        </ul>
        <dl class="space-y-2 border-t border-line px-5 py-4 text-sm">
          <div class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">상품 금액</dt>
            <dd class="tabular-nums text-ink-700">{{ priceText(order.totalPrice) }}</dd>
          </div>
          <!-- 쿠폰 스냅샷(V17). 안 쓴 주문·도입 이전 주문은 할인액이 0이라 줄이 안 나온다. -->
          <div v-if="order.couponDiscount > 0" class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">쿠폰 할인<span v-if="order.couponName" class="muted"> · {{ order.couponName }}</span></dt>
            <dd class="tabular-nums text-danger">−{{ priceText(order.couponDiscount) }}</dd>
          </div>
          <div v-if="order.usedPoint > 0" class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">적립금 사용</dt>
            <dd class="tabular-nums text-danger">−{{ priceText(order.usedPoint) }}</dd>
          </div>
          <div class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">배송비</dt>
            <dd class="tabular-nums" :class="order.shippingFee ? 'text-ink-700' : 'text-emerald-700'">
              {{ order.shippingFee ? priceText(order.shippingFee) : '무료' }}
            </dd>
          </div>
        </dl>
        <!-- 주문 시점에 실제로 받은 금액이다 — 정책이 바뀌어도 이 숫자는 안 바뀐다(스냅샷). -->
        <div class="flex items-end justify-between gap-4 border-t border-line px-5 py-4">
          <span class="text-sm font-medium text-ink-700">결제 금액</span>
          <span class="text-2xl font-bold tabular-nums text-ink-900">{{ priceText(order.payAmount) }}</span>
        </div>

        <!-- 적립은 배송완료 시점이라, 그 전에는 "받을 예정"이 아니라 아무 말도 하지 않는다.
             예정 금액을 미리 보여주면 취소·반품에서 약속이 어긋난다. -->
        <p v-if="order.earnedPoint > 0" class="muted mt-2 text-right">
          이 주문으로 <strong class="text-ink-700">{{ priceText(order.earnedPoint) }}</strong> 적립되었어요.
        </p>
      </div>

      <!-- 액션: 조건은 그대로, 스타일만 정리 -->
      <div class="mt-6 flex flex-wrap items-center justify-between gap-2">
        <button type="button" class="btn btn-secondary" @click="router.push('/orders')">주문 목록</button>

        <div class="flex flex-wrap gap-2">
          <!-- 구매자 액션: 본인 주문일 때만(관리자도 본인 주문이면 보인다) -->
          <template v-if="isMine">
            <button
              v-if="(order.status === 'ORDERED' || order.status === 'PAID') && !cancelForm"
              type="button"
              class="btn btn-danger"
              @click="openCancelForm"
            >주문 취소</button>
            <button
              v-if="order.status === 'ORDERED'"
              type="button"
              class="btn btn-primary"
              @click="onPay"
            >결제하기</button>
            <button
              v-if="order.status === 'DELIVERED' && !returnForm"
              type="button"
              class="btn btn-secondary"
              @click="openReturnForm"
            >반품 요청</button>
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
          <template v-if="isAdmin && order.status === 'RETURN_REQUESTED'">
            <button type="button" class="btn btn-primary" @click="onApproveReturn">반품 승인</button>
            <button type="button" class="btn btn-secondary" @click="openRejectForm">반품 거절</button>
          </template>
        </div>
      </div>

      <!--
        취소 폼(구매자, B-17). 사유는 **선택**이라 비워도 「취소하기」가 눌린다 —
        그래서 라벨에 (선택)을 붙이고 버튼도 비활성화하지 않는다(반품 폼과 다른 점).
        폼이 곧 확인 단계다: 주 버튼이 btn-danger 이고, 무엇이 되돌아오는지(재고)를 먼저 적는다.
      -->
      <div v-if="cancelForm" class="card mt-4 p-5">
        <h2 class="section-title">주문 취소</h2>
        <p class="muted mt-1">취소하면 <strong>재고가 복원</strong>됩니다. 되돌릴 수 없습니다.</p>
        <!--
          쓴 적립금이 있을 때만 한 줄 더 붙인다(2026-08-07).

          ⚠ 반품 폼은 처음부터 «적립금으로 환불된다» 를 말해 왔는데 취소 폼은 아무 말이 없었다.
          말이 없던 게 맞았던 게 아니라 **실제로 안 돌려주고 있었다** — 그걸 고치면서 안내도 맞춘다.

          ⚠ 금액을 조건 없이 늘 띄우지 않는다. 적립금을 안 쓴 주문이 대부분이라, 늘 띄우면
          «0P 환불» 같은 줄이 서고 사용자는 그게 무슨 말인지 되묻게 된다.
        -->
        <p v-if="order.usedPoint > 0" class="muted mt-1">
          사용한 적립금 <strong>{{ priceText(order.usedPoint) }}</strong>은 취소와 함께 <strong>돌려드립니다</strong>.
        </p>
        <label class="field mt-3">
          <span class="field-label">사유 <span class="muted">(선택)</span></span>
          <input
            v-model="cancelForm.reason"
            class="field"
            maxlength="500"
            placeholder="예: 단순 변심, 배송이 늦어서 (안 적어도 됩니다)"
          />
        </label>
        <div class="mt-3 flex gap-2">
          <button type="button" class="btn btn-danger" @click="submitCancel">취소하기</button>
          <button type="button" class="btn btn-secondary" @click="cancelForm = null">닫기</button>
        </div>
      </div>

      <!-- 반품 요청 폼(구매자). 사유가 필요해 인라인 폼으로 받는다. -->
      <div v-if="returnForm" class="card mt-4 p-5">
        <h2 class="section-title">반품 요청</h2>
        <p class="muted mt-1">
          관리자 승인 시 상품 금액이 <strong>적립금으로 환불</strong>됩니다(배송비 제외).
          이 주문으로 받은 적립금과 등급 반영분은 회수됩니다.
        </p>
        <label class="field mt-3">
          <span class="field-label">사유</span>
          <input v-model="returnForm.reason" class="field" placeholder="예: 단순 변심, 상품 불량" />
        </label>
        <div class="mt-3 flex gap-2">
          <button type="button" class="btn btn-primary" @click="submitReturn">반품 요청</button>
          <button type="button" class="btn btn-secondary" @click="returnForm = null">닫기</button>
        </div>
      </div>

      <!-- 반품 거절 폼(관리자). 사유가 **필수**라 confirm 으로 못 받는다(발송·반품요청과 같은 이유). -->
      <div v-if="rejectForm" class="card mt-4 p-5">
        <h2 class="section-title">반품 거절</h2>
        <p class="muted mt-1">
          배송완료 상태로 되돌아갑니다. 재고·적립금은 움직이지 않습니다.
          <strong>입력한 사유가 고객에게 그대로 전달</strong>됩니다(알림 + 주문 상세).
        </p>
        <label class="field mt-3">
          <span class="field-label">사유</span>
          <input v-model="rejectForm.reason" class="field" placeholder="예: 사용 흔적이 있어 반품이 어렵습니다" />
        </label>
        <div class="mt-3 flex gap-2">
          <button type="button" class="btn btn-primary" @click="submitReject">반품 거절</button>
          <button type="button" class="btn btn-secondary" @click="rejectForm = null">닫기</button>
        </div>
      </div>

      <!--
        반품 진행 상태 안내.
        ⚠ **거절도 여기 뜬다**(2026-08-11). 거절하면 상태가 DELIVERED 로 돌아가는데, 예전엔 렌더 조건이
          RETURN_REQUESTED·RETURNED 뿐이라 **카드가 통째로 사라졌다** — 반품을 요청한 적 없는 주문과
          구분이 안 됐고, 거절 알림의 "주문 상세에서 확인해 주세요" 가 **없는 곳을 가리켰다.**
          거절은 상태를 안 남기므로 returnRejectedAt 이 유일한 근거다.
      -->
      <div
        v-if="order.status === 'RETURN_REQUESTED' || order.status === 'RETURNED' || order.returnRejectedAt"
        class="card mt-4 p-5"
      >
        <h2 class="section-title">반품</h2>
        <dl class="mt-3 space-y-2 text-sm">
          <div class="flex justify-between gap-4">
            <dt class="text-ink-500">상태</dt>
            <dd>{{ returnStatusText }}</dd>
          </div>
          <div v-if="order.returnReason" class="flex justify-between gap-4">
            <dt class="text-ink-500">요청 사유</dt>
            <dd class="text-ink-900">{{ order.returnReason }}</dd>
          </div>
          <!-- 거절 사유는 «고객이 알아야 할 답» 이라 요청 사유보다 눈에 띄게 둔다. -->
          <div v-if="order.returnRejectedReason" class="flex justify-between gap-4">
            <dt class="text-ink-500">거절 사유</dt>
            <dd class="text-ink-900">{{ order.returnRejectedReason }}</dd>
          </div>
          <div v-if="order.status === 'RETURNED'" class="flex justify-between gap-4">
            <dt class="text-ink-500">환불 적립금</dt>
            <dd class="tabular-nums text-ink-900">{{ priceText(order.refundAmount) }}</dd>
          </div>
        </dl>
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
