<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxButton } from 'devextreme-vue/button';
import { signup, login } from '../api/auth';

const router = useRouter();
const form = reactive({ loginId: '', password: '', nickname: '' });
const error = ref('');
const loading = ref(false);

async function onSubmit() {
  error.value = '';
  if (!form.loginId || !form.password || !form.nickname) {
    error.value = '모든 항목을 입력하세요.';
    return;
  }
  if (form.loginId.length < 4) {
    error.value = '아이디는 4자 이상이어야 합니다.';
    return;
  }
  if (form.password.length < 8) {
    error.value = '비밀번호는 8자 이상이어야 합니다.';
    return;
  }
  loading.value = true;
  try {
    await signup({ loginId: form.loginId, password: form.password, nickname: form.nickname });
    await login({ loginId: form.loginId, password: form.password }); // 가입 후 자동 로그인
    router.push('/');
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="mx-auto max-w-sm p-6">
    <h2 class="mb-4 text-xl font-semibold text-slate-800">회원가입</h2>
    <div v-if="error" class="mb-4 rounded bg-red-50 p-3 text-red-600">{{ error }}</div>

    <div class="flex flex-col gap-4 rounded-lg border bg-white p-6">
      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">아이디 (4자 이상)</span>
        <DxTextBox v-model:value="form.loginId" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">비밀번호 (8자 이상)</span>
        <DxTextBox v-model:value="form.password" mode="password" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">닉네임</span>
        <DxTextBox v-model:value="form.nickname" @enter-key="onSubmit" />
      </label>
      <DxButton
        :text="loading ? '처리 중…' : '가입하기'"
        type="default"
        styling-mode="contained"
        :disabled="loading"
        @click="onSubmit"
      />
    </div>
  </section>
</template>
