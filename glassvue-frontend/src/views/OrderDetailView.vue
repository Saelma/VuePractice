<script setup>
/**
 * 주문 상세 — 읽는 화면이라 폭을 좁히고(page-narrow) 주문 정보 / 품목 / 액션을 세 덩어리로 나눈다(DESIGN.md §4·§7).
 */
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import {
  getOrder, payOrder, shipOrder, deliverOrder, cancelOrder,
  cancelOrderItem, cancelOrderItemByAdmin,
  requestReturn, requestReturnByAdmin, approveReturn, rejectReturn, returnApproveConfirm,
  orderStatusText, orderStatusClass, DELIVERY_CARRIERS,
} from '../api/order';
import { priceText, hasDiscount, discountRate, strikePrice } from '../api/product';
import ItemThumb from '../components/ItemThumb.vue';
import OrderItemPartialNote from '../components/OrderItemPartialNote.vue';
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

// ─────────────────────────── 부분 취소 (G-4, 2026-08-24)
//
// 🔴 **전체 취소와 버튼을 갈라 둔다.** 「주문 취소」는 주문 전체를 되돌리고 쿠폰까지 복구되지만,
//    부분 취소는 몫을 나눠 돌려주고 **쿠폰은 그대로 걸려 있다**(G-4 결정 1). 한 버튼에 담으면
//    고객이 무엇을 누르는지 모른다.
//
// ⚠ **상한은 `remainingQuantity` 다.** `quantity`(원본 스냅샷)를 쓰면 이미 일부를 뺀 품목에서
//    남은 것보다 많이 보낼 수 있고, 서버가 400 으로 돌려보낸다 — 왕복할 이유가 없다.
const itemCancelForm = ref(null);

function openItemCancelForm(item) {
  itemCancelForm.value = { orderItemId: item.orderItemId, max: item.remainingQuantity, quantity: 1 };
}

/** 지금 취소할 수 있는 품목인가 — 상태 조건은 전체 취소와 **같다**(발송 뒤는 반품이 맡는다). */
const canCancelItems = computed(() =>
  !!order.value
  && (order.value.status === 'ORDERED' || order.value.status === 'PAID')
  && (isMine.value || isAdmin.value));

/**
 * 🔴 **환불 예정 금액** — 서버가 쓰는 배분식과 **같은 식**이다(BACKLOG G-4):
 *
 *     취소금액  = 단가 × 수량
 *     쿠폰 몫   = 남은쿠폰할인 × 취소금액 / 남은상품합계   (내림)
 *     적립금 몫 = 남은적립금   × 취소금액 / 남은상품합계   (내림)
 *     환불액    = 취소금액 − 쿠폰 몫 − 적립금 몫
 *
 * ⚠ **분모·분자가 «원본» 이 아니라 «지금 남은 값» 이다.** `totalPrice`·`couponDiscount` 를 그대로
 *    쓰면 두 번째 취소부터 서버와 갈린다 — 서버는 이미 떼어 간 몫을 빼고 계산한다.
 *
 * 🔴 ⚠ **같은 식이 서버와 화면 두 곳에 있다**(CLAUDE.md 가 경계하는 모양이다). 그래도 두는 이유는
 *    «얼마 돌려받나» 를 누르기 **전에** 보여줘야 하기 때문이고, 미리보기 API 를 따로 파는 것보다
 *    작다. **대신 어긋나면 잡히게 해 뒀다** — 뷰 테스트가 서버 테스트와 **같은 숫자**
 *    (12,001 · 857)를 단언한다(`ProductDiscountAdminView` 의 반올림 단언과 같은 장치).
 */
const itemCancelPreview = computed(() => {
  const f = itemCancelForm.value;
  if (!f || !order.value) return null;
  const item = order.value.items.find((i) => i.orderItemId === f.orderItemId);
  const qty = Number(f.quantity);
  if (!item || !Number.isInteger(qty) || qty < 1 || qty > item.remainingQuantity) return null;

  const base = order.value.totalPrice - order.value.cancelledItemsTotal;
  if (base <= 0) return null;
  const amount = item.price * qty;
  const couponShare = Math.floor((order.value.couponDiscount - couponTaken.value) * amount / base);
  const pointShare = Math.floor((order.value.usedPoint - order.value.cancelledPoint) * amount / base);
  return { amount, couponShare, pointShare, refund: amount - couponShare - pointShare };
});

