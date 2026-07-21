<script setup>
/**
 * 상품 목록 — 고객 화면이라 표(DataGrid)가 아니라 **카드 그리드**로 보여준다(DESIGN.md §7).
 * 이미지·이름·가격이 먼저 읽히는 게 목적이고, 정렬·밀도가 중요한 관리자 화면은 DataGrid를 유지한다.
 * 필터는 DX 입력 컨트롤을 그대로 쓴다(DESIGN.md §6 — 고객 화면도 입력 컨트롤 정도는 DX).
 */
import { reactive, ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxNumberBox } from 'devextreme-vue/number-box';
import { DxSelectBox } from 'devextreme-vue/select-box';
import { DxButton } from 'devextreme-vue/button';
import { fetchProducts, STATUS_OPTIONS, statusText, priceText } from '../api/product';
import { fetchCategories } from '../api/category';
import { authState } from '../stores/auth';
import StarRating from '../components/StarRating.vue';

const router = useRouter();
const categories = ref([]);
const isAdmin = computed(() => authState.user?.role === 'ADMIN');

const SIZE = 12; // 카드 그리드라 표(10)보다 한 페이지에 조금 더 담는다
const items = ref([]);
const page = ref(0);
const totalPages = ref(0);
const totalElements = ref(0);
const loading = ref(true);
const error = ref('');

const form = reactive({ name: '', categoryId: null, minPrice: null, maxPrice: null, status: null });
let applied = {};
const hasFilter = ref(false); // 빈 목록 문구를 상황에 맞게 고르기 위해(7/20 §8-7 교훈)

async function load(p = 0) {
  loading.value = true;
  error.value = '';
  try {
    const res = await fetchProducts({ ...applied, page: p, size: SIZE });
    items.value = res.content;
    page.value = res.page;
    totalPages.value = res.totalPages;
    totalElements.value = res.totalElements;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  load(0);
  try {
    categories.value = await fetchCategories();
  } catch (e) {
    /* 카테고리 로드 실패해도 목록은 동작 */
  }
});

function search() {
  applied = {
    name: form.name?.trim() || undefined,
    categoryId: form.categoryId || undefined,
    minPrice: form.minPrice ?? undefined,
    maxPrice: form.maxPrice ?? undefined,
    status: form.status || undefined,
  };
  hasFilter.value = Object.values(applied).some((v) => v !== undefined);
  load(0);
}
function reset() {
  form.name = '';
  form.categoryId = null;
  form.minPrice = null;
  form.maxPrice = null;
  form.status = null;
  applied = {};
  hasFilter.value = false;
  load(0);
}

const thumbOf = (p) => (p.images && p.images.length ? p.images[0].thumbUrl : null);
</script>

