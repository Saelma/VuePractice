<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import CustomStore from 'devextreme/data/custom_store';
import {
  DxDataGrid,
  DxColumn,
  DxPaging,
  DxPager,
} from 'devextreme-vue/data-grid';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxDateBox } from 'devextreme-vue/date-box';
import { DxButton } from 'devextreme-vue/button';
import { fetchNotices } from '../api/notice';

const router = useRouter();
const gridRef = ref(null);

const form = reactive({ title: '', author: '', fromDate: null, toDate: null });
let applied = {};

function toLocalDate(d) {
  if (!d) return null;
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

const store = new CustomStore({
  key: 'id',
  async load(loadOptions) {
    const take = loadOptions.take ?? 10;
    const skip = loadOptions.skip ?? 0;
    const page = Math.floor(skip / take);
    const res = await fetchNotices({ ...applied, page, size: take });
    return { data: res.content, totalCount: res.totalElements };
  },
});

function reloadFromFirstPage() {
  const inst = gridRef.value?.instance;
  if (inst) {
    inst.pageIndex(0);
    inst.refresh();
  }
}

function search() {
  applied = {
    title: form.title?.trim() || undefined,
    author: form.author?.trim() || undefined,
    fromDate: toLocalDate(form.fromDate),
    toDate: toLocalDate(form.toDate),
  };
  reloadFromFirstPage();
}

function reset() {
  form.title = '';
  form.author = '';
  form.fromDate = null;
  form.toDate = null;
  applied = {};
  reloadFromFirstPage();
}

function formatDate(rowData) {
  if (!rowData.createdAt) return '';
  return new Date(rowData.createdAt).toLocaleString('ko-KR');
}

function onRowClick(e) {
  if (e.rowType === 'data' && e.data) {
    router.push(`/notices/${e.data.id}`);
  }
}
</script>

<template>
  <section class="p-6">
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-slate-800">공지 목록</h2>
      <DxButton
        text="+ 새 공지"
        type="default"
        styling-mode="contained"
        @click="router.push('/notices/new')"
      />
    </div>

    <!-- 검색 바 -->
    <div class="mb-4 flex flex-wrap items-end gap-3 rounded-lg border bg-white p-4">
      <label class="flex flex-col gap-1">
        <span class="text-xs text-slate-500">제목</span>
        <DxTextBox v-model:value="form.title" placeholder="제목 검색" :width="180" @enter-key="search" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-xs text-slate-500">작성자</span>
        <DxTextBox v-model:value="form.author" placeholder="작성자 검색" :width="140" @enter-key="search" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-xs text-slate-500">작성일(시작)</span>
        <DxDateBox v-model:value="form.fromDate" type="date" display-format="yyyy-MM-dd" :width="150" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-xs text-slate-500">작성일(종료)</span>
        <DxDateBox v-model:value="form.toDate" type="date" display-format="yyyy-MM-dd" :width="150" />
      </label>
      <div class="flex gap-2">
        <DxButton text="검색" type="default" styling-mode="contained" @click="search" />
        <DxButton text="초기화" styling-mode="outlined" @click="reset" />
      </div>
    </div>

    <!-- 목록 (행 클릭 → 상세) -->
    <DxDataGrid
      ref="gridRef"
      :data-source="store"
      :remote-operations="{ paging: true }"
      :show-borders="true"
      :column-auto-width="true"
      :hover-state-enabled="true"
      no-data-text="조건에 맞는 공지가 없습니다."
      @row-click="onRowClick"
      class="cursor-pointer-grid"
    >
      <DxColumn data-field="pinned" caption="고정" :width="60" alignment="center" cell-template="pinnedCell" />
      <DxColumn data-field="title" caption="제목" />
      <DxColumn data-field="author" caption="작성자" :width="120" />
      <DxColumn data-field="viewCount" caption="조회" :width="80" alignment="center" />
      <DxColumn data-field="createdAt" caption="작성일" :width="190" :calculate-display-value="formatDate" />

      <DxPaging :page-size="10" />
      <DxPager
        :show-page-size-selector="true"
        :allowed-page-sizes="[10, 20, 50]"
        :show-info="true"
        info-text="{2}건 중 {0}-{1}"
      />

      <template #pinnedCell="{ data }">
        <span v-if="data.value" title="상단 고정">📌</span>
      </template>
    </DxDataGrid>
  </section>
</template>

<style scoped>
.cursor-pointer-grid :deep(.dx-data-row) {
  cursor: pointer;
}
</style>
