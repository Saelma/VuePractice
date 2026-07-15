<script setup>
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxButton } from 'devextreme-vue/button';
import { login } from '../api/auth';

const route = useRoute();
const router = useRouter();
const form = reactive({ loginId: '', password: '' });
const error = ref('');
const loading = ref(false);

async function onSubmit() {
  error.value = '';
  if (!form.loginId || !form.password) {
    error.value = '아이디와 비밀번호를 입력하세요.';
    return;
  }
  loading.value = true;
  try {
    await login({ loginId: form.loginId, password: form.password });
    router.push(route.query.redirect || '/');
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="mx-auto max-w-sm p-6">
    <h2 class="mb-4 text-xl font-semibold text-slate-800">로그인</h2>
    <div v-if="error" class="mb-4 rounded bg-red-50 p-3 text-red-600">{{ error }}</div>

    <div class="flex flex-col gap-4 rounded-lg border bg-white p-6">
      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">아이디</span>
        <DxTextBox v-model:value="form.loginId" @enter-key="onSubmit" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">비밀번호</span>
        <DxTextBox v-model:value="form.password" mode="password" @enter-key="onSubmit" />
      </label>
      <DxButton
        :text="loading ? '로그인 중…' : '로그인'"
        type="default"
        styling-mode="contained"
        :disabled="loading"
        @click="onSubmit"
      />
      <p class="text-center text-sm text-slate-500">
        계정이 없나요?
        <RouterLink to="/signup" class="text-blue-600 hover:underline">회원가입</RouterLink>
      </p>
    </div>
  </section>
</template>
