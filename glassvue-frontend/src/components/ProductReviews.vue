<script setup>
import { ref, computed, onMounted } from 'vue';
import { DxTextArea } from 'devextreme-vue/text-area';
import { fetchProductReviews, createReview, updateReview, deleteReview, REVIEW_IMAGE_MAX } from '../api/review';
import { authState, isLoggedIn, isAdmin } from '../stores/auth';
import StarRating from './StarRating.vue';
import ImageUploader from './ImageUploader.vue';
import EmptyState from './EmptyState.vue';
import SkeletonList from './SkeletonList.vue';

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

async function load(p = 0) {
  loading.value = true;
  error.value = '';
  try {
    const data = await fetchProductReviews(props.productId, { page: p, size: 5 });
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
  <section class="mt-8">
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
    <p v-else class="mb-6 text-sm text-ink-500">리뷰 작성은 로그인 후 가능합니다.</p>

    <!-- 로딩: 텍스트 대신 스켈레톤 (DESIGN.md §5) -->
    <SkeletonList v-if="loading" />

    <!-- 빈 상태 -->
    <EmptyState
      v-else-if="!page.content.length"
      density="section"
      icon="💬"
      message="아직 리뷰가 없어요."
      :hint="isLoggedIn ? '이 상품을 구매하셨다면 첫 리뷰를 남겨보세요.' : null"
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
