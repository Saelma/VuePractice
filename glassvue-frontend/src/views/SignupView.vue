<script setup>
import { reactive, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { signup, login } from '../api/auth';
import { fetchWelcomeCoupon, couponDiscountText } from '../api/coupon';

const router = useRouter();
const form = reactive({ loginId: '', password: '', nickname: '', email: '' });
const error = ref('');
const loading = ref(false);

// 서버(@Email)와 같은 것을 막자는 게 아니라, 왕복 없이 오타를 먼저 걸러 주자는 것.
// 최종 판정은 서버가 한다 — 여기 규칙이 서버보다 엄격하면 정상 주소가 화면에서 막힌다.
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// 가입 즉시 받는 쿠폰(G-2). 서버가 null 을 주면(기능 꺼짐·쿠폰 삭제) 문구를 아예 안 띄운다 —
// 결정 직전 화면이라 여기서 거짓말을 하면 가장 나쁘다. 실패해도 가입 화면은 그대로 동작한다.
const welcomeCoupon = ref(null);
onMounted(async () => {
  try {
    welcomeCoupon.value = (await fetchWelcomeCoupon()) || null;
  } catch {
    welcomeCoupon.value = null;
  }
});

async function onSubmit() {
  error.value = '';
  if (!form.loginId || !form.password || !form.nickname || !form.email) {
    error.value = '모든 항목을 입력하세요.';
    return;
  }
  if (form.loginId.length < 4) {
    error.value = '아이디는 4자 이상이어야 합니다.';
    return;
  }
  if (form.password.length < 10) {
    // 서버 정책(E-3)과 같은 하한. ⚠ 나머지 규칙(흔한 목록·아이디 포함 금지)은 **서버가 판정한다** —
    // 화면이 목록을 들고 있으면 목록이 갈라지고, 그 목록 자체가 힌트가 된다.
    error.value = '비밀번호는 10자 이상이어야 합니다.';
    return;
  }
  if (!EMAIL_RE.test(form.email.trim())) {
    error.value = '이메일 형식이 올바르지 않습니다.';
    return;
  }
  loading.value = true;
  try {
    await signup({
      loginId: form.loginId,
      password: form.password,
      nickname: form.nickname,
      email: form.email.trim(),
    });
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
        <p v-if="welcomeCoupon" class="mt-3 rounded-card border border-ink-200 bg-canvas px-4 py-3 text-sm text-ink-700">
          가입 즉시 <strong class="text-ink-900">{{ couponDiscountText(welcomeCoupon) }}</strong> 쿠폰이 지급됩니다.
        </p>

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
            <span class="muted">10자 이상 · 아이디·닉네임이나 흔한 비밀번호는 쓸 수 없습니다</span>
          </label>
          <label class="field">
            <span class="field-label">닉네임</span>
            <DxTextBox v-model:value="form.nickname" />
          </label>
          <label class="field">
            <span class="field-label">이메일</span>
            <DxTextBox v-model:value="form.email" mode="email" @enter-key="onSubmit" />
            <span class="muted">비밀번호를 잊었을 때 재설정 링크를 받을 주소입니다.</span>
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
