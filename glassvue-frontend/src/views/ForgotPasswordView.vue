<script setup>
import { reactive, ref } from 'vue';
import { DxTextBox } from 'devextreme-vue/text-box';
import { requestPasswordReset } from '../api/auth';

const form = reactive({ loginId: '' });
const error = ref('');
const loading = ref(false);
const submitted = ref(false);
// dev 전용: 서버가 토큰을 내려주면 화면에서 바로 링크를 보여준다.
// ⚠ 2026-07-29 부터 dev 는 **메일도 함께 나간다**(로컬 캐처 Mailpit → http://127.0.0.1:8025).
//    이 블록은 캐처를 안 띄웠을 때를 위한 이중 확인 수단이라 남겨 둔다. 운영은 토큰이 비어 안 뜬다.
const devToken = ref('');

async function onSubmit() {
  error.value = '';
  if (!form.loginId) {
    error.value = '아이디를 입력하세요.';
    return;
  }
  loading.value = true;
  try {
    // 열거 공격 방지 — 아이디 존재 여부와 무관하게 항상 성공으로 답한다.
    const res = await requestPasswordReset(form.loginId);
    submitted.value = true;
    devToken.value = res?.token || '';
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
        <h1 class="page-title">비밀번호 재설정</h1>
        <p class="muted mt-1">가입한 아이디를 입력하면 재설정 링크를 보내드립니다.</p>

        <div v-if="error" class="alert-error mt-5">{{ error }}</div>

        <template v-if="!submitted">
          <div class="mt-5 flex flex-col gap-4">
            <label class="field">
              <span class="field-label">아이디</span>
              <DxTextBox v-model:value="form.loginId" @enter-key="onSubmit" />
            </label>

            <button type="button" class="btn btn-primary w-full" :disabled="loading" @click="onSubmit">
              {{ loading ? '처리 중…' : '재설정 링크 받기' }}
            </button>
          </div>
        </template>

        <template v-else>
          <div class="alert-success mt-5">
            해당 아이디가 있다면 재설정 링크를 보냈습니다. 메일함을 확인해 주세요.
          </div>
          <!-- dev 전용: 메일 캐처를 안 띄웠을 때도 확인할 수 있게 링크를 화면에도 노출한다.
               운영에서는 token 이 비어 이 블록이 뜨지 않는다. -->
          <div v-if="devToken" class="mt-4 rounded-md border border-dashed border-ink-300 p-3 text-sm">
            <p class="muted mb-2">개발용 링크(메일과 별개로 확인용):</p>
            <RouterLink
              :to="{ path: '/reset-password', query: { token: devToken } }"
              class="font-medium text-ink-900 underline underline-offset-2 break-all"
            >
              /reset-password?token={{ devToken }}
            </RouterLink>
          </div>
        </template>
      </div>

      <!-- 아이디를 잊은 사람이 이 화면으로 온다 — 여기서 막히면 갈 곳이 없어 옆으로 보내 준다(G-1). -->
      <p class="mt-4 text-center text-sm text-ink-500">
        아이디도 기억나지 않나요?
        <RouterLink to="/find-id" class="font-medium text-ink-900 underline underline-offset-2">아이디 찾기</RouterLink>
      </p>
      <p class="mt-2 text-center text-sm text-ink-500">
        <RouterLink to="/login" class="font-medium text-ink-900 underline underline-offset-2">로그인으로 돌아가기</RouterLink>
      </p>
    </div>
  </section>
</template>
