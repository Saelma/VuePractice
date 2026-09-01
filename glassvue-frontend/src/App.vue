<script setup>
import { computed, ref, onMounted, watch } from 'vue';
import { RouterLink, RouterView, useRouter } from 'vue-router';
import { authState, isLoggedIn } from './stores/auth';
import { loadMe } from './api/auth';
import { connectNotifications, disconnectNotifications } from './stores/notifications';
import { cartState, loadCartCount, clearCartCount } from './stores/cart';
import NotificationBell from './components/NotificationBell.vue';
import NotificationToaster from './components/NotificationToaster.vue';
import AdminMenu from './components/AdminMenu.vue';
import AccountMenu from './components/AccountMenu.vue';
import RecentSearches from './components/RecentSearches.vue';
import { recentSearches, pushRecentSearch } from './stores/recentSearches';
import { useLoginRedirect } from './composables/useLoginRedirect';

const router = useRouter();
const searchQuery = ref('');

/**
 * 헤더의 「로그인」·「회원가입」이 **지금 보던 자리를 들고 간다** (2026-09-01, BACKLOG J-2).
 * 🔴 **전에는 `to="/login"` 이라 쿼리가 없어, 상품 상세에서 «로그인이 필요해요» 를 읽고 헤더로 온
 * 사람이 로그인 후 홈으로 떨어졌다.** 규칙 자체는 `useLoginRedirect` 한 곳에 있다.
 */
const { loginTo, signupTo } = useLoginRedirect();
/** 최근 검색어 패널이 펴져 있나 (G-7). 목록이 비어 있으면 아예 안 편다 — 빈 상자가 뜨면 고장으로 읽힌다. */
const searchOpen = ref(false);

onMounted(() => {
  // 저장된 토큰으로 내 정보 갱신. 라우터 가드가 보호 경로 진입 때 이미 로드했으면(authState.user 존재)
  // 중복 /me 요청을 피한다 — 공개 경로로 처음 들어온 경우엔 여기서 채운다(헤더 닉네임·관리자 nav용).
  if (!authState.user) loadMe();
  if (isLoggedIn.value) {
    connectNotifications(); // 이미 로그인 상태면 알림 스트림 연결
    loadCartCount(); // 🛒 배지 초기값
  }
});

// 로그인/로그아웃에 맞춰 알림 스트림·장바구니 배지를 붙이고 뗀다(로그아웃 시 다음 사용자에게 안 새게).
watch(isLoggedIn, (loggedIn) => {
  if (loggedIn) {
    connectNotifications();
    loadCartCount(true);
  } else {
    disconnectNotifications();
    clearCartCount();
  }
});

