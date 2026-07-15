<script setup>
import NoticeList from './components/NoticeList.vue';
</script>

<template>
  <main class="min-h-screen bg-slate-50">
    <header class="border-b bg-white px-6 py-4">
      <h1 class="text-2xl font-bold text-slate-800">Glassvue</h1>
      <p class="text-sm text-slate-500">사내 공지 게시판</p>
    </header>

    <NoticeList />
  </main>
</template>
