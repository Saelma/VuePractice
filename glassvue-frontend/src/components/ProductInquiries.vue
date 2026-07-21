<script setup>
import { ref, computed, onMounted } from 'vue';
import { DxButton } from 'devextreme-vue/button';
import { DxTextArea } from 'devextreme-vue/text-area';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxCheckBox } from 'devextreme-vue/check-box';
import {
  fetchProductInquiries, createInquiry, updateInquiry, deleteInquiry, answerInquiry, inquiryStatusText,
  INQUIRY_IMAGE_MAX,
} from '../api/inquiry';
import { authState, isLoggedIn } from '../stores/auth';
import ImageUploader from './ImageUploader.vue';

const props = defineProps({ productId: { type: String, required: true } });

const page = ref({ content: [], page: 0, totalPages: 0, last: true });
const loading = ref(true);
const error = ref('');

const isAdmin = computed(() => authState.user?.role === 'ADMIN');
const myId = computed(() => authState.user?.id);
function canEdit(q) {
  return q.authorId === myId.value && q.status === 'WAITING';
}
function canDelete(q) {
  return isAdmin.value || q.authorId === myId.value;
}

// 작성 폼 — images는 [{id,url}] (전송 시 id만 뽑는다)
const form = ref({ title: '', content: '', secret: false, images: [] });
const submitting = ref(false);
const formError = ref('');

// 인라인 수정 / 관리자 답변
const editingId = ref(null);
const editForm = ref({ title: '', content: '', secret: false, images: [] });
const answeringId = ref(null);
const answerText = ref('');

