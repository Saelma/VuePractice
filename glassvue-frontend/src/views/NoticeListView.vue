<script setup>
/**
 * 공지 목록 — 첫 화면. 표(DataGrid) 대신 **읽기 좋은 리스트**로 보여준다(DESIGN.md §7).
 * 제목이 먼저 읽히고 작성자·날짜·조회수는 보조로 물러난다.
 */
import { reactive, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxDateBox } from 'devextreme-vue/date-box';
import { fetchNotices } from '../api/notice';
import { isLoggedIn } from '../stores/auth';
import EmptyState from '../components/EmptyState.vue';
import SkeletonList from '../components/SkeletonList.vue';

const router = useRouter();

const SIZE = 10;
const items = ref([]);
const page = ref(0);
const totalPages = ref(0);
const totalElements = ref(0);
const loading = ref(true);
const error = ref('');

const form = reactive({ title: '', author: '', fromDate: null, toDate: null });
let applied = {};
const hasFilter = ref(false);

function toLocalDate(d) {
  if (!d) return null;
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

async function load(p = 0) {
  loading.value = true;
  error.value = '';
  try {
    const res = await fetchNotices({ ...applied, page: p, size: SIZE });
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
onMounted(() => load(0));

function search() {
  applied = {
    title: form.title?.trim() || undefined,
    author: form.author?.trim() || undefined,
    fromDate: toLocalDate(form.fromDate),
    toDate: toLocalDate(form.toDate),
  };
  hasFilter.value = Object.values(applied).some((v) => v);
  load(0);
}
function reset() {
  form.title = '';
  form.author = '';
  form.fromDate = null;
  form.toDate = null;
  applied = {};
  hasFilter.value = false;
  load(0);
}

const fmtDate = (iso) => (iso ? new Date(iso).toLocaleDateString('ko-KR') : '');
</script>

<template>
  <section class="page">
    <div class="mb-5 flex items-end justify-between gap-4">
      <div>
        <h1 class="page-title">공지</h1>
        <p v-if="!loading" class="muted mt-1">{{ totalElements }}건</p>
      </div>
      <button v-if="isLoggedIn" type="button" class="btn btn-primary" @click="router.push('/notices/new')">
        새 공지
      </button>
    </div>

    <!-- 검색 -->
    <div class="card mb-6 flex flex-wrap items-end gap-3 p-4">
      <label class="field">
        <span class="field-label">제목</span>
        <DxTextBox v-model:value="form.title" placeholder="제목 검색" :width="180" @enter-key="search" />
      </label>
      <label class="field">
        <span class="field-label">작성자</span>
        <DxTextBox v-model:value="form.author" placeholder="작성자 검색" :width="140" @enter-key="search" />
      </label>
      <label class="field">
        <span class="field-label">작성일(시작)</span>
        <DxDateBox v-model:value="form.fromDate" type="date" display-format="yyyy-MM-dd" :width="150" />
      </label>
      <label class="field">
        <span class="field-label">작성일(종료)</span>
        <DxDateBox v-model:value="form.toDate" type="date" display-format="yyyy-MM-dd" :width="150" />
      </label>
      <div class="flex gap-2">
        <button type="button" class="btn btn-primary" @click="search">검색</button>
        <button type="button" class="btn btn-secondary" @click="reset">초기화</button>
      </div>
    </div>

    <div v-if="error" class="alert-error">{{ error }}</div>

    <!-- 로딩 스켈레톤 -->
    <SkeletonList v-else-if="loading" :rows="6" trailing />

    <!-- 빈 상태: 필터 때문에 빈 것과 정말 없는 것을 구분한다 -->
    <EmptyState
      v-else-if="!items.length"
      :icon="hasFilter ? '🔍' : '📭'"
      :message="hasFilter ? '조건에 맞는 공지가 없어요.' : '아직 등록된 공지가 없어요.'"
    >
      <button v-if="hasFilter" type="button" class="btn btn-secondary" @click="reset">필터 초기화</button>
      <button v-else-if="isLoggedIn" type="button" class="btn btn-primary" @click="router.push('/notices/new')">
        새 공지 작성
      </button>
    </EmptyState>

    <!-- 목록 -->
    <ul v-else class="card divide-y divide-line">
      <li v-for="n in items" :key="n.id">
        <button
          type="button"
          class="flex w-full items-center gap-3 px-5 py-4 text-left transition-colors hover:bg-canvas focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand-600"
          @click="router.push(`/notices/${n.id}`)"
        >
          <span v-if="n.pinned" class="shrink-0 text-sm" title="상단 고정">📌</span>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-ink-900">{{ n.title }}</p>
            <p class="muted mt-1">{{ n.author }} · {{ fmtDate(n.createdAt) }}</p>
          </div>
          <span class="muted shrink-0 tabular-nums">조회 {{ n.viewCount }}</span>
        </button>
      </li>
    </ul>

    <!-- 페이지 이동 -->
    <div v-if="!loading && totalPages > 1" class="mt-8 flex items-center justify-center gap-4">
      <button type="button" class="btn btn-secondary" :disabled="page === 0" @click="load(page - 1)">이전</button>
      <span class="text-sm tabular-nums text-ink-500">{{ page + 1 }} / {{ totalPages }}</span>
      <button type="button" class="btn btn-secondary" :disabled="page + 1 >= totalPages" @click="load(page + 1)">
        다음
      </button>
    </div>
  </section>
</template>
