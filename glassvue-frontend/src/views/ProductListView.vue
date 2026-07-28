<script setup>
/**
 * 상품 목록 — 커머스 표준 구성(DESIGN.md §7):
 *   좌: 필터 사이드바(카테고리·가격·상태) / 우: 정렬 툴바 + 카드 그리드
 * 적용된 조건은 상단에 **칩**으로 보여주고 하나씩 뗄 수 있게 한다(필터가 걸린 줄 모르고 헤매지 않게).
 * 정렬은 백엔드 화이트리스트(SORT_OPTIONS)와 맞춰야 400이 나지 않는다.
 */
import { reactive, ref, computed, onMounted, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxNumberBox } from 'devextreme-vue/number-box';
import { fetchProducts, SORT_OPTIONS, STATUS_OPTIONS, statusText, priceText } from '../api/product';
import { fetchCategories } from '../api/category';
import { authState, isLoggedIn } from '../stores/auth';
import { loadWishlistIds } from '../stores/wishlist';
import EmptyState from '../components/EmptyState.vue';
import ProductCard from '../components/ProductCard.vue';

const router = useRouter();
const route = useRoute();
const categories = ref([]);
const isAdmin = computed(() => authState.user?.role === 'ADMIN');

const SIZE = 12;
const items = ref([]);
const page = ref(0);
const totalPages = ref(0);
const totalElements = ref(0);
const loading = ref(true);
const error = ref('');
const sort = ref(SORT_OPTIONS[0].value);
const filterOpen = ref(false); // 모바일에서 필터 접기

/** 화면 입력값(폼)과 실제 적용된 조건(applied)을 분리한다 — 입력 중에 목록이 흔들리지 않게. */
const form = reactive({ name: '', categoryId: null, minPrice: null, maxPrice: null, status: null });
const applied = reactive({ name: null, categoryId: null, minPrice: null, maxPrice: null, status: null });

