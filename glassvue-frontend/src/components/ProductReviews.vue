<script setup>
import { ref, computed, onMounted } from 'vue';
import { DxTextArea } from 'devextreme-vue/text-area';
import {
  fetchProductReviews, createReview, updateReview, deleteReview,
  REVIEW_IMAGE_MAX, REVIEW_SORT_OPTIONS,
} from '../api/review';
import { RouterLink } from 'vue-router';
import { authState, isLoggedIn, isAdmin } from '../stores/auth';
import { useLoginRedirect } from '../composables/useLoginRedirect';
import StarRating from './StarRating.vue';
import ImageUploader from './ImageUploader.vue';
import EmptyState from './EmptyState.vue';
import SkeletonList from './SkeletonList.vue';

const { loginTo } = useLoginRedirect();
const props = defineProps({ productId: { type: String, required: true } });

const summary = ref({ averageRating: 0, reviewCount: 0 });
const page = ref({ content: [], page: 0, totalPages: 0, last: true });
const loading = ref(true);
const error = ref('');

const myId = computed(() => authState.user?.id);
function canManage(r) {
  return isAdmin.value || r.authorId === myId.value;
}

// 작성 폼 — images는 [{id,url}] (전송 시 id만 뽑는다)
const form = ref({ rating: 5, content: '', images: [] });
const submitting = ref(false);
const formError = ref('');

// 인라인 수정
const editingId = ref(null);
const editForm = ref({ rating: 5, content: '', images: [] });

// 정렬·필터 (B-22). 바꾸면 **0페이지부터** 다시 읽는다 — 3페이지에서 정렬만 바꾸면
// 그 페이지에 뭐가 있을지 알 수 없어 사용자가 길을 잃는다.
const sort = ref(REVIEW_SORT_OPTIONS[0].value);
const photoOnly = ref(false);

function changeView() {
  load(0);
}

