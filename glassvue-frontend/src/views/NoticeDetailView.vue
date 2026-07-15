<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxButton } from 'devextreme-vue/button';
import { getNotice, deleteNotice, increaseView } from '../api/notice';

const props = defineProps({ id: { type: String, required: true } });
const router = useRouter();

const notice = ref(null);
const error = ref('');
const loading = ref(true);

onMounted(async () => {
  try {
    // 조회수 증가(실패해도 조회는 계속) → 최신 값 포함해 조회
    await increaseView(props.id).catch(() => {});
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
  <section class="max-w-3xl p-6">
    <div v-if="error" class="mb-4 rounded bg-red-50 p-3 text-red-600">{{ error }}</div>
    <div v-else-if="loading" class="text-slate-500">불러오는 중…</div>

    <article v-else-if="notice" class="rounded-lg border bg-white p-6">
      <div class="mb-2 flex items-center gap-2">
        <span v-if="notice.pinned" title="상단 고정">📌</span>
        <h2 class="text-2xl font-bold text-slate-800">{{ notice.title }}</h2>
      </div>
      <div class="mb-4 flex flex-wrap gap-4 border-b pb-3 text-sm text-slate-500">
        <span>작성자 <b class="text-slate-700">{{ notice.author }}</b></span>
        <span>조회 {{ notice.viewCount }}</span>
        <span>작성 {{ fmt(notice.createdAt) }}</span>
        <span v-if="notice.updatedAt !== notice.createdAt">수정 {{ fmt(notice.updatedAt) }}</span>
      </div>

      <p class="min-h-[8rem] whitespace-pre-wrap text-slate-700">{{ notice.content }}</p>

      <div class="mt-6 flex gap-2">
        <DxButton text="목록" styling-mode="outlined" @click="router.push('/')" />
        <DxButton text="수정" type="default" styling-mode="contained" @click="router.push(`/notices/${id}/edit`)" />
        <DxButton text="삭제" type="danger" styling-mode="contained" @click="onDelete" />
      </div>
    </article>
  </section>
</template>
