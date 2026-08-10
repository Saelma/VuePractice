<script setup>
/**
 * 관리자 주문 목록 — 발송 처리 동선의 시작점.
 *
 * 이 화면이 생기기 전엔 주문 id를 알아야만 발송할 수 있어서, 실제로는 DB를 직접 뒤져야 했다.
 * 그래서 기본 필터를 **결제완료(PAID)** 로 둔다 — 관리자가 이 화면에 오는 이유가
 * "발송할 주문 찾기"이기 때문. 전체를 보려면 필터를 바꾸면 된다.
 */
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import CustomStore from 'devextreme/data/custom_store';
import { DxDataGrid, DxColumn, DxPaging, DxPager } from 'devextreme-vue/data-grid';
import { DxTextBox } from 'devextreme-vue/text-box';
import {
  fetchAdminOrders, fetchAdminOrderCounts, shipOrder, deliverOrder, approveReturn, rejectReturn,
  adminCancelOrder,
  orderStatusText, orderStatusClass, ORDER_STATUS_TEXT, DELIVERY_CARRIERS,
  resolveOrderStatusFilter,
} from '../api/order';
import { priceText } from '../api/product';

const router = useRouter();
const route = useRoute();
const error = ref('');

// 기본은 **결제완료(PAID)** = 발송 대기. `?status=` 로 들어오면 그쪽을 우선한다 —
// 관리자 홈(B-16)의 「반품 요청」 카드처럼 **다른 할 일을 집어서 오는 진입점**이 생겼다.
// 값 판정은 api/order.js 에 두고 테스트로 고정했다(모르는 값이 빈 목록으로 보이는 걸 막는다).
const form = ref({ status: resolveOrderStatusFilter(route.query.status), buyer: '', orderNo: '' });
const applied = ref({ ...form.value });
const gridRef = ref(null);

/**
 * 상태별 건수 — 필터를 바꿔보지 않고도 "할 일이 몇 건인지" 보이게 한다.
 * 발송 처리 후에도 다시 읽어 숫자가 즉시 줄어드는 게 보이게 한다.
 */
const counts = ref({});
const TABS = [{ value: null, text: '전체' },
  ...Object.entries(ORDER_STATUS_TEXT).map(([value, text]) => ({ value, text }))];
const totalCount = computed(() => Object.values(counts.value).reduce((a, b) => a + b, 0));
const countOf = (v) => (v === null ? totalCount.value : (counts.value[v] ?? 0));

async function loadCounts() {
  try {
    counts.value = await fetchAdminOrderCounts();
  } catch (e) {
    /* 요약 실패해도 목록은 동작한다 */
  }
}
onMounted(loadCounts);

/** 탭 클릭 → 즉시 적용(운영 화면에선 검색 버튼을 한 번 더 누르게 하지 않는다) */
function pickTab(v) {
  form.value.status = v;
  applied.value = { ...form.value };
  gridRef.value?.instance.refresh();
}

const store = new CustomStore({
  key: 'id',
  load: async (options) => {
    const size = options.take || 10;
    const page = Math.floor((options.skip || 0) / size);
    const res = await fetchAdminOrders({ ...applied.value, page, size });
    return { data: res.content, totalCount: res.totalElements };
  },
});

/**
 * 빈 목록 문구는 필터에 따라 다르게 말한다.
 *
 * 기본 필터가 PAID(발송 대기)라 발송할 게 없으면 자연히 빈 화면이 되는데,
 * "조건에 맞는 주문이 없습니다"만 뜨면 화면이 고장 난 것처럼 보인다.
 * 기본 상태에서는 **"할 일이 없다"** 는 뜻으로 읽히게 문구를 바꾼다.
 */
const noDataText = computed(() => {
  const { status, buyer } = applied.value;
  if (status === 'PAID' && !buyer) return '발송 대기 중인 주문이 없습니다.';
  if (status === null && !buyer) return '주문이 없습니다.';
  return '조건에 맞는 주문이 없습니다.';
});

function search() {
  applied.value = { ...form.value };
  gridRef.value?.instance.refresh();
}
function reset() {
  form.value = { status: null, buyer: '', orderNo: '' };
  search();
}