<template>
  <section class="px-4 py-6 sm:px-6">
    <!-- 머리: 제목 + 관리자 액션 -->
    <div class="mb-5 flex items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold tracking-tight text-ink-900">상품</h1>
        <p v-if="!loading" class="mt-1 text-xs text-ink-500">{{ totalElements }}개</p>
      </div>
      <div v-if="isAdmin" class="flex gap-2">
        <DxButton text="카테고리 관리" styling-mode="outlined" @click="router.push('/admin/categories')" />
        <DxButton text="+ 상품 등록" type="default" styling-mode="contained" @click="router.push('/products/new')" />
      </div>
    </div>

    <!-- 필터 -->
    <div class="mb-6 flex flex-wrap items-end gap-3 rounded-card border border-line bg-surface p-4 shadow-card">
      <label class="flex flex-col gap-1">
        <span class="text-xs text-ink-500">상품명</span>
        <DxTextBox v-model:value="form.name" :width="160" @enter-key="search" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-xs text-ink-500">카테고리</span>
        <DxSelectBox v-model:value="form.categoryId" :items="categories" value-expr="id" display-expr="name"
          :show-clear-button="true" placeholder="전체" :width="140" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-xs text-ink-500">상태</span>
        <DxSelectBox v-model:value="form.status" :items="STATUS_OPTIONS" value-expr="value" display-expr="text"
          :show-clear-button="true" placeholder="전체" :width="110" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-xs text-ink-500">최소가</span>
        <DxNumberBox v-model:value="form.minPrice" :min="0" :show-clear-button="true" :width="110" format="#,##0" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-xs text-ink-500">최대가</span>
        <DxNumberBox v-model:value="form.maxPrice" :min="0" :show-clear-button="true" :width="110" format="#,##0" />
      </label>
      <div class="flex gap-2">
        <DxButton text="검색" type="default" styling-mode="contained" @click="search" />
        <DxButton text="초기화" styling-mode="outlined" @click="reset" />
      </div>
    </div>

    <div v-if="error" class="rounded-card border border-line bg-red-50 p-4 text-sm text-danger">{{ error }}</div>

    <!-- 로딩: 텍스트 대신 스켈레톤으로 레이아웃을 미리 잡는다 (DESIGN.md §5) -->
    <div v-else-if="loading" class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      <div v-for="n in 8" :key="n" class="overflow-hidden rounded-card border border-line bg-surface shadow-card">
        <div class="aspect-square animate-pulse bg-slate-100"></div>
        <div class="space-y-2 p-4">
          <div class="h-3 w-16 animate-pulse rounded bg-slate-100"></div>
          <div class="h-4 w-3/4 animate-pulse rounded bg-slate-100"></div>
          <div class="h-5 w-24 animate-pulse rounded bg-slate-100"></div>
        </div>
      </div>
    </div>

    <!-- 빈 상태: 필터 때문에 빈 것과 정말 없는 것을 구분한다 -->
    <div v-else-if="!items.length" class="flex flex-col items-center gap-3 py-16 text-center">
      <span class="text-4xl">{{ hasFilter ? '🔍' : '🗂️' }}</span>
      <p class="text-sm text-ink-500">
        {{ hasFilter ? '조건에 맞는 상품이 없어요.' : '아직 등록된 상품이 없어요.' }}
      </p>
      <DxButton v-if="hasFilter" text="필터 초기화" styling-mode="outlined" @click="reset" />
      <DxButton v-else-if="isAdmin" text="+ 상품 등록" type="default" styling-mode="contained"
        @click="router.push('/products/new')" />
    </div>

    <!-- 카드 그리드 -->
    <div v-else class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      <button
        v-for="p in items"
        :key="p.id"
        type="button"
        class="group overflow-hidden rounded-card border border-line bg-surface text-left shadow-card transition duration-200 hover:-translate-y-0.5 hover:shadow-lift focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
        @click="router.push(`/products/${p.id}`)"
      >
        <div class="aspect-square overflow-hidden bg-canvas">
          <img
            v-if="thumbOf(p)"
            :src="thumbOf(p)"
            :alt="p.name"
            class="h-full w-full object-cover transition duration-300 group-hover:scale-105"
          />
          <div v-else class="flex h-full items-center justify-center text-3xl text-ink-400">🖼️</div>
        </div>

        <div class="p-4">
          <p class="text-xs text-ink-500">{{ p.categoryName }}</p>
          <h3 class="mt-0.5 line-clamp-1 text-sm font-medium text-ink-900">{{ p.name }}</h3>

          <div class="mt-2 flex items-end justify-between gap-2">
            <span class="text-lg font-semibold tabular-nums text-ink-900">{{ priceText(p.price) }}</span>
            <StarRating :model-value="p.averageRating" :count="p.reviewCount" size="sm" />
          </div>

          <!-- 정상 판매중이면 배지를 달지 않는다(노이즈 감소) -->
          <span
            v-if="p.status !== 'SELLING'"
            class="mt-2 inline-block rounded-full px-2 py-0.5 text-xs font-medium"
            :class="p.status === 'SOLD_OUT' ? 'bg-amber-50 text-amber-700' : 'bg-slate-100 text-ink-500'"
          >{{ statusText(p.status) }}</span>
        </div>
      </button>
    </div>

    <!-- 페이지 이동 -->
    <div v-if="!loading && totalPages > 1" class="mt-8 flex items-center justify-center gap-4">
      <button
        type="button"
        class="rounded-control border border-line px-3 py-1.5 text-sm text-ink-700 transition-colors hover:bg-surface disabled:opacity-40 disabled:hover:bg-transparent"
        :disabled="page === 0"
        @click="load(page - 1)"
      >이전</button>
      <span class="text-sm tabular-nums text-ink-500">{{ page + 1 }} / {{ totalPages }}</span>
      <button
        type="button"
        class="rounded-control border border-line px-3 py-1.5 text-sm text-ink-700 transition-colors hover:bg-surface disabled:opacity-40 disabled:hover:bg-transparent"
        :disabled="page + 1 >= totalPages"
        @click="load(page + 1)"
      >다음</button>
    </div>
  </section>
</template>
