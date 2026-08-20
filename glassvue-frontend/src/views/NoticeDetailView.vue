<script setup>
/**
 * 공지 상세 — 읽는 화면이라 폭을 좁게(page-narrow) 잡고 제목 → 메타 → 본문 순으로 읽히게 한다(DESIGN.md §7).
 */
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getNotice, deleteNotice, increaseView } from '../api/notice';
import { authState, isAdminRole } from '../stores/auth';

const props = defineProps({ id: { type: String, required: true } });
const router = useRouter();

const notice = ref(null);
const error = ref('');
const loading = ref(true);

// 🔴 **관리자만** 수정/삭제 (2026-08-20, BACKLOG E-4). 공지는 관리자 콘텐츠다.
//
// ⚠ 전에는 «본인 글이거나 ADMIN» 이었다. 공지가 관리자 전용이 되면서 **본인 글 갈래는 뜻을 잃는다** —
//    일반 회원은 이제 공지를 못 쓰고, 이미 쓴 글도 못 고친다(의도한 결과다).
//    ⚠ 운영에 그런 글이 1건 있다(검증 데이터라 그대로 둔다).
// ⚠ 이름도 `isOwner` → `canManage` 로 바꾼다. 뜻이 «소유» 가 아니라 «권한» 이 됐는데 이름이 그대로면
//    다음 사람이 소유권 규칙이 남아 있다고 읽는다(WA §2-10 — 이름을 바꾸는 쪽이 더 위험하다).
const canManage = computed(() => isAdminRole(authState.user?.role));

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
    router.push('/notices');
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
    <!-- 돌아가기: 읽는 화면에선 이탈 경로가 위에 있는 게 자연스럽다 -->
    <button type="button" class="btn btn-ghost -ml-2 mb-4" @click="router.push('/notices')">← 목록</button>

    <div v-if="error" class="alert-error">{{ error }}</div>

    <!-- 로딩: 제목 → 메타 → 본문 순서 그대로 자리를 잡아둔다 (DESIGN.md §5) -->
    <div v-else-if="loading">
      <div class="skeleton h-8 w-3/4"></div>
      <div class="mt-3 flex gap-3">
        <div class="skeleton h-3 w-20"></div>
        <div class="skeleton h-3 w-24"></div>
        <div class="skeleton h-3 w-16"></div>
      </div>
      <div class="mt-8 space-y-3">
        <div class="skeleton h-4 w-full"></div>
        <div class="skeleton h-4 w-11/12"></div>
        <div class="skeleton h-4 w-4/5"></div>
      </div>
    </div>

    <article v-else-if="notice">
      <!-- 제목: 카드에 가두지 않고 페이지 제목으로 올려 위계를 준다(문서를 읽는 화면) -->
      <div class="flex flex-wrap items-center gap-2">
        <span v-if="notice.pinned" class="badge badge-neutral">📌 고정</span>
      </div>
      <h1 class="mt-2 text-3xl font-bold leading-snug tracking-tight text-ink-900">{{ notice.title }}</h1>

      <div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 border-b border-line pb-5 text-xs text-ink-500">
        <span><b class="font-medium text-ink-700">{{ notice.author }}</b></span>
        <span aria-hidden="true">·</span>
        <span class="tabular-nums">{{ fmt(notice.createdAt) }}</span>
        <span v-if="notice.updatedAt !== notice.createdAt" class="tabular-nums">(수정 {{ fmt(notice.updatedAt) }})</span>
        <span aria-hidden="true">·</span>
        <span class="tabular-nums">조회 {{ notice.viewCount }}</span>
      </div>

      <!-- 본문: 읽기 편하게 줄간격·글자를 키운다(보조 텍스트와 확실히 구분) -->
      <div class="whitespace-pre-wrap py-8 text-[15px] leading-7 text-ink-700">{{ notice.content }}</div>

      <div class="flex items-center gap-2 border-t border-line pt-5">
        <button type="button" class="btn btn-secondary" @click="router.push('/notices')">목록</button>
        <template v-if="canManage">
          <button type="button" class="btn btn-secondary" @click="router.push(`/notices/${id}/edit`)">수정</button>
          <button type="button" class="btn btn-danger ml-auto" @click="onDelete">삭제</button>
        </template>
      </div>
    </article>
  </section>
</template>