async function load(p = 0) {
  loading.value = true;
  error.value = '';
  try {
    const data = await fetchProductReviews(props.productId, {
      page: p, size: 5, sort: sort.value, photoOnly: photoOnly.value,
    });
    summary.value = { averageRating: data.averageRating, reviewCount: data.reviewCount };
    page.value = data.page;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function submit() {
  formError.value = '';
  if (!form.value.content.trim()) {
    formError.value = '내용을 입력하세요.';
    return;
  }
  submitting.value = true;
  try {
    await createReview(props.productId, {
      rating: form.value.rating,
      content: form.value.content.trim(),
      imageIds: form.value.images.map((i) => i.id),
    });
    form.value = { rating: 5, content: '', images: [] };
    await load(0);
  } catch (e) {
    formError.value = e.message; // 미구매(403)·중복(409) 등 서버 메시지 그대로
  } finally {
    submitting.value = false;
  }
}

function startEdit(r) {
  editingId.value = r.id;
  // 기존 이미지를 그대로 들고 시작한다 — 수정은 "보낸 목록으로 통째 교체"라 빠뜨리면 삭제된다.
  editForm.value = { rating: r.rating, content: r.content, images: [...(r.images || [])] };
}
function cancelEdit() {
  editingId.value = null;
}
async function saveEdit(r) {
  try {
    await updateReview(r.id, {
      rating: editForm.value.rating,
      content: editForm.value.content.trim(),
      imageIds: editForm.value.images.map((i) => i.id),
    });
    editingId.value = null;
    await load(page.value.page);
  } catch (e) {
    error.value = e.message;
  }
}

async function remove(r) {
  if (!window.confirm('이 리뷰를 삭제할까요?')) return;
  try {
    await deleteReview(r.id);
    await load(0);
  } catch (e) {
    error.value = e.message;
  }
}

function fmtDate(iso) {
  return iso ? new Date(iso).toLocaleDateString('ko-KR') : '';
}

onMounted(() => load(0));
</script>

<template>
  <section>
    <header class="mb-4 flex items-center gap-3">
      <h2 class="section-title">상품 리뷰</h2>
      <StarRating :model-value="summary.averageRating" :count="summary.reviewCount" />
    </header>

    <div v-if="error" class="alert-error mb-3">{{ error }}</div>

    <!-- 작성 폼 (로그인 시) -->
    <div v-if="isLoggedIn" class="card mb-6 flex flex-col gap-4 p-5">
      <div class="field">
        <span class="field-label">별점</span>
        <StarRating v-model="form.rating" editable size="lg" />
      </div>
      <label class="field">
        <span class="field-label">내용</span>
        <DxTextArea v-model:value="form.content" :height="70" placeholder="이 상품은 어떠셨나요?" />
      </label>
      <div class="field">
        <span class="field-label">사진 첨부 (최대 {{ REVIEW_IMAGE_MAX }}장)</span>
        <ImageUploader v-model="form.images" :max="REVIEW_IMAGE_MAX" thumb-class="h-16 w-16" @error="formError = $event" />
      </div>
      <p v-if="formError" class="field-error">{{ formError }}</p>
      <div class="flex items-center justify-between gap-3">
        <p class="muted">※ 구매하신 상품만 리뷰를 작성할 수 있어요.</p>
        <button type="button" class="btn btn-primary" :disabled="submitting" @click="submit">리뷰 등록</button>
      </div>
    </div>
    <!--
      🔴 **문구를 «갈 수 있는 말» 로 바꿨다** (2026-09-01, BACKLOG J-4). 전에는 누를 수 없는 `<p>` 라
      비회원이 «그래서 어디로 가나» 를 스스로 찾아야 했고, 헤더로 가면 **보던 상품을 잃었다**(J-2).
    -->
    <p v-else class="mb-6 text-sm text-ink-500">
      리뷰 작성은
      <RouterLink :to="loginTo" class="font-medium text-ink-900 underline underline-offset-2">로그인</RouterLink>
      후 가능합니다.
    </p>

    <!--
      정렬·사진만 보기 (B-22). ⚠ **리뷰가 없으면 통째로 감춘다** — 고를 게 없는데 컨트롤만
      떠 있으면 "필터 때문에 비었나?" 로 읽힌다(빈 상태 문구는 상황에 맞게, DESIGN §5).
      ⚠ 「사진 있는 리뷰만」으로 걸러 0건이 될 때는 **감추지 않는다** — 그때는 필터를 풀 수 있어야 한다.
    -->
    <div v-if="summary.reviewCount > 0" class="mb-3 flex flex-wrap items-center justify-between gap-2">
      <label class="flex cursor-pointer items-center gap-2 text-sm text-ink-700">
        <input v-model="photoOnly" type="checkbox" class="review-filter-box" @change="changeView" />
        사진 있는 리뷰만
      </label>
      <select v-model="sort" class="review-sort" @change="changeView">
        <option v-for="o in REVIEW_SORT_OPTIONS" :key="o.value" :value="o.value">{{ o.text }}</option>
      </select>
    </div>

    <!-- 로딩: 텍스트 대신 스켈레톤 (DESIGN.md §5) -->
    <SkeletonList v-if="loading" />

    <!-- 빈 상태 -->
    <EmptyState
      v-else-if="!page.content.length"
      density="section"
      icon="💬"
      :message="photoOnly ? '사진이 있는 리뷰가 없어요.' : '아직 리뷰가 없어요.'"
      :hint="photoOnly
        ? '「사진 있는 리뷰만」을 끄면 전체 리뷰를 볼 수 있어요.'
        : (isLoggedIn ? '이 상품을 구매하셨다면 첫 리뷰를 남겨보세요.' : null)"
    />

    <!-- 목록 -->
    <ul v-else class="card divide-y divide-line">
      <li v-for="r in page.content" :key="r.id" class="px-5 py-4">
        <div class="mb-2 flex items-center justify-between gap-3">
          <div class="flex items-center gap-2">
            <StarRating :model-value="r.rating" size="sm" />
            <span class="text-sm font-medium text-ink-900">{{ r.author }}</span>
          </div>
          <span class="muted shrink-0">{{ fmtDate(r.createdAt) }}</span>
        </div>

        <template v-if="editingId === r.id">
          <div class="flex flex-col gap-3">
            <StarRating v-model="editForm.rating" editable size="lg" />
            <DxTextArea v-model:value="editForm.content" :height="60" />
            <ImageUploader v-model="editForm.images" :max="REVIEW_IMAGE_MAX" thumb-class="h-16 w-16" @error="error = $event" />
            <div class="flex gap-2">
              <button type="button" class="btn btn-secondary" @click="saveEdit(r)">저장</button>
              <button type="button" class="btn btn-ghost" @click="cancelEdit">취소</button>
            </div>
          </div>
        </template>
        <template v-else>
          <p class="whitespace-pre-wrap text-sm text-ink-700">{{ r.content }}</p>
          <div v-if="r.images?.length" class="mt-3 flex flex-wrap gap-2">
            <a v-for="img in r.images" :key="img.id" :href="img.url" target="_blank" rel="noopener">
              <img
                :src="img.thumbUrl"
                :alt="`${r.author}님의 리뷰 사진`"
                class="h-20 w-20 rounded-control border border-line object-cover"
              />
            </a>
          </div>
          <div v-if="canManage(r)" class="mt-3 flex gap-1">
            <button type="button" class="btn btn-ghost" @click="startEdit(r)">수정</button>
            <button type="button" class="btn btn-danger" @click="remove(r)">삭제</button>
          </div>
        </template>
      </li>
    </ul>

    <!-- 페이지 이동 -->
    <div v-if="page.totalPages > 1" class="mt-6 flex items-center justify-center gap-4">
      <button type="button" class="btn btn-secondary" :disabled="page.page === 0" @click="load(page.page - 1)">이전</button>
      <span class="text-sm tabular-nums text-ink-500">{{ page.page + 1 }} / {{ page.totalPages }}</span>
      <button type="button" class="btn btn-secondary" :disabled="page.last" @click="load(page.page + 1)">다음</button>
    </div>
  </section>
</template>

<style scoped>
/*
 * 정렬 select·필터 체크박스는 **네이티브**를 쓴다 — DevExtreme 로 바꾸면 라벨 클릭·포커스 링을
 * 다시 만들어야 하는데, 브라우저 기본이 이미 갖추고 있다(가입 동의 체크박스와 같은 판단, DESIGN §6).
 * 색·radius 만 토큰에 맞춘다.
 */
.review-filter-box {
  width: 1rem;
  height: 1rem;
  accent-color: var(--color-brand-600);
  cursor: pointer;
}
.review-sort {
  border: 1px solid var(--color-line);
  border-radius: var(--radius-control);
  background: var(--color-surface);
  padding: 0.375rem 0.5rem;
  font-size: 0.875rem;
  color: var(--color-ink-700);
  cursor: pointer;
}
.review-sort:focus-visible {
  outline: 2px solid var(--color-brand-600);
  outline-offset: 2px;
}
</style>