/**
 * 지금까지 회수된 쿠폰 몫. ⚠ 서버가 이 값을 따로 안 보낸다 — `refundedAmount` 가
 * «취소금액 − 쿠폰몫 − 적립금몫» 이므로 **거꾸로 풀어서** 얻는다.
 * (셋 다 응답에 있으니 새 필드를 요구하지 않고 있는 것으로 만든다.)
 */
const couponTaken = computed(() => order.value
  ? order.value.cancelledItemsTotal - order.value.refundedAmount - order.value.cancelledPoint
  : 0);

/**
 * 이번 취소로 **주문이 통째로 취소되나** — 남은 수량이 이 품목의 취소 수량뿐일 때다.
 *
 * 🔴 서버가 그때 주문을 `CANCELLED` 로 떨어뜨리고 쿠폰을 복구한다(G-4). 화면이 그걸 말하지 않으면
 * 고객은 「품목 하나만 빼는 줄」 알고 누른다 — **되돌리기 어려운 조작을 조용히 하지 않는다.**
 */
const cancellingLastItem = computed(() => {
  const f = itemCancelForm.value;
  if (!f || !order.value) return false;
  return order.value.items.every((i) => (i.orderItemId === f.orderItemId
    ? i.remainingQuantity - Number(f.quantity) <= 0
    : i.remainingQuantity === 0));
});

async function onCancelItem() {
  const f = itemCancelForm.value;
  try {
    // ⚠ 관리자가 **남의** 주문을 뺄 때만 관리자 경로다. 본인 주문이면 관리자여도 본인 경로 —
    //    백엔드가 소유 기준으로 가르는 것과 맞춘다(WA §2-3, 2026-07-20 의 그 버그).
    const call = isMine.value ? cancelOrderItem : cancelOrderItemByAdmin;
    await call(props.id, f.orderItemId, Number(f.quantity));
    itemCancelForm.value = null;
    await load();
  } catch (e) {
    error.value = e.message;
  }
}

// 반품(2026-07-24 C-9). 요청은 사유 입력이 필요해 인라인 폼으로 받는다(취소·발송과 같은 이유).
//
// ─────────────────────────── 부분 반품 (G-10, 2026-08-25)
//
// 🔴 **고객이 품목·수량을 고른다**(G-10 결정 2) — 승인은 «요청한 대로 해 준다» 라 관리자가
//    정하면 그 규약이 깨진다. 기본값은 «남은 것 전부» 다: 전량 반품이 가장 흔하고, 기본값이
//    고르는 수고를 없앤다. ⚠ 그래도 **비워 보낼 수는 없다**(서버가 items 를 필수로 받는다).
const returnForm = ref(null);

/** 지금 반품 요청을 걸 수 있는 품목 — `returnableQuantity` 는 서버가 계산해 준다(화면이 안 센다). */
const returnableItems = computed(() =>
  (order.value?.items || []).filter((i) => i.returnableQuantity > 0));

/**
 * @param byAdmin 관리자 대행인가 (2026-08-27, §I-15). 🔴 **폼은 하나를 쓴다** — 품목·수량·사유를
 *   고르는 일이 똑같아서다. 갈리는 것은 **어느 API 로 보내나**와 **문구**뿐이다.
 *
 * ⚠ 🔴 **템플릿에서 `@click="openReturnForm"` 로 쓰면 안 된다** — Vue 가 **DOM 이벤트를 첫 인자로**
 *   넘겨서 `byAdmin` 이 MouseEvent(truthy)가 되고, **고객의 반품이 전부 대행 경로로 나간다**(403).
 *   실제로 그렇게 썼다가 테스트가 잡았다(2026-08-27). 반드시 **`openReturnForm()`** 로 부른다.
 *   그래서 아래에서 `Boolean(...)` 이 아니라 `=== true` 로 눕힌다 — 실수해도 대행으로 안 샌다.
 */
function openReturnForm(byAdmin = false) {
  returnForm.value = {
    byAdmin: byAdmin === true,
    reason: '',
    quantities: Object.fromEntries(returnableItems.value.map((i) => [i.orderItemId, i.returnableQuantity])),
  };
}

