<script setup>
/**
 * 알림 전체 보기 (2026-07-24). 벨 드롭다운은 최근 것만, 여기는 페이징으로 전부 본다.
 * 항목 클릭 시 읽음 처리 + 링크 이동. 상단 "모두 읽음".
 */
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { fetchNotifications } from '../api/notification';
import { markRead, markAllRead as storeMarkAllRead, notificationState } from '../stores/notifications';
import EmptyState from '../components/EmptyState.vue';
import SkeletonList from '../components/SkeletonList.vue';

const router = useRouter();
const items = ref([]);
const page = ref(0);
const totalPages = ref(0);
const totalElements = ref(0);
const loading = ref(true);
const error = ref('');

async function load(p = 0) {
  loading.value = true;
  error.value = '';
  try {
    const res = await fetchNotifications({ page: p, size: 20 });
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

async function onClick(n) {
  if (!n.read) {
    n.read = true;
    await markRead(n.id); // 스토어 unread 뱃지도 함께 줄인다
  }
  if (n.link) router.push(n.link);
}

async function onMarkAll() {
  await storeMarkAllRead();
  items.value = items.value.map((n) => ({ ...n, read: true }));
}

function relTime(iso) {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return '방금 전';
  if (min < 60) return `${min}분 전`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}시간 전`;
  return new Date(iso).toLocaleString('ko-KR');
}
</script>

<template>
  <section class="page-narrow">
    <div class="mb-5 flex items-end justify-between gap-4">
      <div>
        <h1 class="page-title">알림</h1>
        <p v-if="!loading" class="muted mt-1">{{ totalElements }}건</p>
      </div>
      <button
        v-if="notificationState.unread > 0"
        type="button"
        class="btn btn-secondary"
        @click="onMarkAll"
      >모두 읽음</button>
    </div>

    <div v-if="error" class="alert-error">{{ error }}</div>

    <SkeletonList v-else-if="loading" :rows="6" />

    <EmptyState v-else-if="!items.length" icon="🔔" message="아직 알림이 없어요." />

    <ul v-else class="card divide-y divide-line">
      <li v-for="n in items" :key="n.id">
        <button
          type="button"
          class="flex w-full flex-col gap-1 px-5 py-4 text-left transition-colors hover:bg-canvas"
          :class="n.read ? '' : 'bg-brand-50'"
          @click="onClick(n)"
        >
          <span class="flex items-center gap-2">
            <span v-if="!n.read" class="h-1.5 w-1.5 shrink-0 rounded-full bg-danger" aria-hidden="true"></span>
            <span class="text-sm font-medium text-ink-900">{{ n.title }}</span>
            <span class="badge badge-neutral ml-auto shrink-0">{{ n.typeLabel }}</span>
          </span>
          <span class="text-sm text-ink-500">{{ n.message }}</span>
          <span class="muted">{{ relTime(n.createdAt) }}</span>
        </button>
      </li>
    </ul>

    <div v-if="!loading && totalPages > 1" class="mt-8 flex items-center justify-center gap-4">
      <button type="button" class="btn btn-secondary" :disabled="page === 0" @click="load(page - 1)">이전</button>
      <span class="text-sm tabular-nums text-ink-500">{{ page + 1 }} / {{ totalPages }}</span>
      <button type="button" class="btn btn-secondary" :disabled="page + 1 >= totalPages" @click="load(page + 1)">다음</button>
    </div>
  </section>
</template>
