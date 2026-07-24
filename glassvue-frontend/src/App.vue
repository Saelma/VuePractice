<script setup>
import { computed, ref, onMounted, watch } from 'vue';
import { RouterLink, RouterView, useRouter } from 'vue-router';
import { authState, isLoggedIn } from './stores/auth';
import { logout as apiLogout, loadMe } from './api/auth';
import { connectNotifications, disconnectNotifications } from './stores/notifications';
import NotificationBell from './components/NotificationBell.vue';
import NotificationToaster from './components/NotificationToaster.vue';

const router = useRouter();
const isAdmin = computed(() => authState.user?.role === 'ADMIN');
const searchQuery = ref('');

onMounted(() => {
  loadMe(); // 저장된 토큰으로 내 정보 갱신
  if (isLoggedIn.value) connectNotifications(); // 이미 로그인 상태면 알림 스트림 연결
});

// 로그인/로그아웃에 맞춰 알림 스트림을 붙이고 뗀다(로그아웃 시 다음 사용자에게 안 새게 끊는다).
watch(isLoggedIn, (loggedIn) => {
  if (loggedIn) connectNotifications();
  else disconnectNotifications();
});

async function onLogout() {
  await apiLogout();
  router.push('/');
}

/** 헤더 전역 검색 — 상품 목록으로 이름 쿼리를 넘긴다. 목록 화면이 ?name= 을 읽어 필터한다(B-8). */
function onSearch() {
  const q = searchQuery.value.trim();
  router.push(q ? { path: '/products', query: { name: q } } : { path: '/products' });
}

const year = new Date().getFullYear();
</script>

<template>
  <div class="flex min-h-screen flex-col bg-canvas">
    <!-- sticky 헤더: 목록을 스크롤해도 이동이 항상 닿는다 (DESIGN.md §4) -->
    <header class="sticky top-0 z-40 border-b border-line bg-surface/85 backdrop-blur">
      <div class="mx-auto flex h-14 max-w-6xl items-center gap-4 px-4 sm:px-6">
        <div class="flex items-center gap-7">
          <RouterLink to="/" class="text-lg font-bold tracking-tight text-ink-900">Glassvue</RouterLink>
          <nav class="flex items-center gap-5 text-sm">
            <!-- "/"는 모든 경로의 접두사라 기본 active가 항상 켜진다 → 홈은 정확 매칭만 쓴다 -->
            <RouterLink to="/" class="nav-link" active-class="" exact-active-class="router-link-active">홈</RouterLink>
            <RouterLink to="/products" class="nav-link">상품</RouterLink>
            <RouterLink to="/notices" class="nav-link">공지</RouterLink>
            <RouterLink v-if="isAdmin" to="/admin/orders" class="nav-link">주문 관리</RouterLink>
            <RouterLink v-if="isAdmin" to="/admin/stats" class="nav-link">매출</RouterLink>
          </nav>
        </div>

        <!-- 전역 상품 검색 (좁은 화면에선 감춘다 — /products 안의 검색으로 대체) -->
        <form class="hidden flex-1 sm:block" role="search" @submit.prevent="onSearch">
          <div class="mx-auto max-w-xs">
            <input
              v-model="searchQuery"
              type="search"
              placeholder="상품 검색"
              aria-label="상품 검색"
              class="w-full rounded-control border border-line bg-canvas px-3 py-1.5 text-sm text-ink-900 placeholder:text-ink-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
            />
          </div>
        </form>

        <div class="flex items-center gap-4 text-sm">
          <template v-if="isLoggedIn">
            <RouterLink to="/wishlist" class="nav-link">찜</RouterLink>
            <RouterLink to="/cart" class="nav-link">장바구니</RouterLink>
            <RouterLink to="/orders" class="nav-link">주문내역</RouterLink>
            <NotificationBell />
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

    <main class="mx-auto w-full max-w-6xl flex-1">
      <RouterView />
    </main>

    <!-- 사이트 푸터 — 커머스 완성도. 정적이고 링크는 전부 내부다(연습 단계라 외부 의존을 만들지 않는다). -->
    <footer class="mt-16 border-t border-line bg-surface">
      <div class="mx-auto max-w-6xl px-4 py-10 sm:px-6">
        <div class="flex flex-col gap-8 sm:flex-row sm:justify-between">
          <div class="max-w-sm">
            <p class="text-lg font-bold tracking-tight text-ink-900">Glassvue</p>
            <p class="muted mt-2 leading-relaxed">
              매일 쓰는 것들을 조금 더 좋은 것으로. 학습용으로 만든 데모 스토어입니다.
            </p>
          </div>
          <nav class="grid grid-cols-2 gap-x-12 gap-y-2 text-sm sm:grid-cols-3">
            <RouterLink to="/products" class="text-ink-500 transition-colors hover:text-ink-900">상품</RouterLink>
            <RouterLink to="/notices" class="text-ink-500 transition-colors hover:text-ink-900">공지</RouterLink>
            <RouterLink to="/wishlist" class="text-ink-500 transition-colors hover:text-ink-900">찜</RouterLink>
            <RouterLink to="/cart" class="text-ink-500 transition-colors hover:text-ink-900">장바구니</RouterLink>
            <RouterLink to="/orders" class="text-ink-500 transition-colors hover:text-ink-900">주문내역</RouterLink>
            <RouterLink to="/settings" class="text-ink-500 transition-colors hover:text-ink-900">내 정보</RouterLink>
          </nav>
        </div>
        <p class="muted mt-8 border-t border-line pt-6">© {{ year }} Glassvue — 데모 프로젝트</p>
      </div>
    </footer>

    <!-- 알림 토스트 — 새 알림이 SSE 로 오면 오른쪽 위에서 슬라이드로 나온다 -->
    <NotificationToaster />
  </div>
</template>
