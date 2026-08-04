<script setup>
/**
 * 상품의 재고 변경 이력 (2026-08-04, 백로그 B-19).
 *
 * `product_variant.stock` 은 현재값 하나뿐이라 "어제 10개였는데 왜 3개지"에 답할 수 없었다.
 * 이 패널이 그 원장을 보여준다 — 관리자가 재고를 고치러 **이미 와 있는 화면**(상품 수정)에 붙인다.
 *
 * ⚠ **지금 옵션 목록에 없는 이름이 나올 수 있다.** 상품을 저장하면 옵션이 통째로 교체되므로,
 *    이력은 옵션 id 가 아니라 **상품 + 옵션명**으로 이어진다(삭제된 옵션의 과거 줄도 그대로 보인다).
 *    "모르는 옵션"이라고 감추면 이력을 만든 이유가 사라진다.
 *
 * ⚠ **합계로 현재 재고를 검산하지 않는다.** V39(2026-08-04) 이전 변동은 기록이 없다 —
 *    백필하지 않았기 때문이다. 그래서 오래된 상품은 이력이 **비어 있는 게 정상**이고,
 *    빈 상태 문구가 그걸 말해 줘야 한다(안 그러면 관리자가 고장으로 읽는다).
 */
import { ref, onMounted } from 'vue';
import CustomStore from 'devextreme/data/custom_store';
import { DxDataGrid, DxColumn, DxPaging, DxPager } from 'devextreme-vue/data-grid';
import { fetchStockHistory, stockReasonText, stockDeltaText } from '../api/product';

const props = defineProps({ productId: { type: String, required: true } });

const error = ref('');
const loaded = ref(false);

const store = new CustomStore({
  key: 'id',
  load: async (options) => {
    const size = options.take || 20;
    const page = Math.floor((options.skip || 0) / size);
    try {
      const res = await fetchStockHistory(props.productId, { page, size });
      error.value = '';
      return { data: res.content, totalCount: res.totalElements };
    } catch (e) {
      // 이력은 부가 정보다 — 못 읽어도 상품 수정은 계속할 수 있어야 한다. 다만 **빈 목록으로 위장하지
      // 않는다**: 실패를 0건으로 그리면 "변동이 없었다"로 읽힌다(대시보드에서 배운 것, DESIGN §7).
      error.value = e.message;
      throw e;
    } finally {
      loaded.value = true;
    }
  },
});

onMounted(() => {
  loaded.value = false;
});

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}

/** 사유별 뱃지: 재고가 줄어드는 것은 주의(warning), 느는 것은 성공, 관리자 조작은 중립. */
function reasonBadge(reason) {
  if (reason === 'ORDER') return 'badge-warning';
  if (reason === 'CANCEL' || reason === 'RETURN') return 'badge-success';
  return 'badge-neutral';
}

/**
 * 「누가·왜」 열 — 사유에 따라 채워지는 칸이 다르다.
 * 주문 경로는 행위자를 안 남기고 주문으로 되짚으므로 주문번호 자리를 보여준다.
 */
function sourceText(row) {
  if (row.actorName) return row.actorName;
  if (row.orderId) return '주문';
  return '—';
}
</script>

<template>
  <div class="field">
    <span class="field-label">재고 변경 이력</span>
    <p class="muted mb-2">
      주문·취소·반품·관리자 편집으로 재고가 바뀐 기록입니다. 변동이 없는 저장은 남지 않습니다.
    </p>

    <p v-if="error" class="alert-error mb-2">재고 이력을 불러오지 못했습니다. {{ error }}</p>

    <DxDataGrid
      :data-source="store"
      :remote-operations="true"
      :show-borders="false"
      :show-column-lines="false"
      :column-auto-width="true"
      :hover-state-enabled="true"
      no-data-text="아직 기록된 재고 변동이 없습니다. (2026-08-04 이전의 변동은 기록되지 않았습니다)"
    >
      <DxColumn data-field="createdAt" caption="일시" :width="170" :calculate-display-value="(r) => fmt(r.createdAt)" />
      <DxColumn data-field="variantName" caption="옵션" :width="140" />
      <DxColumn data-field="reason" caption="사유" :width="110" alignment="center" cell-template="reasonCell" />
      <DxColumn data-field="quantity" caption="변동" :width="90" alignment="right" cell-template="deltaCell" />
      <DxColumn data-field="stockAfter" caption="변동 후 재고" :width="110" alignment="right" />
      <DxColumn caption="누가 · 왜" :calculate-cell-value="sourceText" />

      <DxPaging :page-size="20" />
      <DxPager :show-page-size-selector="true" :allowed-page-sizes="[20, 50]" :show-info="true" info-text="{2}건 중 {0}-{1}" />

      <template #reasonCell="{ data }">
        <span class="badge" :class="reasonBadge(data.data.reason)">{{ stockReasonText(data.data.reason) }}</span>
      </template>
      <template #deltaCell="{ data }">
        <span class="tabular-nums font-medium" :class="data.data.quantity < 0 ? 'text-danger' : 'text-success'">
          {{ stockDeltaText(data.data.quantity) }}
        </span>
      </template>
    </DxDataGrid>
  </div>
</template>
