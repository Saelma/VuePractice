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
      <div>
        <RouterLink to="/" class="text-2xl font-bold text-slate-800 hover:text-blue-600">Glassvue</RouterLink>
        <p class="text-sm text-slate-500">사내 공지 게시판</p>
      </div>
      <div class="flex items-center gap-3 text-sm">
        <template v-if="isLoggedIn">
          <span class="text-slate-600"><b>{{ authState.user?.nickname }}</b>님</span>
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
