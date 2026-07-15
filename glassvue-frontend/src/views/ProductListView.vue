<script setup>
import { reactive, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import CustomStore from 'devextreme/data/custom_store';
import { DxDataGrid, DxColumn, DxPaging, DxPager } from 'devextreme-vue/data-grid';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxNumberBox } from 'devextreme-vue/number-box';
import { DxSelectBox } from 'devextreme-vue/select-box';
import { DxButton } from 'devextreme-vue/button';
import { fetchProducts, STATUS_OPTIONS, statusText, priceText } from '../api/product';
import { fetchCategories } from '../api/category';
import { authState } from '../stores/auth';

const router = useRouter();
const gridRef = ref(null);
const categories = ref([]);

const isAdmin = () => authState.user?.role === 'ADMIN';

const form = reactive({ name: '', categoryId: null, minPrice: null, maxPrice: null, status: null });
let applied = {};

const store = new CustomStore({
  key: 'id',
  async load(loadOptions) {
    const take = loadOptions.take ?? 10;
    const skip = loadOptions.skip ?? 0;
    const page = Math.floor(skip / take);
    const res = await fetchProducts({ ...applied, page, size: take });
    return { data: res.content, totalCount: res.totalElements };
  },
});

onMounted(async () => {
  try {
    categories.value = await fetchCategories();
  } catch (e) {
    /* 카테고리 로드 실패해도 목록은 동작 */
  }
});

function reload() {
  const inst = gridRef.value?.instance;
  if (inst) {
    inst.pageIndex(0);
    inst.refresh();
  }
}
function search() {
  applied = {
    name: form.name?.trim() || undefined,
    categoryId: form.categoryId || undefined,
    minPrice: form.minPrice ?? undefined,
    maxPrice: form.maxPrice ?? undefined,
    status: form.status || undefined,
  };
  reload();
}
function reset() {
  form.name = '';
  form.categoryId = null;
  form.minPrice = null;
  form.maxPrice = null;
  form.status = null;
  applied = {};
  reload();
}
function onRowClick(e) {
  if (e.rowType === 'data' && e.data) router.push(`/products/${e.data.id}`);
}
const priceCell = (r) => priceText(r.price);
const statusCell = (r) => statusText(r.status);
</script>

<template>
  <section class="p-6">
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-slate-800">상품</h2>
      <div class="flex gap-2" v-if="isAdmin()">
        <DxButton text="카테고리 관리" styling-mode="outlined" @click="router.push('/admin/categories')" />
        <DxButton text="+ 상품 등록" type="default" styling-mode="contained" @click="router.push('/products/new')" />
      </div>
    </div>

    <!-- 검색 -->
    <div class="mb-4 flex flex-wrap items-end gap-3 rounded-lg border bg-white p-4">
      <label class="flex flex-col gap-1">
        <span class="text-xs text-slate-500">상품명</span>
        <DxTextBox v-model:value="form.name" :width="160" @enter-key="search" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-xs text-slate-500">카테고리</span>
        <DxSelectBox v-model:value="form.categoryId" :items="categories" value-expr="id" display-expr="name"
          :show-clear-button="true" placeholder="전체" :width="140" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-xs text-slate-500">상태</span>
        <DxSelectBox v-model:value="form.status" :items="STATUS_OPTIONS" value-expr="value" display-expr="text"
          :show-clear-button="true" placeholder="전체" :width="110" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-xs text-slate-500">최소가</span>
        <DxNumberBox v-model:value="form.minPrice" :min="0" :show-clear-button="true" :width="110" format="#,##0" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-xs text-slate-500">최대가</span>
        <DxNumberBox v-model:value="form.maxPrice" :min="0" :show-clear-button="true" :width="110" format="#,##0" />
      </label>
      <div class="flex gap-2">
        <DxButton text="검색" type="default" styling-mode="contained" @click="search" />
        <DxButton text="초기화" styling-mode="outlined" @click="reset" />
      </div>
    </div>

    <!-- 목록 -->
    <DxDataGrid
      ref="gridRef"
      :data-source="store"
      :remote-operations="{ paging: true }"
      :show-borders="true"
      :column-auto-width="true"
      :hover-state-enabled="true"
      no-data-text="조건에 맞는 상품이 없습니다."
      @row-click="onRowClick"
      class="cursor-pointer-grid"
    >
      <DxColumn data-field="name" caption="상품명" />
      <DxColumn data-field="categoryName" caption="카테고리" :width="120" />
      <DxColumn data-field="price" caption="가격" :width="120" alignment="right" :calculate-display-value="priceCell" />
      <DxColumn data-field="stock" caption="재고" :width="90" alignment="center" />
      <DxColumn data-field="status" caption="상태" :width="90" alignment="center" :calculate-display-value="statusCell" />

      <DxPaging :page-size="10" />
      <DxPager :show-page-size-selector="true" :allowed-page-sizes="[10, 20, 50]" :show-info="true" info-text="{2}개 중 {0}-{1}" />
    </DxDataGrid>
  </section>
</template>

<style scoped>
.cursor-pointer-grid :deep(.dx-data-row) {
  cursor: pointer;
}
</style>
