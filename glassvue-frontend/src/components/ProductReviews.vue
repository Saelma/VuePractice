<script setup>
import { ref, computed, onMounted } from 'vue';
import { DxButton } from 'devextreme-vue/button';
import { DxTextArea } from 'devextreme-vue/text-area';
import { fetchProductReviews, createReview, updateReview, deleteReview } from '../api/review';
import { authState, isLoggedIn } from '../stores/auth';

const props = defineProps({ productId: { type: String, required: true } });

const summary = ref({ averageRating: 0, reviewCount: 0 });
const page = ref({ content: [], page: 0, totalPages: 0, last: true });
const loading = ref(true);
const error = ref('');

const isAdmin = computed(() => authState.user?.role === 'ADMIN');
const myId = computed(() => authState.user?.id);
function canManage(r) {
  return isAdmin.value || r.authorId === myId.value;
}

// 작성 폼
const form = ref({ rating: 5, content: '' });
const submitting = ref(false);
const formError = ref('');

// 인라인 수정
const editingId = ref(null);
const editForm = ref({ rating: 5, content: '' });

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
    await createReview(props.productId, { rating: form.value.rating, content: form.value.content.trim() });
    form.value = { rating: 5, content: '' };
    await load(0);
  } catch (e) {
    formError.value = e.message; // 미구매(403)·중복(409) 등 서버 메시지 그대로
  } finally {
    submitting.value = false;
  }
}

function startEdit(r) {
  editingId.value = r.id;
  editForm.value = { rating: r.rating, content: r.content };
}
function cancelEdit() {
  editingId.value = null;
}
async function saveEdit(r) {
  try {
    await updateReview(r.id, { rating: editForm.value.rating, content: editForm.value.content.trim() });
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
    <header class="mb-3 flex items-center gap-3 border-b pb-2">
      <h3 class="text-lg font-bold text-slate-800">상품 리뷰</h3>
      <span class="text-amber-500">
        <span class="font-semibold">★ {{ summary.averageRating.toFixed(1) }}</span>
        <span class="ml-1 text-sm text-slate-500">({{ summary.reviewCount }})</span>
      </span>
    </header>

    <div v-if="error" class="mb-3 rounded bg-red-50 p-2 text-sm text-red-600">{{ error }}</div>

    <!-- 작성 폼 (로그인 시) -->
    <div v-if="isLoggedIn" class="mb-5 rounded-lg border bg-slate-50 p-4">
      <div class="mb-2 flex items-center gap-2">
        <span class="text-sm text-slate-600">별점</span>
        <div class="flex">
          <button
            v-for="n in 5"
            :key="n"
            type="button"
            class="text-2xl leading-none"
            :class="n <= form.rating ? 'text-amber-400' : 'text-slate-300'"
            @click="form.rating = n"
          >★</button>
        </div>
      </div>
      <DxTextArea v-model:value="form.content" :height="70" placeholder="이 상품은 어떠셨나요?" />
      <div class="mt-2 flex items-center gap-2">
        <DxButton text="리뷰 등록" type="success" styling-mode="contained" :disabled="submitting" @click="submit" />
        <span v-if="formError" class="text-sm text-red-600">{{ formError }}</span>
      </div>
      <p class="mt-1 text-xs text-slate-400">※ 구매하신 상품만 리뷰를 작성할 수 있어요.</p>
    </div>
    <p v-else class="mb-5 text-sm text-slate-500">리뷰 작성은 로그인 후 가능합니다.</p>

    <!-- 목록 -->
    <div v-if="loading" class="text-slate-500">불러오는 중…</div>
    <div v-else-if="!page.content.length" class="text-slate-400">아직 리뷰가 없어요.</div>
    <ul v-else class="space-y-3">
      <li v-for="r in page.content" :key="r.id" class="rounded-lg border bg-white p-4">
        <div class="mb-1 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="text-amber-400">{{ '★'.repeat(r.rating) }}<span class="text-slate-300">{{ '★'.repeat(5 - r.rating) }}</span></span>
            <span class="text-sm font-medium text-slate-700">{{ r.author }}</span>
          </div>
          <span class="text-xs text-slate-400">{{ fmtDate(r.createdAt) }}</span>
        </div>

        <template v-if="editingId === r.id">
          <div class="mb-2 flex">
            <button
              v-for="n in 5" :key="n" type="button" class="text-xl leading-none"
              :class="n <= editForm.rating ? 'text-amber-400' : 'text-slate-300'"
              @click="editForm.rating = n"
            >★</button>
          </div>
          <DxTextArea v-model:value="editForm.content" :height="60" />
          <div class="mt-2 flex gap-2">
            <DxButton text="저장" type="default" styling-mode="contained" @click="saveEdit(r)" />
            <DxButton text="취소" styling-mode="outlined" @click="cancelEdit" />
          </div>
        </template>
        <template v-else>
          <p class="whitespace-pre-wrap text-slate-700">{{ r.content }}</p>
          <div v-if="canManage(r)" class="mt-2 flex gap-2">
            <button class="text-xs text-slate-500 hover:underline" @click="startEdit(r)">수정</button>
            <button class="text-xs text-red-500 hover:underline" @click="remove(r)">삭제</button>
          </div>
        </template>
      </li>
    </ul>

    <!-- 페이지 이동 -->
    <div v-if="page.totalPages > 1" class="mt-3 flex items-center justify-center gap-3 text-sm">
      <button class="text-slate-500 disabled:text-slate-300" :disabled="page.page === 0" @click="load(page.page - 1)">이전</button>
      <span class="text-slate-500">{{ page.page + 1 }} / {{ page.totalPages }}</span>
      <button class="text-slate-500 disabled:text-slate-300" :disabled="page.last" @click="load(page.page + 1)">다음</button>
    </div>
  </section>
</template>