/**
 * 발송 처리는 운송장(택배사·송장번호) 입력이 필요해 confirm 대화상자로 처리할 수 없다.
 * 목록 위에 입력 패널을 띄우고, 어느 주문인지 함께 보여준다(그리드에서 행을 잃지 않게).
 */
const shipTarget = ref(null);
// 발송 패널 전용 에러 — 페이지 맨 위 error는 스크롤하면 안 보인다.
// 빈 송장번호 경고가 입력칸 바로 옆에 떠야 사용자가 알아챈다(2026-07-24 사용자 지적).
const shipError = ref('');
function openShip(row) {
  shipError.value = '';
  shipTarget.value = { id: row.id, buyer: row.buyerNickname, carrier: 'CJ', trackingNo: '' };
}
async function submitShip() {
  const trackingNo = shipTarget.value.trackingNo.trim();
  // 서버도 @NotBlank로 막지만 화면에서 먼저 거른다(왕복 절약).
  // 에러는 패널 안(shipError)에 띄운다 — 페이지 맨 위 error는 발송 패널을 연 상태에선 안 보인다.
  if (!trackingNo) {
    shipError.value = '송장번호를 입력해 주세요.';
    return;
  }
  shipError.value = '';
  try {
    await shipOrder(shipTarget.value.id, { carrier: shipTarget.value.carrier, trackingNo });
    shipTarget.value = null;
    gridRef.value?.instance.refresh();
    await loadCounts(); // 발송 대기 건수가 즉시 줄어드는 게 보이게
  } catch (e) {
    shipError.value = e.message;
  }
}

/**
 * 관리자 대행 취소 (2026-08-10, 백로그 B-25).
 *
 * ⚠ 발송 처리와 **같은 패널 패턴**이고 confirm 이 아니다 — 사유가 필수라 입력받을 자리가 필요하다.
 *    반품 승인·거절이 confirm 인 것과 갈리는 이유가 이거다(그쪽은 입력이 없다).
 * ⚠ 되돌릴 수 없는 조작이라 **무엇이 따라오는지**를 패널에 적는다(재고 복원·적립금 환불·고객 알림).
 */
const cancelTarget = ref(null);
const cancelError = ref('');
function openCancel(row) {
  cancelError.value = '';
  cancelTarget.value = { id: row.id, buyer: row.buyerNickname, orderNo: row.orderNo, reason: '' };
}
async function submitCancel() {
  const reason = cancelTarget.value.reason.trim();
  // 서버도 @NotBlank 로 막지만 화면에서 먼저 거른다(왕복 절약) — 송장번호와 같은 방식.
  if (!reason) {
    cancelError.value = '취소 사유를 입력해 주세요. 고객이 아닌 관리자가 취소한 주문은 사유가 유일한 단서입니다.';
    return;
  }
  cancelError.value = '';
  try {
    await adminCancelOrder(cancelTarget.value.id, reason);
    cancelTarget.value = null;
    gridRef.value?.instance.refresh();
    await loadCounts();
  } catch (e) {
    cancelError.value = e.message;
  }
}

async function onReturnApprove(row) {
  if (!window.confirm(`${row.buyerNickname}님의 반품을 승인할까요? 재고 복원 + 결제금액을 적립금으로 환불합니다.`)) return;
  shipError.value = ''; error.value = '';
  try {
    await approveReturn(row.id);
    gridRef.value?.instance.refresh();
    await loadCounts();
  } catch (e) { error.value = e.message; }
}
async function onReturnReject(row) {
  if (!window.confirm(`${row.buyerNickname}님의 반품을 거절할까요? 배송완료로 되돌립니다.`)) return;
  shipError.value = ''; error.value = '';
  try {
    await rejectReturn(row.id);
    gridRef.value?.instance.refresh();
    await loadCounts();
  } catch (e) { error.value = e.message; }
}

async function onDeliver(row) {
  if (!window.confirm(`${row.buyerNickname}님의 주문을 배송완료로 처리할까요?`)) return;
  error.value = '';
  try {
    await deliverOrder(row.id);
    gridRef.value?.instance.refresh();
    await loadCounts();
  } catch (e) {
    error.value = e.message;
  }
}

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}
</script>