/**
 * 🔴 **환불 예정 금액** — 서버가 쓰는 배분식과 **같은 식**이다(BACKLOG G-10):
 *
 *     반품금액 = 단가 × 수량
 *     쿠폰 몫  = 남은쿠폰할인 × 반품금액 / 남은상품합계   (내림)
 *     환불액   = 반품금액 − 쿠폰 몫
 *
 * ⚠ **취소와 다르다** — 적립금 몫을 빼지 않는다. 반품은 «현금결제분 + 사용적립금» 을 **함께**
 *    적립금으로 돌려주기 때문이다(그래서 적립금 몫이 환불액 안에 이미 들어 있다).
 *
 * ⚠ **품목마다 «이번 회차에 이미 뗀 몫» 을 빼 가며** 분모·분자를 다시 만든다. 한 번만 잡으면
 *    두 번째 품목부터 서버와 갈린다 — 내림 배분이 경로 의존이라 그렇다.
 * ⚠ **순서가 서버와 같아야 한다** — 서버는 `order.getItems()` 순으로 도는데 그 순서가 곧
 *    `order.items` 순이다. 화면이 정렬을 바꾸면 잔돈 1원이 다른 자리에 붙는다.
 *
 * 🔴 ⚠ **같은 식이 서버와 화면 두 곳에 있다**(부분 취소와 같은 자리, CLAUDE.md 가 경계하는 모양).
 *    두는 이유도 같다 — «얼마 돌려받나» 를 누르기 **전에** 보여줘야 한다.
 *    **대신 어긋나면 잡히게 해 뒀다**: 뷰 테스트가 서버 테스트와 **글자 그대로 같은 숫자**를 단언한다.
 */
const returnPreview = computed(() => {
  const f = returnForm.value;
  const o = order.value;
  if (!f || !o) return null;
  let dItems = 0;
  let dCoupon = 0;
  let refund = 0;
  for (const item of o.items) {
    const qty = Number(f.quantities[item.orderItemId] || 0);
    if (!Number.isInteger(qty) || qty <= 0) continue;
    if (qty > item.returnableQuantity) return null;
    const base = o.totalPrice - o.cancelledItemsTotal - o.returnedItemsTotal - dItems;
    if (base <= 0) return null;
    const remCoupon = o.couponDiscount - couponTaken.value - o.returnedCouponDiscount - dCoupon;
    const amount = item.price * qty;
    const couponShare = Math.floor((remCoupon * amount) / base);
    dItems += amount;
    dCoupon += couponShare;
    refund += amount - couponShare;
  }
  return refund > 0 || dItems > 0 ? { amount: dItems, couponShare: dCoupon, refund } : null;
});

/**
 * 이번 요청이 **주문을 통째로 비우나** — 그때만 서버가 `RETURNED` 로 떨어뜨리고 **쿠폰을 복구**한다.
 * 부분 취소의 `cancellingLastItem` 과 같은 자리다: 되돌리기 어려운 조작을 조용히 하지 않는다.
 */
const returningEverything = computed(() => {
  const f = returnForm.value;
  if (!f || !order.value) return false;
  return order.value.items.every(
    (i) => i.remainingQuantity - Number(f.quantities[i.orderItemId] || 0) <= 0);
});

async function submitReturn() {
  const reason = (returnForm.value?.reason || '').trim();
  if (!reason) { error.value = '반품 사유를 입력하세요.'; return; }
  const items = Object.entries(returnForm.value.quantities)
    .map(([orderItemId, quantity]) => ({ orderItemId, quantity: Number(quantity) }))
    .filter((l) => Number.isInteger(l.quantity) && l.quantity > 0);
  // ⚠ 서버도 막지만 여기서 먼저 막는다 — 왕복할 이유가 없고, 문구를 이 자리에서 더 정확히 말할 수 있다.
  if (items.length === 0) { error.value = '반품할 품목을 하나 이상 골라 주세요.'; return; }
  error.value = '';
  try {
    // ⚠ **경로가 갈린다** — 대행은 기한을 안 보고 원장에 남으며 고객에게 알림이 간다(§I-15).
    await (returnForm.value.byAdmin
      ? requestReturnByAdmin(props.id, reason, items)
      : requestReturn(props.id, reason, items));
    returnForm.value = null;
    await load();
  } catch (e) {
    error.value = e.message;
  }
}
// ⚠ 문구가 «결제금액» 에서 «요청된 품목» 으로 바뀌었다(G-10) — 부분 반품이 생기면서 전량이 아닐 수 있다.
// 🔴 **수량은 2026-08-27 에 붙었다**(§I-7). 관리자 «목록» 에만 넣고 여기를 안 열어서, 같은 주문을
//    목록에서 승인하면 「8개 중 5개」, 상세에서 승인하면 수량이 없는 상태였다 — **사용자가 잡았다.**
//    그래서 문구를 `api/order.js` 한 곳으로 옮겼다. 여기서 다시 적지 않는다.
const onApproveReturn = () => act(approveReturn, returnApproveConfirm(order.value));