async function load(p = 0) {
  loading.value = true;
  error.value = '';
  try {
    const res = await fetchProducts({
      name: applied.name ?? undefined,
      categoryId: applied.categoryId ?? undefined,
      minPrice: applied.minPrice ?? undefined,
      maxPrice: applied.maxPrice ?? undefined,
      status: applied.status ?? undefined,
      sort: sort.value,
      page: p,
      size: SIZE,
    });
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

/**
 * URL 쿼리(?name=…&categoryId=…&sort=…)를 화면 조건으로 반영한다.
 * 홈의 카테고리 바로가기·"인기 더보기"·헤더 검색이 전부 이 경로로 들어온다.
 * 정렬은 화이트리스트에 있는 값만 받는다(임의 값이면 서버가 400을 낸다).
 */
function syncFromQuery() {
  const q = route.query;
  form.name = q.name || '';
  applied.name = q.name ? String(q.name).trim() || null : null;
  form.categoryId = q.categoryId || null;
  applied.categoryId = q.categoryId || null;
  sort.value = q.sort && SORT_OPTIONS.some((o) => o.value === q.sort) ? q.sort : SORT_OPTIONS[0].value;
}

onMounted(async () => {
  syncFromQuery();
  load(0);
  // 찜 하트를 채우려면 내가 찜한 상품 id가 필요하다. 실패해도 목록은 그대로 동작한다.
  if (isLoggedIn.value) loadWishlistIds();
  try {
    categories.value = await fetchCategories();
  } catch (e) {
    /* 카테고리 로드 실패해도 목록은 동작 */
  }
});

// 이미 /products 에 있는데 헤더 검색·홈 링크로 쿼리만 바뀌면 onMounted 는 다시 안 뛴다 → 쿼리를 지켜본다.
// 사이드바 필터(apply 등)는 URL 을 안 건드리므로 이 watch 를 건드리지 않는다(충돌 없음).
watch(() => route.query, () => {
  syncFromQuery();
  load(0);
});

function apply() {
  applied.name = form.name?.trim() || null;
  applied.categoryId = form.categoryId;
  applied.minPrice = form.minPrice ?? null;
  applied.maxPrice = form.maxPrice ?? null;
  applied.status = form.status;
  filterOpen.value = false;
  load(0);
}
/** 사이드바에서 카테고리를 고르면 바로 반영한다(커머스에선 즉시 적용이 자연스럽다). */
function pickCategory(id) {
  form.categoryId = id;
  applied.categoryId = id;
  load(0);
}
function pickStatus(v) {
  form.status = v;
  applied.status = v;
  load(0);
}
function resetAll() {
  form.name = '';
  form.categoryId = null;
  form.minPrice = null;
  form.maxPrice = null;
  form.status = null;
  Object.assign(applied, { name: null, categoryId: null, minPrice: null, maxPrice: null, status: null });
  load(0);
}
/** 칩 하나만 떼기 */
function clearOne(key) {
  applied[key] = null;
  form[key] = key === 'name' ? '' : null;
  load(0);
}

const categoryName = (id) => categories.value.find((c) => c.id === id)?.name ?? '카테고리';

/** 상단에 보여줄 활성 필터 칩 목록 */
const chips = computed(() => {
  const out = [];
  if (applied.name) out.push({ key: 'name', label: `"${applied.name}"` });
  if (applied.categoryId) out.push({ key: 'categoryId', label: categoryName(applied.categoryId) });
  if (applied.status) out.push({ key: 'status', label: statusText(applied.status) });
  if (applied.minPrice != null) out.push({ key: 'minPrice', label: `${priceText(applied.minPrice)} 이상` });
  if (applied.maxPrice != null) out.push({ key: 'maxPrice', label: `${priceText(applied.maxPrice)} 이하` });
  return out;
});
</script>

<template>
  <section class="page">
    <div class="mb-5 flex items-end justify-between gap-4">
      <h1 class="page-title">상품</h1>
      <div v-if="isAdmin" class="flex gap-2">
        <button type="button" class="btn btn-secondary" @click="router.push('/admin/categories')">카테고리 관리</button>
        <button type="button" class="btn btn-primary" @click="router.push('/products/new')">상품 등록</button>
      </div>
    </div>

    <div class="grid gap-6 lg:grid-cols-[220px_1fr]">
      <!-- 좌: 필터 사이드바 (모바일에선 접힘) -->
      <aside>
        <button
          type="button"
          class="btn btn-secondary w-full lg:hidden"
          :aria-expanded="filterOpen"
          @click="filterOpen = !filterOpen"
        >필터 {{ filterOpen ? '닫기' : '열기' }}</button>

        <div :class="filterOpen ? 'mt-3 block' : 'hidden lg:block'" class="space-y-6 lg:sticky lg:top-20">
          <!-- 카테고리: 즉시 적용 -->
          <div>
            <h2 class="field-label mb-2">카테고리</h2>
            <ul class="space-y-1 text-sm">
              <li>
                <button type="button" class="w-full rounded-control px-2 py-1 text-left transition-colors hover:bg-surface"
                  :class="applied.categoryId === null ? 'font-medium text-ink-900' : 'text-ink-500'"
                  @click="pickCategory(null)">전체</button>
              </li>
              <li v-for="c in categories" :key="c.id">
                <button type="button" class="w-full rounded-control px-2 py-1 text-left transition-colors hover:bg-surface"
                  :class="applied.categoryId === c.id ? 'font-medium text-ink-900' : 'text-ink-500'"
                  @click="pickCategory(c.id)">{{ c.name }}</button>
              </li>
            </ul>
          </div>

          <!-- 상태: 즉시 적용 -->
          <div>
            <h2 class="field-label mb-2">판매 상태</h2>
            <ul class="space-y-1 text-sm">
              <li>
                <button type="button" class="w-full rounded-control px-2 py-1 text-left transition-colors hover:bg-surface"
                  :class="applied.status === null ? 'font-medium text-ink-900' : 'text-ink-500'"
                  @click="pickStatus(null)">전체</button>
              </li>
              <li v-for="s in STATUS_OPTIONS" :key="s.value">
                <button type="button" class="w-full rounded-control px-2 py-1 text-left transition-colors hover:bg-surface"
                  :class="applied.status === s.value ? 'font-medium text-ink-900' : 'text-ink-500'"
                  @click="pickStatus(s.value)">{{ s.text }}</button>
              </li>
            </ul>
          </div>

          <!-- 검색어·가격: 입력 후 적용 -->
          <div class="space-y-3 border-t border-line pt-5">
            <label class="field">
              <span class="field-label">상품명</span>
              <DxTextBox v-model:value="form.name" placeholder="검색어" @enter-key="apply" />
            </label>
            <label class="field">
              <span class="field-label">최소 가격</span>
              <DxNumberBox v-model:value="form.minPrice" :min="0" :show-clear-button="true" format="#,##0" />
            </label>
            <label class="field">
              <span class="field-label">최대 가격</span>
              <DxNumberBox v-model:value="form.maxPrice" :min="0" :show-clear-button="true" format="#,##0" />
            </label>
            <div class="flex gap-2 pt-1">
              <button type="button" class="btn btn-primary flex-1" @click="apply">적용</button>
              <button type="button" class="btn btn-secondary" @click="resetAll">초기화</button>
            </div>
          </div>
        </div>
      </aside>

      <!-- 우: 툴바 + 그리드 -->
      <div>
        <!-- 툴바: 결과 수 + 정렬 -->
        <div class="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-line pb-3">
          <p class="text-sm text-ink-500">
            총 <b class="tabular-nums text-ink-900">{{ totalElements }}</b>개
          </p>
          <label class="flex items-center gap-2">
            <span class="field-label">정렬</span>
            <select
              v-model="sort"
              class="rounded-control border border-line bg-surface px-2 py-1.5 text-sm text-ink-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
              @change="load(0)"
            >
              <option v-for="o in SORT_OPTIONS" :key="o.value" :value="o.value">{{ o.text }}</option>
            </select>
          </label>
        </div>

        <!-- 활성 필터 칩 -->
        <div v-if="chips.length" class="mb-4 flex flex-wrap items-center gap-2">
          <button
            v-for="c in chips"
            :key="c.key"
            type="button"
            class="badge badge-neutral gap-1 hover:bg-brand-100"
            :aria-label="`${c.label} 필터 제거`"
            @click="clearOne(c.key)"
          >{{ c.label }} <span aria-hidden="true">×</span></button>
          <button type="button" class="btn btn-ghost btn-sm" @click="resetAll">전체 해제</button>
        </div>

        <div v-if="error" class="alert-error">{{ error }}</div>

        <!-- 로딩 스켈레톤 -->
        <div v-else-if="loading" class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
          <div v-for="n in 6" :key="n" class="overflow-hidden rounded-card bg-surface shadow-card">
            <div class="skeleton aspect-square rounded-none"></div>
            <div class="space-y-2 p-4">
              <div class="skeleton h-3 w-16"></div>
              <div class="skeleton h-4 w-3/4"></div>
              <div class="skeleton h-5 w-24"></div>
            </div>
          </div>
        </div>

        <!-- 빈 상태: 필터 때문에 빈 것과 정말 없는 것을 구분한다 -->
        <EmptyState
          v-else-if="!items.length"
          :icon="chips.length ? '🔍' : '🗂️'"
          :message="chips.length ? '조건에 맞는 상품이 없어요.' : '아직 등록된 상품이 없어요.'"
        >
          <button v-if="chips.length" type="button" class="btn btn-secondary" @click="resetAll">필터 초기화</button>
          <button v-else-if="isAdmin" type="button" class="btn btn-primary" @click="router.push('/products/new')">
            상품 등록
          </button>
        </EmptyState>

        <!-- 카드 그리드 (카드는 홈과 공유하는 ProductCard 컴포넌트) -->
        <div v-else class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
          <ProductCard v-for="p in items" :key="p.id" :product="p" />
        </div>

        <!-- 페이지 이동 -->
        <div v-if="!loading && totalPages > 1" class="mt-8 flex items-center justify-center gap-4">
          <button type="button" class="btn btn-secondary" :disabled="page === 0" @click="load(page - 1)">이전</button>
          <span class="text-sm tabular-nums text-ink-500">{{ page + 1 }} / {{ totalPages }}</span>
          <button type="button" class="btn btn-secondary" :disabled="page + 1 >= totalPages" @click="load(page + 1)">
            다음
          </button>
        </div>
      </div>
    </div>
  </section>
</template>
