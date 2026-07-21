<script setup>
/**
 * 공지 상세 — 읽는 화면이라 폭을 좁게(page-narrow) 잡고 제목 → 메타 → 본문 순으로 읽히게 한다(DESIGN.md §7).
 */
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getNotice, deleteNotice, increaseView } from '../api/notice';
import { isLoggedIn, authState } from '../stores/auth';

const props = defineProps({ id: { type: String, required: true } });
const router = useRouter();

const notice = ref(null);
const error = ref('');
const loading = ref(true);

// 본인 글이거나 ADMIN일 때 수정/삭제 노출 (백엔드도 동일 규칙으로 강제)
const isOwner = computed(
  () =>
    isLoggedIn.value &&
    (notice.value?.authorId === authState.user?.id || authState.user?.role === 'ADMIN'),
);

onMounted(async () => {
  try {
    await increaseView(props.id).catch(() => {}); // 조회수 증가(실패해도 조회 계속)
    notice.value = await getNotice(props.id);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
});

async function onDelete() {
  if (!window.confirm('이 공지를 삭제할까요?')) return;
  try {
    await deleteNotice(props.id);
    router.push('/');
  } catch (e) {
    error.value = e.message;
  }
}

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}
</script>

<template>
  <section class="page-narrow">
    <div v-if="error" class="alert-error">{{ error }}</div>

    <!-- 로딩: 텍스트 대신 스켈레톤으로 읽는 레이아웃을 미리 잡는다 (DESIGN.md §5) -->
    <div v-else-if="loading" class="card p-6">
      <div class="skeleton h-7 w-2/3"></div>
      <div class="mt-3 flex gap-3 border-b border-line pb-4">
        <div class="skeleton h-3 w-20"></div>
        <div class="skeleton h-3 w-16"></div>
        <div class="skeleton h-3 w-28"></div>
      </div>
      <div class="mt-4 space-y-2">
        <div class="skeleton h-4 w-full"></div>
        <div class="skeleton h-4 w-11/12"></div>
        <div class="skeleton h-4 w-3/4"></div>
      </div>
    </div>

    <article v-else-if="notice" class="card p-6">
      <div class="flex items-start gap-2">
        <span v-if="notice.pinned" class="shrink-0 text-lg" title="상단 고정">📌</span>
        <h1 class="page-title">{{ notice.title }}</h1>
      </div>

      <div class="mt-3 flex flex-wrap gap-x-4 gap-y-1 border-b border-line pb-4">
        <span class="muted">작성자 <b class="font-medium text-ink-700">{{ notice.author }}</b></span>
        <span class="muted tabular-nums">조회 {{ notice.viewCount }}</span>
        <span class="muted tabular-nums">작성 {{ fmt(notice.createdAt) }}</span>
        <span v-if="notice.updatedAt !== notice.createdAt" class="muted tabular-nums">
          수정 {{ fmt(notice.updatedAt) }}
        </span>
      </div>

      <p class="mt-5 min-h-[8rem] whitespace-pre-wrap text-sm leading-relaxed text-ink-700">{{ notice.content }}</p>

      <div class="mt-8 flex items-center gap-2 border-t border-line pt-5">
        <button type="button" class="btn btn-secondary" @click="router.push('/')">목록</button>
        <template v-if="isOwner">
          <button type="button" class="btn btn-primary" @click="router.push(`/notices/${id}/edit`)">수정</button>
          <button type="button" class="btn btn-danger ml-auto" @click="onDelete">삭제</button>
        </template>
      </div>
    </article>
  </section>
</template>