/** 로고 클릭 — 홈으로 가되 이미 홈이면 라우팅이 없어 밋밋하니 맨 위로 스크롤(사용자 지적, 2026-07-28). */
function onLogo() {
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

/** 헤더 전역 검색 — 상품 목록으로 이름 쿼리를 넘긴다. 목록 화면이 ?name= 을 읽어 필터한다(B-8). */
function onSearch() {
  const q = searchQuery.value.trim();
  // 🔴 «검색을 실행한 순간» 에만 담는다(G-7) — ?name= 이 적용될 때마다 담으면 남이 공유한 링크를
  //    열기만 해도 내 검색어가 된다. 목록 필터(ProductListView.apply)도 같은 함수를 부른다.
  if (q) pushRecentSearch(q);
  searchOpen.value = false;
  router.push(q ? { path: '/products', query: { name: q } } : { path: '/products' });
}

/** 최근 검색어를 누르면 그 말로 다시 검색한다 — 담기·이동이 onSearch 한 곳에만 있게 되짚어 부른다. */
function onPickRecent(term) {
  searchQuery.value = term;
  onSearch();
}

const year = new Date().getFullYear();
</script>

<template>
  <div class="flex min-h-screen flex-col bg-canvas">
    <!-- sticky 헤더: 목록을 스크롤해도 이동이 항상 닿는다 (DESIGN.md §4) -->
    <header class="sticky top-0 z-40 border-b border-line bg-surface/85 backdrop-blur">
      <div class="mx-auto flex h-14 max-w-7xl items-center gap-4 px-4 sm:px-6">
        <div class="flex items-center gap-7">
          <RouterLink to="/" class="text-lg font-bold tracking-tight text-ink-900" @click="onLogo">Glassvue</RouterLink>
          <nav class="flex items-center gap-5 text-sm">
            <!-- 「홈」 링크는 뺐다 — 로고(Glassvue)가 이미 "/" 로 간다(중복, 2026-07-28) -->
            <RouterLink to="/products" class="nav-link">상품</RouterLink>
            <RouterLink to="/notices" class="nav-link">공지</RouterLink>
            <!-- 관리자 링크(주문·회원·매출·감사)는 「관리 ▾」 하나로 묶는다 — 메인 nav 번잡 해소(2026-07-28) -->
            <AdminMenu />
          </nav>
        </div>

        <!-- 전역 상품 검색 (좁은 화면에선 감춘다 — /products 안의 검색으로 대체) -->
        <form class="hidden flex-1 sm:block" role="search" @submit.prevent="onSearch">
          <div class="relative mx-auto max-w-xs">
            <input
              v-model="searchQuery"
              type="search"
              placeholder="상품 검색"
              aria-label="상품 검색"
              class="w-full rounded-control border border-line bg-canvas px-3 py-1.5 text-sm text-ink-900 placeholder:text-ink-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
              @focus="searchOpen = true"
              @keydown.escape="searchOpen = false"
            />

            <!-- 최근 검색어 (G-7). 바깥 클릭으로 닫는다 — AccountMenu 와 같은 패턴(오버레이 z-40 / 패널 z-50). -->
            <template v-if="searchOpen && recentSearches.length">
              <div class="fixed inset-0 z-40" @click="searchOpen = false"></div>
              <div class="absolute left-0 right-0 z-50 mt-2 rounded-card border border-line bg-surface p-2 shadow-lift">
                <RecentSearches @pick="onPickRecent" />
              </div>
            </template>
          </div>
        </form>

        <div class="flex items-center gap-4 text-sm">
          <template v-if="isLoggedIn">
            <NotificationBell />
            <!-- 장바구니는 결제 핵심 동선이라 밖에 아이콘으로 남긴다(찜·주문내역·설정·로그아웃은 계정 메뉴로) -->
            <RouterLink
              to="/cart"
              class="relative flex h-9 w-9 items-center justify-center rounded-control text-ink-700 transition-colors hover:bg-canvas"
              :aria-label="`장바구니${cartState.count ? ` ${cartState.count}개` : ''}`"
            >
              <span class="text-lg" aria-hidden="true">🛒</span>
              <span
                v-if="cartState.count > 0"
                class="absolute -right-0.5 -top-0.5 min-w-4 rounded-full bg-brand-600 px-1 text-center text-[10px] font-bold leading-4 text-white"
              >{{ cartState.count > 99 ? '99+' : cartState.count }}</span>
            </RouterLink>
            <span class="hidden h-4 w-px bg-line sm:block"></span>
            <AccountMenu />
          </template>
          <template v-else>
            <RouterLink :to="loginTo" class="nav-link">로그인</RouterLink>
            <RouterLink
              :to="signupTo"
              class="rounded-control bg-brand-600 px-3 py-1.5 font-medium text-white transition-colors hover:bg-brand-700"
            >회원가입</RouterLink>
          </template>
        </div>
      </div>
    </header>

    <main class="mx-auto w-full max-w-7xl flex-1">
      <RouterView />
    </main>

    <!-- 사이트 푸터 — 커머스 완성도. 정적이고 링크는 전부 내부다(연습 단계라 외부 의존을 만들지 않는다). -->
    <footer class="mt-16 border-t border-line bg-surface">
      <div class="mx-auto max-w-7xl px-4 py-10 sm:px-6">
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
            <RouterLink to="/benefits" class="text-ink-500 transition-colors hover:text-ink-900">혜택</RouterLink>
            <RouterLink to="/settings" class="text-ink-500 transition-colors hover:text-ink-900">내 정보</RouterLink>
            <!-- 고객센터(G-3, 2026-08-07) — 커머스 푸터에 있어야 할 것이 여기만 비어 있었다.
                 «상품과 무관한 문의를 할 데» 를 찾는 사람이 처음 보는 자리다. -->
            <RouterLink to="/support" class="text-ink-500 transition-colors hover:text-ink-900">고객센터</RouterLink>
          </nav>
        </div>
        <p class="muted mt-8 border-t border-line pt-6">© {{ year }} Glassvue — 데모 프로젝트</p>
      </div>
    </footer>

    <!-- 알림 토스트 — 새 알림이 SSE 로 오면 오른쪽 위에서 슬라이드로 나온다 -->
    <NotificationToaster />
  </div>
</template>
