<script setup>
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { confirmPasswordReset } from '../api/auth';

const route = useRoute();
const router = useRouter();
// 토큰은 쿼리로 들어온다(메일 링크 또는 dev 화면 링크). 비면 잘못된 접근.
const token = String(route.query.token || '');
const form = reactive({ newPassword: '', confirm: '' });
const error = ref('');
const loading = ref(false);
const done = ref(false);

async function onSubmit() {
  error.value = '';
  if (!token) {
    error.value = '재설정 링크가 올바르지 않습니다. 처음부터 다시 시도해 주세요.';
    return;
  }
  if (form.newPassword.length < 10) {
    error.value = '비밀번호는 10자 이상이어야 합니다.';
    return;
  }
  if (form.newPassword !== form.confirm) {
    error.value = '새 비밀번호가 일치하지 않습니다.';
    return;
  }
  loading.value = true;
  try {
    await confirmPasswordReset(token, form.newPassword);
    done.value = true;
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
        <h1 class="page-title">새 비밀번호 설정</h1>

        <div v-if="error" class="alert-error mt-5">{{ error }}</div>

        <template v-if="!done">
          <p class="muted mt-1">새로 사용할 비밀번호를 입력해 주세요.</p>
          <div class="mt-5 flex flex-col gap-4">
            <label class="field">
              <span class="field-label">새 비밀번호</span>
              <DxTextBox v-model:value="form.newPassword" mode="password" />
              <span class="muted">10자 이상 · 아이디·닉네임이나 흔한 비밀번호는 쓸 수 없습니다</span>
            </label>
            <label class="field">
              <span class="field-label">새 비밀번호 확인</span>
              <DxTextBox v-model:value="form.confirm" mode="password" @enter-key="onSubmit" />
            </label>

            <button type="button" class="btn btn-primary w-full" :disabled="loading" @click="onSubmit">
              {{ loading ? '변경 중…' : '비밀번호 변경' }}
            </button>
          </div>
        </template>

        <template v-else>
          <div class="alert-success mt-5">비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.</div>
          <button type="button" class="btn btn-primary mt-5 w-full" @click="router.push('/login')">
            로그인하러 가기
          </button>
        </template>
      </div>
    </div>
  </section>
</template>
