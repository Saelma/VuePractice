<script setup>
import { ref, onMounted } from 'vue';
import { DxTextBox } from 'devextreme-vue/text-box';
import { fetchCategories, createCategory, deleteCategory } from '../api/category';
import EmptyState from '../components/EmptyState.vue';

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
  <section class="page-narrow">
    <h1 class="page-title mb-5">카테고리 관리</h1>

    <div v-if="error" class="alert-error mb-3">{{ error }}</div>
    <div v-if="msg" class="alert-success mb-3">{{ msg }}</div>

    <!-- 추가 폼 -->
    <div class="card mb-6 flex flex-wrap items-end gap-3 p-4">
      <label class="field">
        <span class="field-label">카테고리 이름</span>
        <DxTextBox v-model:value="name" placeholder="새 카테고리" :width="200" @enter-key="onAdd" />
      </label>
      <button type="button" class="btn btn-primary" @click="onAdd">추가</button>
    </div>

    <!-- 목록 -->
    <ul v-if="categories.length" class="card divide-y divide-line">
      <li v-for="c in categories" :key="c.id" class="flex items-center justify-between gap-3 px-5 py-3">
        <span class="text-sm text-ink-900">{{ c.name }}</span>
        <button type="button" class="btn btn-danger" @click="onDelete(c)">삭제</button>
      </li>
    </ul>

    <!-- 빈 상태 -->
    <EmptyState
      v-else
      icon="🗂️"
      message="아직 등록된 카테고리가 없어요."
      hint="위 입력란에 이름을 적고 “추가”를 누르세요."
    />
  </section>
</template>
