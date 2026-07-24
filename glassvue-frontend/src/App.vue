<script setup>
import { computed, onMounted } from 'vue';
import { RouterLink, RouterView, useRouter } from 'vue-router';
import { authState, isLoggedIn } from './stores/auth';
import { logout as apiLogout, loadMe } from './api/auth';

const router = useRouter();
const isAdmin = computed(() => authState.user?.role === 'ADMIN');

onMounted(() => {
  loadMe(); // 저장된 토큰으로 내 정보 갱신
});

async function onLogout() {
  await apiLogout();
  router.push('/');
}
</script>

<template>
  <div class="min-h-screen bg-canvas">
    <!-- sticky 헤더: 목록을 스크롤해도 이동이 항상 닿는다 (DESIGN.md §4) -->
    <header class="sticky top-0 z-40 border-b border-line bg-surface/85 backdrop-blur">
      <div class="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
        <div class="flex items-center gap-7">
          <RouterLink to="/" class="text-lg font-bold tracking-tight text-ink-900">Glassvue</RouterLink>
          <nav class="flex items-center gap-5 text-sm">
            <!-- "/"는 모든 경로의 접두사라 기본 active가 항상 켜진다 → 정확 매칭만 쓴다 -->
            <RouterLink to="/" class="nav-link" active-class="" exact-active-class="router-link-active">공지</RouterLink>
            <RouterLink to="/products" class="nav-link">상품</RouterLink>
            <RouterLink v-if="isAdmin" to="/admin/orders" class="nav-link">주문 관리</RouterLink>
          </nav>
        </div>

        <div class="flex items-center gap-4 text-sm">
          <template v-if="isLoggedIn">
            <RouterLink to="/wishlist" class="nav-link">찜</RouterLink>
            <RouterLink to="/cart" class="nav-link">장바구니</RouterLink>
            <RouterLink to="/orders" class="nav-link">주문내역</RouterLink>
            <span class="hidden h-4 w-px bg-line sm:block"></span>
            <RouterLink to="/settings" class="hidden text-ink-700 hover:text-ink-900 sm:block">
              <b class="font-medium">{{ authState.user?.nickname }}</b>
            </RouterLink>
            <button
              type="button"
              class="rounded-control border border-line px-3 py-1.5 text-ink-700 transition-colors hover:bg-canvas"
              @click="onLogout"
            >로그아웃</button>
          </template>
          <template v-else>
            <RouterLink to="/login" class="nav-link">로그인</RouterLink>
            <RouterLink
              to="/signup"
              class="rounded-control bg-brand-600 px-3 py-1.5 font-medium text-white transition-colors hover:bg-brand-700"
            >회원가입</RouterLink>
          </template>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-6xl">
      <RouterView />
    </main>
  </div>
</template>
