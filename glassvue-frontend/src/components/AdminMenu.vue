<script setup>
/**
 * 헤더 「관리 ▾」 드롭다운 (2026-07-28). 관리자 링크(주문·회원·매출·감사)가 메인 nav 에 나란히 섞여
 * 번잡했던 걸 하나로 묶는다(사용자 지적). 트리거는 관리자(isAdmin)만, 감사 이력 항목은 SUPER 만 보인다.
 * 팝오버 방식은 NotificationBell 과 같다(open ref + 바깥클릭 backdrop). 라우팅하면 닫는다.
 */
import { ref, computed } from 'vue';
import { useRoute } from 'vue-router';
import { isAdmin, isSuperAdmin } from '../stores/auth';

const route = useRoute();
const open = ref(false);

// 관리 화면 안에 있으면 트리거를 활성 표시(색이 아니라 농도/굵기 — nav-link 규칙과 동일).
const onAdminRoute = computed(() => route.path.startsWith('/admin'));
</script>

<template>
  <div v-if="isAdmin" class="relative">
    <button
      type="button"
      class="nav-link flex items-center gap-1"
      :class="{ 'router-link-active': onAdminRoute }"
      aria-haspopup="true"
      :aria-expanded="open"
      @click="open = !open"
    >
      관리
      <span class="text-[10px] leading-none" aria-hidden="true">▾</span>
    </button>

    <!-- 바깥 클릭으로 닫기 -->
    <div v-if="open" class="fixed inset-0 z-40" @click="open = false"></div>

    <div
      v-if="open"
      class="absolute left-0 z-50 mt-2 w-40 overflow-hidden rounded-card border border-line bg-surface py-1 shadow-lift"
    >
      <RouterLink to="/admin/orders" class="admin-menu-item" @click="open = false">주문 관리</RouterLink>
      <RouterLink to="/admin/members" class="admin-menu-item" @click="open = false">회원 관리</RouterLink>
      <RouterLink to="/admin/stats" class="admin-menu-item" @click="open = false">매출</RouterLink>
      <template v-if="isSuperAdmin">
        <span class="my-1 block border-t border-line" aria-hidden="true"></span>
        <RouterLink to="/admin/audit" class="admin-menu-item" @click="open = false">감사 이력</RouterLink>
      </template>
    </div>
  </div>
</template>

<style scoped>
.admin-menu-item {
  display: block;
  padding: 0.5rem 1rem;
  font-size: 0.875rem;
  color: var(--color-ink-700);
  transition: background-color 150ms, color 150ms;
}
.admin-menu-item:hover {
  background-color: var(--color-canvas);
  color: var(--color-ink-900);
}
.admin-menu-item.router-link-active {
  color: var(--color-ink-900);
  font-weight: 500;
}
</style>
