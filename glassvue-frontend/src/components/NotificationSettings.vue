<script setup>
/**
 * 알림 켜기/끄기 설정 (2026-07-24). 타입별 토글. 끄면 그 타입 알림은 만들어지지도, 뜨지도 않는다(서버 opt-out).
 * 서버가 모든 타입을 기본값(켜짐)까지 채워 내려주므로 화면은 그대로 그리기만 한다.
 */
import { ref, onMounted } from 'vue';
import { fetchNotificationSettings, updateNotificationSetting } from '../api/notification';

const settings = ref([]);
const loading = ref(true);
const error = ref('');

onMounted(async () => {
  try {
    settings.value = await fetchNotificationSettings();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
});

async function toggle(s) {
  const next = !s.enabled;
  s.enabled = next; // 낙관적
  try {
    await updateNotificationSetting(s.type, next);
  } catch (e) {
    s.enabled = !next; // 롤백
    error.value = e.message;
  }
}
</script>

<template>
  <div class="card flex flex-col gap-4 p-5">
    <div>
      <h2 class="section-title">알림 설정</h2>
      <p class="muted mt-1">끄면 해당 알림이 오지 않아요.</p>
    </div>

    <p v-if="error" class="alert-error">{{ error }}</p>

    <div v-if="loading" class="space-y-2">
      <div class="skeleton h-9 w-full"></div>
      <div class="skeleton h-9 w-full"></div>
    </div>

    <ul v-else class="divide-y divide-line">
      <li v-for="s in settings" :key="s.type" class="flex items-center justify-between py-3">
        <span class="text-sm text-ink-900">{{ s.label }}</span>
        <!-- 토글 스위치 — 켜짐이면 강조색, 꺼짐이면 회색 -->
        <button
          type="button"
          role="switch"
          :aria-checked="s.enabled"
          :aria-label="s.label"
          class="relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
          :class="s.enabled ? 'bg-brand-600' : 'bg-line'"
          @click="toggle(s)"
        >
          <span
            class="inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform"
            :class="s.enabled ? 'translate-x-5' : 'translate-x-0.5'"
          ></span>
        </button>
      </li>
    </ul>
  </div>
</template>
