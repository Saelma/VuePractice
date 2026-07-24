<script setup>
/**
 * 알림 토스트 (2026-07-24). 새 알림이 SSE 로 도착하면 오른쪽 위에서 슬라이드로 툭 나온다.
 * 5초 뒤 자동으로 사라지고, 누르면 링크로 이동한다. 상태는 stores/notifications 의 latestToast 를 지켜본다.
 */
import { ref, watch, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { latestToast, markRead } from '../stores/notifications';

const router = useRouter();
const toasts = ref([]);
const timers = new Map();

watch(latestToast, (n) => {
  if (!n) return;
  toasts.value = [...toasts.value.filter((t) => t.id !== n.id), n].slice(-4); // 최근 4개까지 쌓임
  const timer = setTimeout(() => dismiss(n.id), 5000);
  timers.set(n.id, timer);
});

function dismiss(id) {
  toasts.value = toasts.value.filter((t) => t.id !== id);
  const timer = timers.get(id);
  if (timer) {
    clearTimeout(timer);
    timers.delete(id);
  }
}

async function onClick(n) {
  await markRead(n.id);
  dismiss(n.id);
  if (n.link) router.push(n.link);
}

onBeforeUnmount(() => {
  timers.forEach((t) => clearTimeout(t));
  timers.clear();
});
</script>

<template>
  <div class="pointer-events-none fixed right-4 top-16 z-[60] flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2">
    <TransitionGroup name="toast">
      <button
        v-for="n in toasts"
        :key="n.id"
        type="button"
        class="pointer-events-auto flex flex-col gap-0.5 rounded-card border border-line bg-surface p-4 text-left shadow-lift"
        @click="onClick(n)"
      >
        <span class="flex items-center gap-2">
          <span class="text-base" aria-hidden="true">🔔</span>
          <span class="truncate text-sm font-semibold text-ink-900">{{ n.title }}</span>
        </span>
        <span class="line-clamp-2 pl-6 text-xs text-ink-500">{{ n.message }}</span>
      </button>
    </TransitionGroup>
  </div>
</template>

<style scoped>
/* 오른쪽에서 툭 — 들어올 땐 슬라이드+페이드, 나갈 땐 페이드 */
.toast-enter-active {
  transition: transform 0.28s ease, opacity 0.28s ease;
}
.toast-leave-active {
  transition: opacity 0.2s ease;
}
.toast-enter-from {
  transform: translateX(110%);
  opacity: 0;
}
.toast-leave-to {
  opacity: 0;
}
</style>
