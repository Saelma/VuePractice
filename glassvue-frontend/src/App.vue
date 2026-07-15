<script setup>
import { RouterLink, RouterView } from 'vue-router';
</script>

<template>
  <main class="min-h-screen bg-slate-50">
    <header class="border-b bg-white px-6 py-4">
      <RouterLink to="/" class="text-2xl font-bold text-slate-800 hover:text-blue-600">
        Glassvue
      </RouterLink>
      <p class="text-sm text-slate-500">사내 공지 게시판</p>
    </header>

    <RouterView />
  </main>
</template>
