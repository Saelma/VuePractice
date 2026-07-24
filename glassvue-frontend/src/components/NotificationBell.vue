<script setup>
/**
 * 헤더 알림 벨 (2026-07-24). 안읽음 뱃지 + 드롭다운 목록. 항목을 누르면 읽음 처리하고 링크로 이동한다.
 * 실시간 수신·상태는 stores/notifications.js(SSE)가 들고, 이 컴포넌트는 그 상태를 그린다.
 */
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { notificationState, loadRecent, markRead, markAllRead } from '../stores/notifications';

const router = useRouter();
const open = ref(false);

async function toggle() {
  open.value = !open.value;
  if (open.value && !notificationState.loaded) await loadRecent();
}

async function onClickItem(n) {
  await markRead(n.id);
  open.value = false;
  if (n.link) router.push(n.link);
}

function relTime(iso) {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return '방금 전';
  if (min < 60) return `${min}분 전`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}시간 전`;
  return new Date(iso).toLocaleDateString('ko-KR');
}
</script>

<template>
  <div class="relative">
    <button
      type="button"
      class="relative flex h-9 w-9 items-center justify-center rounded-control text-ink-700 transition-colors hover:bg-canvas"
      :aria-label="`알림${notificationState.unread ? ` ${notificationState.unread}건 안읽음` : ''}`"
      @click="toggle"
    >
      <span class="text-lg" aria-hidden="true">🔔</span>
      <span
        v-if="notificationState.unread > 0"
        class="absolute -right-0.5 -top-0.5 min-w-4 rounded-full bg-danger px-1 text-center text-[10px] font-bold leading-4 text-white"
      >{{ notificationState.unread > 99 ? '99+' : notificationState.unread }}</span>
    </button>

    <!-- 바깥 클릭으로 닫기 -->
    <div v-if="open" class="fixed inset-0 z-40" @click="open = false"></div>

    <div
      v-if="open"
      class="absolute right-0 z-50 mt-2 w-80 overflow-hidden rounded-card border border-line bg-surface shadow-lift"
    >
      <div class="flex items-center justify-between border-b border-line px-4 py-3">
        <span class="text-sm font-semibold text-ink-900">알림</span>
        <button
          v-if="notificationState.unread > 0"
          type="button"
          class="text-xs text-ink-500 hover:text-ink-900"
          @click="markAllRead"
        >모두 읽음</button>
      </div>

      <div class="max-h-96 overflow-y-auto">
        <p v-if="!notificationState.items.length" class="px-4 py-10 text-center text-sm text-ink-400">
          알림이 없어요.
        </p>
        <ul v-else class="divide-y divide-line">
          <li v-for="n in notificationState.items" :key="n.id">
            <button
              type="button"
              class="flex w-full flex-col gap-0.5 px-4 py-3 text-left transition-colors hover:bg-canvas"
              :class="n.read ? '' : 'bg-brand-50'"
              @click="onClickItem(n)"
            >
              <span class="flex items-center gap-2">
                <span v-if="!n.read" class="h-1.5 w-1.5 shrink-0 rounded-full bg-danger" aria-hidden="true"></span>
                <span class="truncate text-sm font-medium text-ink-900">{{ n.title }}</span>
              </span>
              <span class="line-clamp-2 text-xs text-ink-500">{{ n.message }}</span>
              <span class="text-[11px] text-ink-400">{{ relTime(n.createdAt) }}</span>
            </button>
          </li>
        </ul>
      </div>

      <RouterLink
        to="/notifications"
        class="block border-t border-line px-4 py-2.5 text-center text-xs text-ink-500 transition-colors hover:bg-canvas hover:text-ink-900"
        @click="open = false"
      >전체 보기</RouterLink>
    </div>
  </div>
</template>