<template>
  <section class="page">
    <!-- 셸(제목·필터·버튼)만 토큰/공용 클래스로. 표는 운영 화면이라 DataGrid 그대로 (DESIGN.md §7) -->
    <div class="mb-5">
      <h1 class="page-title">주문 관리</h1>
      <p class="muted mt-1">발송할 주문을 찾아 처리합니다.</p>
    </div>

    <div v-if="error" class="alert-error mb-4">{{ error }}</div>

    <!-- 상태 탭 + 건수: 발송할 게 몇 건인지 한눈에 -->
    <div class="mb-4 flex flex-wrap gap-1 border-b border-line">
      <button
        v-for="t in TABS"
        :key="t.value ?? 'all'"
        type="button"
        class="-mb-px flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm transition-colors focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand-600"
        :class="applied.status === t.value
          ? 'border-brand-600 font-medium text-ink-900'
          : 'border-transparent text-ink-500 hover:text-ink-900'"
        :aria-current="applied.status === t.value"
        @click="pickTab(t.value)"
      >
        {{ t.text }}
        <span class="badge" :class="applied.status === t.value ? 'badge-neutral' : 'bg-canvas text-ink-400'">
          {{ countOf(t.value) }}
        </span>
      </button>
    </div>

    <div class="card mb-4 flex flex-wrap items-end gap-3 p-4">
      <label class="field">
        <span class="field-label">구매자</span>
        <DxTextBox v-model:value="form.buyer" placeholder="닉네임" :width="180" @enter-key="search" />
      </label>
      <!-- CS에서 고객이 불러준 주문번호로 바로 찾는다 — 이게 주문번호를 만든 이유다. -->
      <label class="field">
        <span class="field-label">주문번호</span>
        <DxTextBox v-model:value="form.orderNo" placeholder="20260723-0026" :width="180" @enter-key="search" />
      </label>
      <div class="flex gap-2">
        <button type="button" class="btn btn-primary" @click="search">검색</button>
        <button type="button" class="btn btn-secondary" @click="reset">초기화</button>
      </div>
    </div>

    <!-- 운송장 입력(발송 처리). 어느 주문인지 함께 보여줘야 그리드에서 행을 잃지 않는다. -->
    <div v-if="shipTarget" class="card mb-4 p-4">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <h2 class="section-title">운송장 등록 — {{ shipTarget.buyer }}님의 주문</h2>
        <span class="muted">등록하면 발송완료로 바뀌고 고객이 배송을 조회할 수 있습니다.</span>
      </div>
      <div class="mt-3 flex flex-wrap items-end gap-3">
        <label class="block">
          <span class="muted mb-1 block">택배사</span>
          <select v-model="shipTarget.carrier" class="field">
            <option v-for="c in DELIVERY_CARRIERS" :key="c.value" :value="c.value">{{ c.text }}</option>
          </select>
        </label>
        <label class="block">
          <span class="muted mb-1 block">송장번호</span>
          <input v-model="shipTarget.trackingNo" class="field" placeholder="숫자만 입력" @keyup.enter="submitShip" />
        </label>
        <div class="flex gap-2">
          <button type="button" class="btn btn-primary" @click="submitShip">발송 처리</button>
          <button type="button" class="btn btn-secondary" @click="shipTarget = null; shipError = ''">취소</button>
        </div>
      </div>
      <p v-if="shipError" class="alert-error mt-3">{{ shipError }}</p>
    </div>

    <!--
      관리자 대행 취소(B-25). 발송 패널과 같은 자리·같은 모양 — 사유가 필수라 confirm 으로 못 한다.
      ⚠ 되돌릴 수 없으므로 **무엇이 따라오는지**를 적는다. 반품 승인 confirm 이 「재고 복원 + 적립금
        환불」을 말하는 것과 같은 규칙이다(조작 전에 결과를 읽게 한다).
    -->
    <div v-if="cancelTarget" class="card mb-4 p-4">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <h2 class="section-title">주문 취소 — {{ cancelTarget.buyer }}님의 주문 ({{ cancelTarget.orderNo }})</h2>
        <span class="muted">재고가 복원되고, 쓴 적립금은 돌려드리며, 고객에게 취소 알림이 갑니다. 되돌릴 수 없습니다.</span>
      </div>
      <div class="mt-3 flex flex-wrap items-end gap-3">
        <label class="block grow">
          <span class="muted mb-1 block">취소 사유 <strong>(필수)</strong></span>
          <input
            v-model="cancelTarget.reason"
            class="field w-full"
            placeholder="예) 고객 요청 — 전화 접수"
            maxlength="500"
            @keyup.enter="submitCancel"
          />
        </label>
        <div class="flex gap-2">
          <button type="button" class="btn btn-primary" @click="submitCancel">취소 처리</button>
          <button type="button" class="btn btn-secondary" @click="cancelTarget = null; cancelError = ''">닫기</button>
        </div>
      </div>
      <p v-if="cancelError" class="alert-error mt-3">{{ cancelError }}</p>
    </div>

    <DxDataGrid
      ref="gridRef"
      :data-source="store"
      :remote-operations="true"
      :show-borders="false"
      :show-column-lines="false"
      :column-auto-width="true"
      :hover-state-enabled="true"
      :no-data-text="noDataText"
    >
      <DxColumn data-field="orderNo" caption="주문번호" :width="140" />
      <DxColumn data-field="createdAt" caption="주문일시" :width="160" :calculate-display-value="(r) => fmt(r.createdAt)" />
      <DxColumn data-field="buyerNickname" caption="구매자" :width="130" />
      <DxColumn data-field="summary" caption="상품" />
      <!-- 고객이 본 숫자와 어긋나지 않게 **실제 받은 금액**(payAmount)을 보여준다. -->
      <DxColumn data-field="payAmount" caption="금액" :width="120" alignment="right" :calculate-display-value="(r) => priceText(r.payAmount)" />
      <DxColumn data-field="status" caption="상태" :width="100" alignment="center" cell-template="statusCell" />
      <DxColumn caption="처리" :width="150" alignment="center" cell-template="actionCell" />

      <DxPaging :page-size="10" />
      <DxPager :show-page-size-selector="true" :allowed-page-sizes="[10, 20, 50]" :show-info="true" info-text="{2}건 중 {0}-{1}" />

      <template #statusCell="{ data }">
        <span class="badge" :class="orderStatusClass(data.data.status)">
          {{ orderStatusText(data.data.status) }}
        </span>
      </template>

      <template #actionCell="{ data }">
        <div class="flex justify-center gap-1">
          <button
            v-if="data.data.status === 'PAID'"
            type="button"
            class="btn btn-secondary btn-sm"
            @click="openShip(data.data)"
          >발송</button>
          <button
            v-if="data.data.status === 'SHIPPED'"
            type="button"
            class="btn btn-secondary btn-sm"
            @click="onDeliver(data.data)"
          >배송완료</button>
          <template v-if="data.data.status === 'RETURN_REQUESTED'">
            <button type="button" class="btn btn-secondary btn-sm" @click="onReturnApprove(data.data)">반품승인</button>
            <button type="button" class="btn btn-ghost btn-sm" @click="onReturnReject(data.data)">거절</button>
          </template>
          <!--
            취소는 발송 전(ORDERED·PAID)에만 뜬다 — 서버의 isCancellable() 과 같은 조건이다.
            ⚠ 발송 후에 버튼이 보이면 눌러 보고 400 을 받는데, 그건 화면이 «될 것처럼» 보여 준 탓이다.
          -->
          <button
            v-if="data.data.status === 'ORDERED' || data.data.status === 'PAID'"
            type="button"
            class="btn btn-ghost btn-sm"
            @click="openCancel(data.data)"
          >취소</button>
          <button type="button" class="btn btn-ghost btn-sm" @click="router.push(`/orders/${data.data.id}`)">상세</button>
        </div>
      </template>
    </DxDataGrid>
  </section>
</template>
