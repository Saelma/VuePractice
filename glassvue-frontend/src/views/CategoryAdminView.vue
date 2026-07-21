<script setup>
import { ref, onMounted } from 'vue';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxButton } from 'devextreme-vue/button';
import { fetchCategories, createCategory, deleteCategory } from '../api/category';

const categories = ref([]);
const name = ref('');
const error = ref('');
const msg = ref('');

async function load() {
  try {
    categories.value = await fetchCategories();
  } catch (e) {
    error.value = e.message;
  }
}
onMounted(load);

async function onAdd() {
  error.value = '';
  msg.value = '';
  if (!name.value.trim()) {
    error.value = '카테고리 이름을 입력하세요.';
    return;
  }
  try {
    await createCategory(name.value.trim());
    msg.value = '추가되었습니다.';
    name.value = '';
    await load();
  } catch (e) {
    error.value = e.message;
  }
}

async function onDelete(c) {
  error.value = '';
  msg.value = '';
  // eslint-disable-next-line no-alert
  if (!window.confirm(`'${c.name}' 카테고리를 삭제할까요?`)) return;
  try {
    await deleteCategory(c.id);
    msg.value = '삭제되었습니다.';
    await load();
  } catch (e) {
    // 소속 상품이 있으면 서버가 막는다 — 메시지를 그대로 노출("소속 상품이 있어 삭제할 수 없습니다.").
    error.value = e.message;
  }
}
</script>

<template>
  <section class="mx-auto max-w-md p-6">
    <h2 class="mb-4 text-xl font-semibold text-slate-800">카테고리 관리</h2>
    <div v-if="error" class="mb-3 rounded bg-red-50 p-3 text-red-600">{{ error }}</div>
    <div v-if="msg" class="mb-3 rounded bg-green-50 p-3 text-green-600">{{ msg }}</div>

    <div class="mb-6 flex gap-2 rounded-lg border bg-white p-4">
      <DxTextBox v-model:value="name" placeholder="새 카테고리" :width="200" @enter-key="onAdd" />
      <DxButton text="추가" type="default" styling-mode="contained" @click="onAdd" />
    </div>

    <ul class="divide-y rounded-lg border bg-white">
      <li v-for="c in categories" :key="c.id" class="flex items-center justify-between px-4 py-3 text-slate-700">
        <span>{{ c.name }}</span>
        <button
          class="rounded px-2 py-1 text-sm text-red-600 hover:bg-red-50"
          type="button"
          @click="onDelete(c)"
        >삭제</button>
      </li>
      <li v-if="!categories.length" class="px-4 py-3 text-slate-400">등록된 카테고리가 없습니다.</li>
    </ul>
  </section>
</template>
