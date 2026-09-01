<script setup>
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
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
  <section class="page-narrow">
    <div class="mx-auto max-w-sm">
      <div class="card p-6">
        <h1 class="page-title">로그인</h1>
        <p class="muted mt-1">아이디와 비밀번호를 입력해 주세요.</p>

        <div v-if="error" class="alert-error mt-5">{{ error }}</div>

        <div class="mt-5 flex flex-col gap-4">
          <label class="field">
            <span class="field-label">아이디</span>
            <DxTextBox v-model:value="form.loginId" @enter-key="onSubmit" />
          </label>
          <label class="field">
            <span class="field-label">비밀번호</span>
            <DxTextBox v-model:value="form.password" mode="password" @enter-key="onSubmit" />
          </label>

          <button type="button" class="btn btn-primary w-full" :disabled="loading" @click="onSubmit">
            {{ loading ? '로그인 중…' : '로그인' }}
          </button>

          <div class="flex items-center justify-center gap-3 text-sm text-ink-500">
            <RouterLink to="/find-id" class="underline underline-offset-2">아이디 찾기</RouterLink>
            <span aria-hidden="true" class="text-ink-300">|</span>
            <RouterLink to="/forgot-password" class="underline underline-offset-2">비밀번호를 잊으셨나요?</RouterLink>
          </div>
        </div>
      </div>

      <p class="mt-4 text-center text-sm text-ink-500">
        계정이 없나요?
        <!--
          🔴 **쿼리를 물려준다** (2026-09-01, BACKLOG J-3). 여기서 안 물려주면 «찜을 누르다 로그인까지
          왔다가 가입을 고른» 사람만 복귀 경로를 잃는다 — **유입 사슬이 가장 긴 경로가 가장 먼저 끊긴다.**
        -->
        <RouterLink
          :to="{ path: '/signup', query: route.query }"
          class="font-medium text-ink-900 underline underline-offset-2"
        >회원가입</RouterLink>
      </p>
    </div>
  </section>
</template>
