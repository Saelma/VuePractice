<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
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
  <section class="page-narrow">
    <div class="mx-auto max-w-sm">
      <div class="card p-6">
        <h1 class="page-title">회원가입</h1>
        <p class="muted mt-1">가입이 끝나면 자동으로 로그인됩니다.</p>

        <div v-if="error" class="alert-error mt-5">{{ error }}</div>

        <div class="mt-5 flex flex-col gap-4">
          <label class="field">
            <span class="field-label">아이디</span>
            <DxTextBox v-model:value="form.loginId" />
            <span class="muted">4자 이상</span>
          </label>
          <label class="field">
            <span class="field-label">비밀번호</span>
            <DxTextBox v-model:value="form.password" mode="password" />
            <span class="muted">8자 이상</span>
          </label>
          <label class="field">
            <span class="field-label">닉네임</span>
            <DxTextBox v-model:value="form.nickname" @enter-key="onSubmit" />
          </label>

          <button type="button" class="btn btn-primary w-full" :disabled="loading" @click="onSubmit">
            {{ loading ? '처리 중…' : '가입하기' }}
          </button>
        </div>
      </div>

      <p class="mt-4 text-center text-sm text-ink-500">
        이미 계정이 있나요?
        <RouterLink to="/login" class="font-medium text-ink-900 underline underline-offset-2">로그인</RouterLink>
      </p>
    </div>
  </section>
</template>
