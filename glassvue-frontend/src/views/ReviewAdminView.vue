<script setup>
/**
 * 리뷰 관리 (관리자) — 2026-08-04, 백로그 B-18.
 *
 * 이 화면이 생기기 전까지 **관리자가 리뷰에 손댈 방법이 없었다**(관리자 리뷰 API 0개, 실측).
 * 작성자 본인만 지울 수 있어서 욕설·광고 리뷰가 올라오면 그대로 남았다.
 *
 * ⚠ **삭제가 아니라 숨김**이다 — 관리자가 잘못 판단할 수 있고, 그때 원문이 남아 있어야 되돌린다.
 *    그래서 목록은 **숨긴 것도 함께** 보여준다(안 보이면 되돌릴 수가 없다).
 *
 * ⚠ 숨기면 상품의 **평균 별점·리뷰 수가 함께 움직인다**(서버가 집계를 다시 낸다). 화면에도 그렇게
 *    적어 둔다 — 관리자가 "글 하나 감추는 것" 으로만 알면 별점이 왜 변했는지 못 짚는다.
 */
import { ref } from 'vue';
import CustomStore from 'devextreme/data/custom_store';
import { DxDataGrid, DxColumn, DxPaging, DxPager } from 'devextreme-vue/data-grid';
import { DxSelectBox } from 'devextreme-vue/select-box';
import {
  fetchAdminReviews, hideReview, unhideReview, REVIEW_HIDDEN_OPTIONS,
} from '../api/review';

const filter = ref({ hidden: null });
const gridRef = ref(null);
const error = ref('');

const store = new CustomStore({
  key: 'id',
  load: async (options) => {
    const size = options.take || 20;
    const page = Math.floor((options.skip || 0) / size);
    try {
      // ⚠ hidden 은 null 을 그대로 넘긴다 — apiGet 이 null 만 빼고 **false 는 보낸다**.
      //    여기서 `hidden || undefined` 같은 falsy 처리를 하면 「보이는 것만」이 「전체」가 된다.
      const res = await fetchAdminReviews({ hidden: filter.value.hidden, page, size });
      error.value = '';
      return { data: res.content, totalCount: res.totalElements };
    } catch (e) {
      // 실패를 빈 목록으로 위장하지 않는다 — 0건으로 그리면 "볼 리뷰가 없다"로 읽힌다(DESIGN §7).
      error.value = e.message;
      throw e;
    }
  },
});

function applyFilter() {
  gridRef.value?.instance.refresh();
}

async function toggleHidden(row) {
  const willHide = !row.hidden;
  const message = willHide
    ? '이 리뷰를 숨길까요?\n\n상품 목록에서 사라지고 평균 별점·리뷰 수에서도 빠집니다.\n(작성자 본인에게도 보이지 않습니다. 되돌릴 수 있습니다.)'
    : '숨김을 해제할까요?\n\n다시 보이고 평균 별점·리뷰 수에도 들어갑니다.';
  if (!window.confirm(message)) return;

  error.value = '';
  try {
    await (willHide ? hideReview(row.id) : unhideReview(row.id));
    gridRef.value?.instance.refresh();
  } catch (e) {
    error.value = e.message;
  }
}

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}

/** 별점을 숫자로만 두면 훑기 어렵다 — 낮은 별점이 대개 손볼 대상이라 눈에 띄게 한다. */
function ratingBadge(rating) {
  if (rating <= 2) return 'badge-danger';
  if (rating === 3) return 'badge-warning';
  return 'badge-neutral';
}
</script>

<template>
  <section class="page">
    <!-- 셸만 토큰/공용 클래스로. 표는 운영 화면이라 DataGrid 그대로 (DESIGN.md §7) -->
    <div class="mb-5">
      <h1 class="page-title">리뷰 관리</h1>
      <p class="muted mt-1">
        부적절한 리뷰를 <strong>숨김</strong> 처리합니다. 삭제가 아니라 되돌릴 수 있고,
        숨기면 상품의 <strong>평균 별점·리뷰 수에서도 빠집니다.</strong>
      </p>
    </div>

    <p v-if="error" class="alert-error mb-3">리뷰를 불러오지 못했습니다. {{ error }}</p>

    <div class="card mb-4 flex flex-wrap items-end gap-3 p-4">
      <label class="field">
        <span class="field-label">숨김 상태</span>
        <DxSelectBox
          v-model:value="filter.hidden"
          :items="REVIEW_HIDDEN_OPTIONS"
          display-expr="text"
          value-expr="value"
          :width="160"
          @value-changed="applyFilter"
        />
      </label>
    </div>

    <DxDataGrid
      ref="gridRef"
      :data-source="store"
      :remote-operations="true"
      :show-borders="false"
      :show-column-lines="false"
      :column-auto-width="true"
      :hover-state-enabled="true"
      :word-wrap-enabled="true"
      no-data-text="조건에 맞는 리뷰가 없습니다."
    >
      <DxColumn data-field="createdAt" caption="작성" :width="150" :calculate-display-value="(r) => fmt(r.createdAt)" />
      <DxColumn data-field="productName" caption="상품" :width="180" />
      <DxColumn data-field="author" caption="작성자" :width="120" />
      <DxColumn data-field="rating" caption="별점" :width="80" alignment="center" cell-template="ratingCell" />
      <DxColumn data-field="content" caption="내용" />
      <DxColumn data-field="hidden" caption="상태" :width="90" alignment="center" cell-template="stateCell" />
      <DxColumn caption="처리" :width="110" alignment="center" cell-template="actionCell" />

      <DxPaging :page-size="20" />
      <DxPager :show-page-size-selector="true" :allowed-page-sizes="[20, 50]" :show-info="true" info-text="{2}건 중 {0}-{1}" />

      <template #ratingCell="{ data }">
        <span class="badge" :class="ratingBadge(data.data.rating)">{{ data.data.rating }}점</span>
      </template>
      <template #stateCell="{ data }">
        <span v-if="data.data.hidden" class="badge badge-danger">숨김</span>
        <span v-else class="badge badge-success">노출</span>
      </template>
      <template #actionCell="{ data }">
        <button
          type="button"
          class="btn btn-sm"
          :class="data.data.hidden ? 'btn-secondary' : 'btn-danger'"
          @click="toggleHidden(data.data)"
        >{{ data.data.hidden ? '해제' : '숨김' }}</button>
      </template>
    </DxDataGrid>
  </section>
</template>
