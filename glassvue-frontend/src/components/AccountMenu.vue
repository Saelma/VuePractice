<script setup>
/**
 * 헤더 계정 드롭다운 (2026-07-28). 닉네임 ▾ 를 눌러 주문내역·찜·설정·로그아웃을 편다.
 * 우측에 흩어져 있던 찜·주문내역·설정·로그아웃을 하나로 묶어 헤더 번잡을 줄인다(사용자 지적).
 * 장바구니는 결제 핵심 동선이라 밖에 아이콘으로 남긴다(App.vue). 팝오버는 NotificationBell 과 같은 패턴.
 */
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { authState } from '../stores/auth';
import { logout as apiLogout } from '../api/auth';

const router = useRouter();
const open = ref(false);

async function onLogout() {
  open.value = false;
  await apiLogout();
  router.push('/');
}
</script>

<template>
  <div class="relative">
    <button
      type="button"
      class="flex items-center gap-1 text-ink-700 transition-colors hover:text-ink-900"
      aria-haspopup="true"
      :aria-expanded="open"
      @click="open = !open"
    >
      <b class="font-medium">{{ authState.user?.nickname }}</b>
      <span class="text-[10px] leading-none" aria-hidden="true">▾</span>
    </button>

    <!-- 바깥 클릭으로 닫기 -->
    <div v-if="open" class="fixed inset-0 z-40" @click="open = false"></div>

    <div
      v-if="open"
      class="absolute right-0 z-50 mt-2 w-40 overflow-hidden rounded-card border border-line bg-surface py-1 shadow-lift"
    >
      <RouterLink to="/orders" class="account-menu-item" @click="open = false">주문내역</RouterLink>
      <RouterLink to="/wishlist" class="account-menu-item" @click="open = false">찜</RouterLink>
      <RouterLink to="/settings" class="account-menu-item" @click="open = false">설정</RouterLink>
      <span class="my-1 block border-t border-line" aria-hidden="true"></span>
      <button type="button" class="account-menu-item w-full text-left" @click="onLogout">로그아웃</button>
    </div>
  </div>
</template>

<style scoped>
.account-menu-item {
  display: block;
  padding: 0.5rem 1rem;
  font-size: 0.875rem;
  color: var(--color-ink-700);
  transition: background-color 150ms, color 150ms;
}
.account-menu-item:hover {
  background-color: var(--color-canvas);
  color: var(--color-ink-900);
}
.account-menu-item.router-link-active {
  color: var(--color-ink-900);
  font-weight: 500;
}
</style>
