<script setup>
import CustomStore from 'devextreme/data/custom_store';
import {
  DxDataGrid,
  DxColumn,
  DxPaging,
  DxPager,
} from 'devextreme-vue/data-grid';
import { fetchNotices } from '../api/notice';

// DevExtreme의 서버 페이징: load(loadOptions)에서 skip/take를 page/size로 변환해
// 백엔드에 요청하고 { data, totalCount } 형태로 돌려준다.
const store = new CustomStore({
  key: 'id',
  async load(loadOptions) {
    const take = loadOptions.take ?? 10;
    const skip = loadOptions.skip ?? 0;
    const page = Math.floor(skip / take);

    const res = await fetchNotices({ page, size: take });
    return { data: res.content, totalCount: res.totalElements };
  },
});

function formatDate(rowData) {
  if (!rowData.createdAt) return '';
  return new Date(rowData.createdAt).toLocaleString('ko-KR');
}
</script>

<template>
  <section class="p-6">
    <h2 class="mb-4 text-xl font-semibold text-slate-800">공지 목록</h2>

    <DxDataGrid
      :data-source="store"
      :remote-operations="{ paging: true }"
      :show-borders="true"
      :column-auto-width="true"
      :hover-state-enabled="true"
      no-data-text="등록된 공지가 없습니다."
    >
      <DxColumn
        data-field="pinned"
        caption="고정"
        :width="60"
        alignment="center"
        cell-template="pinnedCell"
      />
      <DxColumn data-field="title" caption="제목" />
      <DxColumn data-field="author" caption="작성자" :width="120" />
      <DxColumn
        data-field="viewCount"
        caption="조회"
        :width="80"
        alignment="center"
      />
      <DxColumn
        data-field="createdAt"
        caption="작성일"
        :width="190"
        :calculate-display-value="formatDate"
      />

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
