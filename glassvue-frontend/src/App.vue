<script setup>
import { ref } from 'vue';
import { DxButton } from 'devextreme-vue/button';

const count = ref(0);
</script>

<template>
  <main class="min-h-screen flex flex-col items-center justify-center gap-6 bg-slate-50">
    <h1 class="text-3xl font-bold text-slate-800">ESP Frontend</h1>
    <p class="text-slate-600">Vue 3 · rsbuild · DevExtreme · Tailwind CSS</p>

    <DxButton
      :text="`클릭 수: ${count}`"
      type="default"
      styling-mode="contained"
      @click="count++"
    />
  </main>
</template>
