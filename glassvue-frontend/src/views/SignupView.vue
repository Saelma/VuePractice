<script setup>
import { computed, reactive, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { signup, login } from '../api/auth';
import { fetchWelcomeCoupon, couponDiscountText } from '../api/coupon';

const router = useRouter();
// 동의(B-21): agreeTerms 는 필수, agreeMarketing 은 선택. 서버도 같은 이름으로 받는다.
const form = reactive({
  loginId: '', password: '', nickname: '', email: '',
  agreeTerms: false, agreeMarketing: false,
});
const error = ref('');
const loading = ref(false);

// 서버(@Email)와 같은 것을 막자는 게 아니라, 왕복 없이 오타를 먼저 걸러 주자는 것.
// 최종 판정은 서버가 한다 — 여기 규칙이 서버보다 엄격하면 정상 주소가 화면에서 막힌다.
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * 「전체 동의」 — 체크박스 하나로 둘을 켜고 끈다.
 *
 * ⚠ **필수와 선택을 함께 켜지만, 필수만으로는 전체가 켜지지 않는다.** 즉 전체 동의는
 * "필수 ∧ 선택"이다. 필수만 켜도 전체가 켜지게 하면 **선택 동의를 한 적 없는 사람에게
 * 동의 표시가 뜬다** — 선택 동의는 안 한 게 기본값이어야 하는 자리라 그 방향으로는 못 켠다.
 */
const agreeAll = computed({
  get: () => form.agreeTerms && form.agreeMarketing,
  set: (v) => {
    form.agreeTerms = v;
    form.agreeMarketing = v;
  },
});

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
  // 필수 동의(B-21). ⚠ **화면이 막는 것은 편의일 뿐 방어가 아니다** — 서버도 같은 것을 막는다
  // (AUTH-400T). 화면만 막으면 API 를 직접 부르는 경로로 동의 없이 가입된다.
  if (!form.agreeTerms) {
    error.value = '이용약관과 개인정보 처리방침에 동의해야 가입할 수 있습니다.';
    return;
  }
  loading.value = true;
  try {
    await signup({
      loginId: form.loginId,
      password: form.password,
      nickname: form.nickname,
      email: form.email.trim(),
      agreeTerms: form.agreeTerms,
      agreeMarketing: form.agreeMarketing,
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
        <p v-if="welcomeCoupon" class="mt-3 rounded-card border border-line bg-canvas px-4 py-3 text-sm text-ink-700">
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

          <!--
            약관 동의 (B-21). 링크는 **새 탭**으로 연다 — 같은 탭이면 입력하던 값이 날아간다.
            체크박스에 label 을 감싸 글자 클릭으로도 토글되게 한다(접근성, DESIGN §8).
          -->
          <div class="rounded-card border border-line bg-canvas p-4">
            <label class="flex cursor-pointer items-center gap-2">
              <input v-model="agreeAll" type="checkbox" class="agree-box" />
              <span class="text-sm font-medium text-ink-900">전체 동의</span>
            </label>

            <div class="mt-3 flex flex-col gap-2 border-t border-line pt-3">
              <label class="flex cursor-pointer items-start gap-2">
                <input v-model="form.agreeTerms" type="checkbox" class="agree-box mt-0.5" />
                <span class="text-sm text-ink-700">
                  <span class="font-medium text-ink-900">[필수]</span>
                  <a href="/terms" target="_blank" rel="noopener"
                     class="underline underline-offset-2 hover:text-ink-900">이용약관</a>
                  및
                  <a href="/privacy" target="_blank" rel="noopener"
                     class="underline underline-offset-2 hover:text-ink-900">개인정보 처리방침</a>
                  에 동의합니다
                </span>
              </label>

              <label class="flex cursor-pointer items-start gap-2">
                <input v-model="form.agreeMarketing" type="checkbox" class="agree-box mt-0.5" />
                <span class="text-sm text-ink-700">
                  <span class="text-ink-500">[선택]</span>
                  혜택·소식 등 마케팅 정보 수신에 동의합니다
                </span>
              </label>
            </div>
          </div>

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

<style scoped>
/*
 * 체크박스는 DevExtreme 대신 네이티브를 쓴다 — 이 화면의 나머지 입력(DxTextBox)과 달리
 * 체크박스는 브라우저 기본이 이미 접근성(키보드·스크린리더)을 갖추고 있고, DX 로 바꾸면
 * 라벨 클릭·포커스 링을 다시 만들어야 한다. 색만 토큰에 맞춘다(DESIGN §6 — DX 공존 방침의 반대편).
 */
.agree-box {
  width: 1rem;
  height: 1rem;
  flex-shrink: 0;
  accent-color: var(--color-brand-600);
  cursor: pointer;
}
</style>
