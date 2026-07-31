<script setup>
import { reactive, ref } from 'vue';
import { DxTextBox } from 'devextreme-vue/text-box';
import { findLoginId } from '../api/auth';

const form = reactive({ email: '' });
const error = ref('');
const loading = ref(false);
const submitted = ref(false);

// ⚠ 재설정 화면(ForgotPasswordView)에 있는 devToken 같은 블록이 **여기엔 없다.**
//    저건 링크지만 아이디는 신원이라, dev 라도 화면에 띄우면 열거 방지가 무의미해진다.
//    dev 확인은 메일 캐처(Mailpit → http://127.0.0.1:8025)로 한다.

async function onSubmit() {
  error.value = '';
  if (!form.email) {
    error.value = '이메일을 입력하세요.';
    return;
  }
  loading.value = true;
  try {
    // 열거 방지 — 가입 여부와 무관하게 항상 성공으로 답한다(응답에 아이디가 없다).
    await findLoginId(form.email);
    submitted.value = true;
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
        <h1 class="page-title">아이디 찾기</h1>
        <p class="muted mt-1">가입할 때 등록한 이메일로 아이디를 보내드립니다.</p>

        <div v-if="error" class="alert-error mt-5">{{ error }}</div>

        <template v-if="!submitted">
          <div class="mt-5 flex flex-col gap-4">
            <label class="field">
              <span class="field-label">이메일</span>
              <DxTextBox v-model:value="form.email" mode="email" @enter-key="onSubmit" />
            </label>

            <button type="button" class="btn btn-primary w-full" :disabled="loading" @click="onSubmit">
              {{ loading ? '처리 중…' : '아이디 받기' }}
            </button>
          </div>
        </template>

        <template v-else>
          <!-- 문구가 "보냈습니다"가 아니라 "가입된 주소라면"인 이유: 화면 문구도 가입 여부를
               알려주는 통로다. 서버가 응답을 똑같이 맞춰 놨는데 여기서 갈리면 소용이 없다. -->
          <div class="alert-success mt-5">
            가입된 주소라면 아이디를 보냈습니다. 메일함을 확인해 주세요.
          </div>
        </template>
      </div>

      <p class="mt-4 text-center text-sm text-ink-500">
        <RouterLink to="/login" class="font-medium text-ink-900 underline underline-offset-2">로그인으로 돌아가기</RouterLink>
      </p>
    </div>
  </section>
</template>
