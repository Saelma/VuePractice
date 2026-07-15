<script setup>
import { onMounted } from 'vue';
import { RouterLink, RouterView, useRouter } from 'vue-router';
import { authState, isLoggedIn } from './stores/auth';
import { logout as apiLogout, loadMe } from './api/auth';

const router = useRouter();

onMounted(() => {
  loadMe(); // 저장된 토큰으로 내 정보 갱신
});

async function onLogout() {
  await apiLogout();
  router.push('/');
}
</script>

<template>
  <main class="min-h-screen bg-slate-50">
    <header class="flex items-center justify-between border-b bg-white px-6 py-4">
      <div class="flex items-center gap-6">
        <RouterLink to="/" class="text-2xl font-bold text-slate-800 hover:text-blue-600">Glassvue</RouterLink>
        <nav class="flex gap-4 text-sm text-slate-600">
          <RouterLink to="/" class="hover:text-blue-600">공지</RouterLink>
          <RouterLink to="/products" class="hover:text-blue-600">상품</RouterLink>
        </nav>
      </div>
      <div class="flex items-center gap-3 text-sm">
        <template v-if="isLoggedIn">
          <span class="text-slate-600"><b>{{ authState.user?.nickname }}</b>님</span>
          <RouterLink to="/cart" class="text-slate-600 hover:underline">장바구니</RouterLink>
          <RouterLink to="/orders" class="text-slate-600 hover:underline">주문내역</RouterLink>
          <RouterLink to="/settings" class="text-slate-600 hover:underline">내 정보</RouterLink>
          <button class="rounded border px-3 py-1 hover:bg-slate-50" @click="onLogout">로그아웃</button>
        </template>
        <template v-else>
          <RouterLink to="/login" class="text-blue-600 hover:underline">로그인</RouterLink>
          <RouterLink to="/signup" class="rounded bg-blue-600 px-3 py-1 text-white hover:bg-blue-700">회원가입</RouterLink>
        </template>
      </div>
    </header>

    <RouterView />
  </main>
</template>