async function load(p = 0) {
  loading.value = true;
  error.value = '';
  try {
    page.value = await fetchProductInquiries(props.productId, { page: p, size: 5 });
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function submit() {
  formError.value = '';
  if (!form.value.title.trim() || !form.value.content.trim()) {
    formError.value = '제목과 내용을 입력하세요.';
    return;
  }
  submitting.value = true;
  try {
    await createInquiry(props.productId, {
      title: form.value.title.trim(), content: form.value.content.trim(), secret: form.value.secret,
      imageIds: form.value.images.map((i) => i.id),
    });
    form.value = { title: '', content: '', secret: false, images: [] };
    await load(0);
  } catch (e) {
    formError.value = e.message;
  } finally {
    submitting.value = false;
  }
}

function startEdit(q) {
  editingId.value = q.id;
  // 수정 폼은 기존 이미지를 들고 시작한다 — 서버가 "보낸 목록으로 통째 교체"라 빠뜨리면 사진이 삭제된다.
  editForm.value = { title: q.title, content: q.content, secret: q.secret, images: [...(q.images || [])] };
}
async function saveEdit(q) {
  try {
    await updateInquiry(q.id, {
      title: editForm.value.title.trim(), content: editForm.value.content.trim(), secret: editForm.value.secret,
      imageIds: editForm.value.images.map((i) => i.id),
    });
    editingId.value = null;
    await load(page.value.page);
  } catch (e) {
    error.value = e.message;
  }
}

async function remove(q) {
  if (!window.confirm('이 문의를 삭제할까요?')) return;
  try {
    await deleteInquiry(q.id);
    await load(0);
  } catch (e) {
    error.value = e.message;
  }
}

function startAnswer(q) {
  answeringId.value = q.id;
  answerText.value = q.answer || '';
}
async function saveAnswer(q) {
  if (!answerText.value.trim()) return;
  try {
    await answerInquiry(q.id, answerText.value.trim());
    answeringId.value = null;
    await load(page.value.page);
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
  <section class="mt-10">
    <header class="mb-3 border-b pb-2">
      <h3 class="text-lg font-bold text-slate-800">상품 문의</h3>
    </header>

    <div v-if="error" class="mb-3 rounded bg-red-50 p-2 text-sm text-red-600">{{ error }}</div>

    <!-- 작성 폼 (로그인 시) -->
    <div v-if="isLoggedIn" class="mb-5 flex flex-col gap-2 rounded-lg border bg-slate-50 p-4">
      <DxTextBox v-model:value="form.title" placeholder="문의 제목" />
      <DxTextArea v-model:value="form.content" :height="70" placeholder="궁금한 점을 남겨주세요." />
      <ImageUploader v-model="form.images" :max="INQUIRY_IMAGE_MAX" thumb-class="h-16 w-16" @error="formError = $event" />
      <div class="flex items-center gap-3">
        <DxCheckBox v-model:value="form.secret" text="비밀글 (작성자·판매자만 열람)" />
        <DxButton text="문의 등록" type="default" styling-mode="contained" :disabled="submitting" @click="submit" />
        <span v-if="formError" class="text-sm text-red-600">{{ formError }}</span>
      </div>
    </div>
    <p v-else class="mb-5 text-sm text-slate-500">문의 작성은 로그인 후 가능합니다.</p>

    <!-- 목록 -->
    <div v-if="loading" class="text-slate-500">불러오는 중…</div>
    <div v-else-if="!page.content.length" class="text-slate-400">등록된 문의가 없어요.</div>
    <ul v-else class="space-y-3">
      <li v-for="q in page.content" :key="q.id" class="rounded-lg border bg-white p-4">
        <div class="mb-1 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span v-if="q.secret" title="비밀글">🔒</span>
            <span class="font-medium text-slate-800">{{ q.title }}</span>
            <span
              class="rounded px-2 py-0.5 text-xs"
              :class="q.status === 'ANSWERED' ? 'bg-green-100 text-green-700' : 'bg-slate-100 text-slate-500'"
            >{{ inquiryStatusText(q.status) }}</span>
          </div>
          <span class="text-xs text-slate-400">{{ q.author }} · {{ fmtDate(q.createdAt) }}</span>
        </div>

        <!-- 수정 모드 -->
        <template v-if="editingId === q.id">
          <div class="flex flex-col gap-2">
            <DxTextBox v-model:value="editForm.title" />
            <DxTextArea v-model:value="editForm.content" :height="60" />
            <ImageUploader v-model="editForm.images" :max="INQUIRY_IMAGE_MAX" thumb-class="h-16 w-16" @error="error = $event" />
            <div class="flex items-center gap-3">
              <DxCheckBox v-model:value="editForm.secret" text="비밀글" />
              <DxButton text="저장" type="default" styling-mode="contained" @click="saveEdit(q)" />
              <DxButton text="취소" styling-mode="outlined" @click="editingId = null" />
            </div>
          </div>
        </template>

        <!-- 보기 모드 -->
        <template v-else>
          <p class="whitespace-pre-wrap text-slate-700" :class="{ 'italic text-slate-400': q.masked }">{{ q.content }}</p>

          <!-- 첨부 이미지 (비밀글 마스킹 시 서버가 images를 비워 보내 자연히 숨겨진다) -->
          <div v-if="q.images?.length" class="mt-2 flex flex-wrap gap-2">
            <a v-for="img in q.images" :key="img.id" :href="img.url" target="_blank" rel="noopener">
              <img :src="img.thumbUrl" alt="문의 이미지" class="h-16 w-16 rounded border object-cover" />
            </a>
          </div>

          <!-- 판매자 답변 -->
          <div v-if="q.answer" class="mt-3 rounded border-l-4 border-green-400 bg-green-50 p-3">
            <div class="mb-1 text-xs font-semibold text-green-700">판매자 답변 · {{ fmtDate(q.answeredAt) }}</div>
            <p class="whitespace-pre-wrap text-sm text-slate-700">{{ q.answer }}</p>
          </div>

          <!-- 액션 -->
          <div class="mt-2 flex gap-3">
            <button v-if="canEdit(q)" class="text-xs text-slate-500 hover:underline" @click="startEdit(q)">수정</button>
            <button v-if="canDelete(q)" class="text-xs text-red-500 hover:underline" @click="remove(q)">삭제</button>
            <button v-if="isAdmin && q.authorId !== myId" class="text-xs text-green-600 hover:underline" @click="startAnswer(q)">
              {{ q.answer ? '답변 수정' : '답변하기' }}
            </button>
          </div>

          <!-- 관리자 답변 폼 -->
          <div v-if="answeringId === q.id" class="mt-2 rounded bg-slate-50 p-3">
            <DxTextArea v-model:value="answerText" :height="60" placeholder="답변 내용" />
            <div class="mt-2 flex gap-2">
              <DxButton text="답변 등록" type="success" styling-mode="contained" @click="saveAnswer(q)" />
              <DxButton text="취소" styling-mode="outlined" @click="answeringId = null" />
            </div>
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
