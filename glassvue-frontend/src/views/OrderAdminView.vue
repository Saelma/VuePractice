<script setup>
/**
 * 관리자 주문 목록 — 발송 처리 동선의 시작점.
 *
 * 이 화면이 생기기 전엔 주문 id를 알아야만 발송할 수 있어서, 실제로는 DB를 직접 뒤져야 했다.
 * 그래서 기본 필터를 **결제완료(PAID)** 로 둔다 — 관리자가 이 화면에 오는 이유가
 * "발송할 주문 찾기"이기 때문. 전체를 보려면 필터를 바꾸면 된다.
 */
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import CustomStore from 'devextreme/data/custom_store';
import { DxDataGrid, DxColumn, DxPaging, DxPager } from 'devextreme-vue/data-grid';
import { DxSelectBox } from 'devextreme-vue/select-box';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxButton } from 'devextreme-vue/button';
import { fetchAdminOrders, shipOrder, orderStatusText, orderStatusClass, ORDER_STATUS_OPTIONS } from '../api/order';
import { priceText } from '../api/product';

const router = useRouter();
const error = ref('');
const form = ref({ status: 'PAID', buyer: '' }); // 발송 대기 주문이 기본
const applied = ref({ ...form.value });
const gridRef = ref(null);

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
  form.value = { status: null, buyer: '' };
  search();
}

async function onShip(row) {
  if (!window.confirm(`${row.buyerNickname}님의 주문을 발송 처리할까요?`)) return;
  error.value = '';
  try {
    await shipOrder(row.id);
    gridRef.value?.instance.refresh();
  } catch (e) {
    error.value = e.message;
  }
}

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}
</script>

<template>
  <section class="p-6">
    <div class="mb-4 flex items-center gap-3">
      <h2 class="text-xl font-semibold text-slate-800">주문 관리</h2>
      <span class="text-sm text-slate-500">발송할 주문을 찾아 처리합니다.</span>
    </div>

    <div v-if="error" class="mb-4 rounded bg-red-50 p-3 text-red-600">{{ error }}</div>

    <div class="mb-4 flex flex-wrap items-end gap-3 rounded-lg border bg-white p-4">
      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">상태</span>
        <DxSelectBox
          v-model:value="form.status"
          :items="ORDER_STATUS_OPTIONS"
          value-expr="value"
          display-expr="text"
          :width="140"
        />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">구매자</span>
        <DxTextBox v-model:value="form.buyer" placeholder="닉네임" :width="180" @enter-key="search" />
      </label>
      <DxButton text="검색" type="default" styling-mode="contained" @click="search" />
      <DxButton text="초기화" styling-mode="outlined" @click="reset" />
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
      <DxColumn data-field="createdAt" caption="주문일시" :width="160" :calculate-display-value="(r) => fmt(r.createdAt)" />
      <DxColumn data-field="buyerNickname" caption="구매자" :width="130" />
      <DxColumn data-field="summary" caption="상품" />
      <DxColumn data-field="totalPrice" caption="금액" :width="120" alignment="right" :calculate-display-value="(r) => priceText(r.totalPrice)" />
      <DxColumn data-field="status" caption="상태" :width="100" alignment="center" cell-template="statusCell" />
      <DxColumn caption="처리" :width="150" alignment="center" cell-template="actionCell" />

      <DxPaging :page-size="10" />
      <DxPager :show-page-size-selector="true" :allowed-page-sizes="[10, 20, 50]" :show-info="true" info-text="{2}건 중 {0}-{1}" />

      <template #statusCell="{ data }">
        <span class="rounded px-2 py-0.5 text-sm" :class="orderStatusClass(data.data.status)">
          {{ orderStatusText(data.data.status) }}
        </span>
      </template>

      <template #actionCell="{ data }">
        <div class="flex justify-center gap-1">
          <DxButton
            v-if="data.data.status === 'PAID'"
            text="발송" type="default" styling-mode="contained" @click="onShip(data.data)"
          />
          <DxButton text="상세" styling-mode="outlined" @click="router.push(`/orders/${data.data.id}`)" />
        </div>
      </template>
    </DxDataGrid>
  </section>
</template>