/**
 * 반품 카드의 상태 문구. ⚠ **거절이 세 번째로 뒤늦게 생겼고**(2026-08-11),
 * **부분 반품이 네 번째로 생겼다**(2026-08-25, §I-3).
 *
 * 🔴 둘 다 **주문 상태를 DELIVERED 로 되돌리는** 갈래라 `order.status` 만으로는 말할 수 없다 —
 * 거절은 `returnRejectedAt`, 부분 반품은 `returnedItemsTotal` 이 유일한 근거다.
 * ⚠ **순서가 규약이다**: 부분 반품을 거절보다 **먼저** 본다. 부분 반품 뒤 다시 요청했다 거절당하면
 * 둘 다 참인데, 그때 고객에게 급한 것은 «왜 거절됐나» 이므로 거절이 **마지막에** 이긴다.
 * → 아래 순서는 «RETURNED → 요청중 → 거절 → 부분 반품» 이다.
 */
const returnStatusText = computed(() => {
  if (!order.value) return '';
  if (order.value.status === 'RETURNED') return '반품 완료 (환불됨)';
  if (order.value.status === 'RETURN_REQUESTED') return '반품 요청됨 (관리자 처리 대기)';
  if (order.value.returnRejectedAt) return '반품 요청이 거절됨';
  // 🔴 여기가 없으면 부분 반품된 주문이 «거절됨» 으로 뜬다 — 렌더 조건만 넓히면 생기는 함정이다.
  return '일부 반품 완료 (남은 품목은 그대로)';
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

/**
 * 반품이 왜 안 되는지 한 줄 (2026-08-27, §I-9).
 *
 * ⚠ **«되나 안 되나» 는 서버가 이미 답했다**(`returnRequestable`) — 여기서 다시 판정하지 않는다.
 *   이 함수가 하는 일은 **이유를 말하는 것**뿐이라, 마감 시각은 «문구» 로만 쓴다.
 * ⚠ 마감 시각이 없으면(배송 전 등) 날짜를 지어내지 말고 일반 문구로 떨어진다.
 */
const returnClosedText = computed(() => {
  const at = order.value?.returnDeadline;
  return at ? `반품 가능 기간이 지났습니다 (${fmt(at)}까지).` : '지금은 반품을 요청할 수 없습니다.';
});
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
          <!--
            ⚠ **key 가 `orderItemId` 다**(G-4 로 생겼다). 예전엔 `variantId || productId` 였는데
            **같은 상품의 다른 옵션이 한 주문에 둘 이상** 들어오면 그것으로는 안 갈린다.
            부분 취소가 품목을 지목해야 하면서 이 자리가 실제로 필요해졌다.
          -->
          <li v-for="item in order.items" :key="item.orderItemId" class="flex items-center gap-4 px-5 py-4">
            <ItemThumb :src="item.productImageUrl" :alt="item.productName" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-ink-900">{{ item.productName }}</p>
              <!-- 옵션명 스냅샷(2026-07-24 C-8). 단일 옵션/옵션 이전 주문이면 null이라 안 뜬다. -->
              <p v-if="item.optionName" class="muted truncate">{{ item.optionName }}</p>
              <p class="muted mt-1 tabular-nums">
                <!--
                  주문 시점 스냅샷에 줄을 긋는다. 🔴 **무엇을 그을지는 strikePrice 가 정한다**(G-9) —
                  세일로 샀으면 `regularPrice`(세일 전 판매가), 아니면 `listPrice`(정가).
                  ⚠ **`item.listPrice` 를 직접 읽지 않는다** — 그러면 **정가 칸이 빈 상품을
                  세일가로 산 주문**에 흔적이 아예 안 남는다.
                  ⚠ 둘 다 없으면 `hasDiscount` 가 거짓이라 이 줄 자체가 안 그려진다.
                -->
                <span v-if="hasDiscount(item)" class="line-through">{{ priceText(strikePrice(item)) }}</span>
                {{ priceText(item.price) }} × {{ item.quantity }}
                <span v-if="hasDiscount(item)" class="font-medium text-danger">{{ discountRate(item) }}%</span>
              </p>
              <!--
                부분 취소·반품 흔적(G-4 · G-10). 🔴 **여기 있던 세 줄을 컴포넌트로 뽑았다**
                (2026-08-27, §I-7) — 주문 «목록» 도 같은 표시가 필요해졌는데, 베껴 쓰면
                **상세와 목록이 같은 주문에 다른 말을 하게 된다.** 규칙은 그쪽에 적혀 있다.
              -->
              <OrderItemPartialNote :item="item" />
              <!--
                부분 취소 버튼. ⚠ 「주문 취소」와 **갈라 둔다** — 전체 취소는 쿠폰까지 복구되고
                이건 안 된다(G-4 결정 1). 남은 수량이 0이면 뺄 것이 없어 안 그린다.
              -->
              <button
                v-if="canCancelItems && item.remainingQuantity > 0 && !itemCancelForm"
                type="button"
                class="btn btn-secondary btn-sm mt-2"
                @click="openItemCancelForm(item)"
              >이 품목 취소</button>
            </div>
            <div class="text-right">
              <span class="text-sm font-semibold tabular-nums"
                    :class="item.remainingQuantity === 0 ? 'text-ink-400 line-through' : 'text-ink-900'"
              >{{ priceText(item.lineTotal) }}</span>
              <!-- 일부만 빠졌으면 «지금 살아 있는 금액» 을 아래 줄에 적는다. -->
              <p v-if="(item.cancelledQuantity > 0 || item.returnedQuantity > 0) && item.remainingQuantity > 0"
                 class="muted tabular-nums">→ {{ priceText(item.price * item.remainingQuantity) }}</p>
            </div>
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
        <!--
          부분 취소로 되돌아간 것(G-4). ⚠ **위 네 줄은 주문 시점 원본이고 여기부터는 그 뒤에 빠진 것**이다.
          부분 취소가 없으면 값이 0이라 줄이 아예 안 나온다 — 예전 화면과 똑같이 읽힌다.
        -->
        <dl v-if="order.cancelledItemsTotal > 0" class="space-y-2 border-t border-line px-5 py-4 text-sm">
          <div class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">취소된 품목</dt>
            <dd class="tabular-nums text-ink-700">−{{ priceText(order.cancelledItemsTotal) }}</dd>
          </div>
          <div class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">환불액</dt>
            <dd class="tabular-nums font-medium text-emerald-700">{{ priceText(order.refundedAmount) }}</dd>
          </div>
          <!-- 적립금은 돈이 아니라 계정으로 돌아갔다 — 환불액과 갈라 적지 않으면 두 번 받은 것처럼 읽힌다. -->
          <div v-if="order.cancelledPoint > 0" class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">돌려받은 적립금</dt>
            <dd class="tabular-nums text-emerald-700">{{ priceText(order.cancelledPoint) }}</dd>
          </div>
        </dl>

        <!--
          🔴 반품으로 되돌아간 것(§I-4, 2026-08-25). **취소 섹션과 줄을 나눈다** — 둘은 돈이 다르게
          움직인다(취소는 돈으로, 반품은 «현금분 + 쓴 적립금» 을 함께 적립금으로). 합치면 못 가른다.
          ⚠ 이 섹션이 없어서 **부분 반품된 주문의 합계 카드는 위 줄들의 산수가 안 맞는데
            그 차액이 어디에도 없었다** — 서버는 값을 보내고 있었고 화면만 안 읽었다.
        -->
        <dl v-if="order.returnedItemsTotal > 0" class="space-y-2 border-t border-line px-5 py-4 text-sm">
          <div class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">반품된 품목</dt>
            <dd class="tabular-nums text-ink-700">−{{ priceText(order.returnedItemsTotal) }}</dd>
          </div>
          <!--
            ⚠ **취소와 달리 «환불액» 하나로 끝난다** — 반품 환불에는 쓴 적립금이 이미 들어 있다.
              취소처럼 「돌려받은 적립금」을 따로 적으면 **두 번 받은 것처럼** 읽힌다.
          -->
          <div class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">환불 적립금</dt>
            <dd class="tabular-nums font-medium text-emerald-700">
              {{ priceText(order.returnedItemsTotal - order.returnedCouponDiscount) }}
            </dd>
          </div>
          <div v-if="order.reversedEarnedPoint > 0" class="flex items-center justify-between gap-4">
            <dt class="text-ink-500">회수된 적립</dt>
            <dd class="tabular-nums text-ink-700">−{{ priceText(order.reversedEarnedPoint) }}</dd>
          </div>
        </dl>

        <!--
          주문 시점에 실제로 받은 금액이다 — 정책이 바뀌어도 이 숫자는 안 바뀐다(스냅샷).
          ⚠ **부분 취소가 있었으면 이 값은 «지금 받을 금액» 이다**(서버가 뺄셈을 이미 했다).
             그래서 그때는 이름도 바꿔 단다 — 「결제 금액」이라고 하면 처음 낸 금액으로 읽힌다.
        -->
        <div class="flex items-end justify-between gap-4 border-t border-line px-5 py-4">
          <span class="text-sm font-medium text-ink-700">
            {{ order.cancelledItemsTotal > 0 || order.returnedItemsTotal > 0 ? '남은 결제 금액' : '결제 금액' }}
          </span>
          <span class="text-2xl font-bold tabular-nums text-ink-900">{{ priceText(order.payAmount) }}</span>
        </div>

        <!--
          부분 취소 폼(G-4). 품목 줄의 「이 품목 취소」가 연다 — 합계 카드 아래에 두는 이유는
          **환불 예정 금액이 위 숫자들과 나란히 읽혀야** 하기 때문이다.
        -->
        <div v-if="itemCancelForm" class="border-t border-line px-5 py-4">
          <p class="text-sm font-medium text-ink-900">이 품목을 몇 개 취소할까요?</p>
          <div class="mt-3 flex flex-wrap items-center gap-3">
            <input
              v-model.number="itemCancelForm.quantity"
              type="number"
              min="1"
              :max="itemCancelForm.max"
              class="ipt w-24 tabular-nums"
              aria-label="취소 수량"
            />
            <span class="muted">남은 수량 {{ itemCancelForm.max }}개</span>
          </div>

          <!--
            🔴 **환불 예정 금액을 누르기 전에 말한다.** 2026-08-07 에 취소 폼이 적립금 환불을
            «말하지 않아서» 고객이 «안 해 주는 줄» 알았던 것의 같은 자리다 — 돈이 움직이는데
            화면이 침묵하면 사용자는 최악을 가정한다.
            ⚠ 값이 안 나오면(수량이 범위 밖) 문장을 **안 그린다** — 반쪽 문장이 더 헷갈린다.
          -->
          <p v-if="itemCancelPreview" class="mt-3 text-sm text-ink-700">
            <b class="tabular-nums">{{ priceText(itemCancelPreview.refund) }}</b>을 환불해요.
            <span v-if="itemCancelPreview.pointShare > 0" class="muted">
              (적립금 {{ priceText(itemCancelPreview.pointShare) }}은 계정으로 돌아가요)
            </span>
          </p>
          <!--
            🔴 **쿠폰 몫이 빠진다는 것을 말한다** — 이게 이 화면에서 가장 놀랄 만한 숫자다.
            «15,000원짜리를 뺐는데 왜 12,001원만 오나» 에 답하지 않으면 문의가 된다.
          -->
          <p v-if="itemCancelPreview && itemCancelPreview.couponShare > 0" class="muted mt-1">
            쿠폰 할인 중 {{ priceText(itemCancelPreview.couponShare) }}은 이 품목 몫이라 환불에서 빠져요.
            <b class="text-ink-700">쿠폰은 그대로 남은 주문에 걸려 있어요.</b>
          </p>
          <!-- ⚠ 마지막 품목이면 주문 자체가 취소된다 — 「품목 하나만 빼는 줄」 알고 눌러선 안 된다. -->
          <p v-if="cancellingLastItem" class="alert-error mt-3">
            마지막 품목이에요. 취소하면 <b>주문 전체가 취소</b>되고 쓴 쿠폰도 돌아와요.
          </p>

          <div class="mt-4 flex gap-2">
            <button type="button" class="btn btn-danger" :disabled="!itemCancelPreview" @click="onCancelItem">
              취소하기
            </button>
            <button type="button" class="btn btn-secondary" @click="itemCancelForm = null">그만두기</button>
          </div>
        </div>

        <!-- 적립은 배송완료 시점이라, 그 전에는 "받을 예정"이 아니라 아무 말도 하지 않는다.
             예정 금액을 미리 보여주면 취소·반품에서 약속이 어긋난다. -->
        <!--
          🔴 **회수분을 뺀다**(§I-4, 2026-08-25). 예전엔 원본 `earnedPoint` 를 그대로 말해서,
             200P 가 회수된 뒤에도 «500원 적립되었어요» 라고 했다. **준 것**과 **지금 남은 것**은 다르다.
          ⚠ 부분 반품 전에는 갈릴 일이 없었다(전량 반품이면 주문이 RETURNED 라 화면이 달랐다) —
            **멀쩡한 배송완료 주문이 틀린 값을 말하는 경우가 새로 생겼다.**
        -->
        <p v-if="order.earnedPoint - order.reversedEarnedPoint > 0" class="muted mt-2 text-right">
          이 주문으로
          <strong class="text-ink-700">{{ priceText(order.earnedPoint - order.reversedEarnedPoint) }}</strong>
          적립되었어요.
          <span v-if="order.reversedEarnedPoint > 0">(반품으로 {{ priceText(order.reversedEarnedPoint) }} 회수)</span>
        </p>
      </div>

      <!--
        🔴 **반품이 왜 안 되는지 «보이는 줄» 로 말한다** (2026-08-27, §I-9).
        버튼의 `title` 툴팁만으론 «말한다» 가 안 된다 — 터치 기기에는 툴팁이 없다.
        ⚠ **판정은 서버가 했다**(`returnRequestable`) — 여기서 마감 시각을 «지금» 과 비교하지 않는다.
        ⚠ 배송완료 주문에만 뜬다 — 그 전에는 «아직» 이지 «지났다» 가 아니다.
      -->
      <p v-if="isMine && order.status === 'DELIVERED' && !order.returnRequestable"
         class="alert-warning mt-6">{{ returnClosedText }}</p>

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
            <!--
              🔴 **기한이 지나도 버튼을 안 숨긴다**(2026-08-27, §I-9). 있던 것이 그냥 사라지면
              고객은 **화면이 고장 났다**고 읽는다 — 발송 후 취소 버튼을 «안 그리는» 것과
              반대 방향인데, 그쪽은 «될 것처럼 보여 주지 않는다» 이고 이쪽은 «있던 것이
              사라졌다» 라서다.
              ⚠ **판정은 서버가 한다**(`returnRequestable`). 화면이 마감 시각을 «지금» 과
              비교하면 서버와 두 벌이 되고, 시계가 어긋난 기기에서 둘이 갈린다.
            -->
            <button
              v-if="order.status === 'DELIVERED' && !returnForm"
              type="button"
              class="btn btn-secondary"
              :disabled="!order.returnRequestable"
              :title="order.returnRequestable ? undefined : returnClosedText"
              @click="openReturnForm()"
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
          <!--
            🔴 **대행 반품 요청** (2026-08-27, §I-15). §I-9 이 7일 기한을 걸면서 «기한을 넘긴 건을
            구제할 자리» 가 사라졌고, 이 버튼이 그 자리다.
            ⚠ **`returnRequestable` 을 안 본다** — 그 값은 «고객이 지금 걸 수 있나» 이고, 이 버튼은
            **기한이 지났을 때가 존재 이유**다. 서버도 이 경로에서만 기한을 안 본다.
            ⚠ **남의 주문일 때만** 뜬다 — 관리자 본인 주문이면 위쪽 「반품 요청」이 이미 있다.
          -->
          <button
            v-if="isAdmin && !isMine && order.status === 'DELIVERED' && !returnForm"
            type="button"
            class="btn btn-secondary"
            @click="openReturnForm(true)"
          >반품 대행 접수</button>
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

      <!--
        반품 요청 폼(구매자). 사유가 필요해 인라인 폼으로 받는다.
        🔴 **품목·수량을 여기서 고른다**(G-10 결정 2) — 승인은 «요청한 대로» 해 주므로 고객이 말해야 한다.
      -->
      <div v-if="returnForm" class="card mt-4 p-5">
        <h2 class="section-title">{{ returnForm.byAdmin ? '반품 요청 — 관리자 대행' : '반품 요청' }}</h2>
        <!--
          🔴 **대행이면 «누구 대신인지» 와 «기한을 무시한다» 를 말한다** (2026-08-27, §I-15).
          관리자가 자기 반품인 줄 알고 누르면 안 되고, 기한 넘긴 건을 여기서 통과시키는 것이
          **의도된 동작**이라는 것도 화면이 말해 줘야 한다.
        -->
        <p v-if="returnForm.byAdmin" class="alert-warning mt-1">
          <strong>{{ order.buyerNickname }}</strong> 님을 대신해 접수합니다.
          <strong>반품 가능 기간이 지났어도 접수됩니다</strong> — 원장에 남고 고객에게 알림이 갑니다.
        </p>
        <p class="muted mt-1">
          관리자 승인 시 <strong>요청한 품목의 금액</strong>이 <strong>적립금으로 환불</strong>됩니다(배송비 제외).
          그 몫만큼 적립금과 등급 반영분도 회수됩니다.
        </p>

        <!-- 품목별 수량. ⚠ 상한은 서버가 준 `returnableQuantity` 다 — 화면이 다시 세지 않는다. -->
        <ul class="mt-4 divide-y divide-line border-y border-line">
          <li v-for="item in returnableItems" :key="item.orderItemId" class="flex items-center gap-3 py-3">
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm text-ink-900">{{ item.productName }}</p>
              <p v-if="item.optionName" class="muted truncate">{{ item.optionName }}</p>
              <p class="muted tabular-nums">{{ priceText(item.price) }} · 반품 가능 {{ item.returnableQuantity }}개</p>
            </div>
            <input
              v-model.number="returnForm.quantities[item.orderItemId]"
              type="number"
              min="0"
              :max="item.returnableQuantity"
              class="field w-20 text-right tabular-nums"
            />
          </li>
        </ul>
        <!-- ⚠ 0 으로 내려 두면 그 품목은 빠진다 — 「전부 반품」이 기본이고 «빼는» 방식이다. -->
        <p class="muted mt-2">돌려보내지 않을 품목은 수량을 <b>0</b> 으로 두세요.</p>

        <label class="field mt-3">
          <span class="field-label">사유</span>
          <input v-model="returnForm.reason" class="field" placeholder="예: 단순 변심, 상품 불량" />
        </label>

        <!--
          환불 예정 금액. 🔴 **누르기 전에 보여준다** — 부분 취소와 같은 자리·같은 이유다.
        -->
        <dl v-if="returnPreview" class="mt-4 space-y-1 border-t border-line pt-3 text-sm">
          <div class="flex justify-between gap-4">
            <dt class="text-ink-500">반품 상품 금액</dt>
            <dd class="tabular-nums text-ink-900">{{ priceText(returnPreview.amount) }}</dd>
          </div>
          <div v-if="returnPreview.couponShare > 0" class="flex justify-between gap-4">
            <dt class="text-ink-500">회수되는 쿠폰 할인 몫</dt>
            <dd class="tabular-nums text-ink-900">− {{ priceText(returnPreview.couponShare) }}</dd>
          </div>
          <div class="flex justify-between gap-4 font-medium">
            <dt class="text-ink-900">환불 예정 적립금</dt>
            <dd class="tabular-nums text-ink-900">{{ priceText(returnPreview.refund) }}</dd>
          </div>
        </dl>
        <!--
          🔴 **주문이 통째로 비는 경우를 말한다** — 그때만 쿠폰이 돌아온다. 부분 취소의 같은 안내와
             짝이다: 되돌리기 어려운 조작을 조용히 하지 않는다.
        -->
        <p v-if="returningEverything" class="mt-3 text-sm text-danger">
          남은 품목이 전부 빠집니다 — 주문이 <b>반품 완료</b>로 바뀌고 사용한 쿠폰이 돌아옵니다.
        </p>

        <div class="mt-4 flex gap-2">
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
        v-if="order.status === 'RETURN_REQUESTED' || order.status === 'RETURNED'
          || order.returnRejectedAt || order.returnedItemsTotal > 0"
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
          <!--
            🔴 **요청 중에도 보여준다**(G-10) — 서버가 «승인하면 얼마인가» 를 계산해 보내므로
               관리자가 누르기 전에 금액을 본다. 승인 뒤에는 «지금까지 실제로 돌려준 누적» 이다.
          -->
          <div v-if="order.refundAmount > 0" class="flex justify-between gap-4">
            <dt class="text-ink-500">
              {{ order.status === 'RETURN_REQUESTED' ? '환불 예정 적립금' : '환불 적립금' }}
            </dt>
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
