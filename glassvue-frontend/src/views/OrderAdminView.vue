<script setup>
/**
 * 관리자 주문 목록 — 발송 처리 동선의 시작점.
 *
 * 이 화면이 생기기 전엔 주문 id를 알아야만 발송할 수 있어서, 실제로는 DB를 직접 뒤져야 했다.
 * 그래서 기본 필터를 **결제완료(PAID)** 로 둔다 — 관리자가 이 화면에 오는 이유가
 * "발송할 주문 찾기"이기 때문. 전체를 보려면 필터를 바꾸면 된다.
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import CustomStore from 'devextreme/data/custom_store';
import { DxDataGrid, DxColumn, DxPaging, DxPager } from 'devextreme-vue/data-grid';
import { DxTextBox } from 'devextreme-vue/text-box';
import {
  fetchAdminOrders, fetchAdminOrderCounts, shipOrder, deliverOrder,
  orderStatusText, orderStatusClass, ORDER_STATUS_TEXT, DELIVERY_CARRIERS,
} from '../api/order';
import { priceText } from '../api/product';

const router = useRouter();
const error = ref('');
const form = ref({ status: 'PAID', buyer: '', orderNo: '' }); // 발송 대기 주문이 기본
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
function openShip(row) {
  shipTarget.value = { id: row.id, buyer: row.buyerNickname, carrier: 'CJ', trackingNo: '' };
}
async function submitShip() {
  const trackingNo = shipTarget.value.trackingNo.trim();
  // 서버도 @NotBlank로 막지만 화면에서 먼저 거른다(왕복 절약).
  if (!trackingNo) {
    error.value = '송장번호를 입력해 주세요.';
    return;
  }
  error.value = '';
  try {
    await shipOrder(shipTarget.value.id, { carrier: shipTarget.value.carrier, trackingNo });
    shipTarget.value = null;
    gridRef.value?.instance.refresh();
    await loadCounts(); // 발송 대기 건수가 즉시 줄어드는 게 보이게
  } catch (e) {
    error.value = e.message;
  }
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
          <button type="button" class="btn btn-secondary" @click="shipTarget = null">취소</button>
        </div>
      </div>
    </div>

    <DxDataGrid
      ref="gridRef"
      :data-source="store"
      :remote-operations="true"
      :show-borders="true"
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
          <button type="button" class="btn btn-ghost btn-sm" @click="router.push(`/orders/${data.data.id}`)">상세</button>
        </div>
      </template>
    </DxDataGrid>
  </section>
</template>
