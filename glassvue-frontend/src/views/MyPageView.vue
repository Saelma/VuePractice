<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { authState } from '../stores/auth';
import { changeNickname, changeEmail, changePassword, withdraw } from '../api/member';
import AddressBook from '../components/AddressBook.vue';
import NotificationSettings from '../components/NotificationSettings.vue';

const router = useRouter();

const nick = reactive({ value: authState.user?.nickname || '', msg: '', err: '', loading: false });
const pw = reactive({ current: '', next: '', msg: '', err: '', loading: false });

async function onNickname() {
  nick.msg = ''; nick.err = '';
  if (!nick.value.trim()) { nick.err = '닉네임을 입력하세요.'; return; }
  nick.loading = true;
  try {
    await changeNickname(nick.value.trim());
    nick.msg = '닉네임이 변경되었습니다.';
  } catch (e) {
    nick.err = e.message;
  } finally {
    nick.loading = false;
  }
}

async function onPassword() {
  pw.msg = ''; pw.err = '';
  if (!pw.current || !pw.next) { pw.err = '현재/새 비밀번호를 입력하세요.'; return; }
  if (pw.next.length < 8) { pw.err = '새 비밀번호는 8자 이상이어야 합니다.'; return; }
  pw.loading = true;
  try {
    await changePassword(pw.current, pw.next);
    pw.msg = '비밀번호가 변경되었습니다. (다른 기기는 재로그인 필요)';
    pw.current = ''; pw.next = '';
  } catch (e) {
    pw.err = e.message;
  } finally {
    pw.loading = false;
  }
}

// 이메일(B-13) — 기존 회원은 값이 없어(null) 이 화면이 유일한 수집 경로다.
const email = reactive({
  value: authState.user?.email || '',
  msg: '', err: '', loading: false,
});
// 서버(@Email)가 최종 판정한다. 여기 규칙이 더 엄격하면 정상 주소가 화면에서 막히므로 느슨하게.
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

async function onEmail() {
  email.msg = ''; email.err = '';
  const v = email.value.trim();
  if (!v) { email.err = '이메일을 입력하세요.'; return; }
  if (!EMAIL_RE.test(v)) { email.err = '이메일 형식이 올바르지 않습니다.'; return; }
  email.loading = true;
  try {
    const me = await changeEmail(v);
    // 서버가 소문자로 정규화하므로 입력값이 아니라 응답값을 되비춘다(예: A@B.com → a@b.com).
    email.value = me.email || '';
    email.msg = '이메일이 저장되었습니다.';
  } catch (e) {
    email.err = e.message;
  } finally {
    email.loading = false;
  }
}

async function onWithdraw() {
  if (!window.confirm('정말 탈퇴할까요? 되돌릴 수 없습니다.')) return;
  try {
    await withdraw();
    router.push('/');
  } catch (e) {
    window.alert(e.message);
  }
}
</script>

<template>
  <section class="page-narrow">
    <h1 class="page-title mb-5">내 정보</h1>

    <!-- 닉네임 변경 -->
    <div class="card flex flex-col gap-3 p-5">
      <h2 class="section-title">닉네임 변경</h2>
      <p v-if="nick.err" class="alert-error">{{ nick.err }}</p>
      <p v-if="nick.msg" class="alert-success">{{ nick.msg }}</p>
      <label class="field">
        <span class="field-label">닉네임</span>
        <DxTextBox v-model:value="nick.value" @enter-key="onNickname" />
      </label>
      <button type="button" class="btn btn-secondary self-start" :disabled="nick.loading" @click="onNickname">
        {{ nick.loading ? '변경 중…' : '닉네임 변경' }}
      </button>
    </div>

    <!-- 이메일 (B-13, 2026-07-29) — 기존 회원은 값이 없어 여기가 유일한 수집 경로다. -->
    <div class="card mt-8 flex flex-col gap-3 p-5">
      <h2 class="section-title">이메일</h2>
      <p v-if="!authState.user?.email" class="text-sm text-ink-700">
        아직 등록된 이메일이 없습니다. 등록해 두면 비밀번호를 잊었을 때 재설정 링크를 받을 수 있습니다.
      </p>
      <p v-if="email.err" class="alert-error">{{ email.err }}</p>
      <p v-if="email.msg" class="alert-success">{{ email.msg }}</p>
      <label class="field">
        <span class="field-label">이메일</span>
        <DxTextBox v-model:value="email.value" mode="email" @enter-key="onEmail" />
      </label>
      <button type="button" class="btn btn-secondary self-start" :disabled="email.loading" @click="onEmail">
        {{ email.loading ? '저장 중…' : (authState.user?.email ? '이메일 변경' : '이메일 등록') }}
      </button>
    </div>

    <!-- 적립금·등급·쿠폰은 「혜택」 페이지(/benefits)로 옮겼다 — 혜택과 계정설정은 성격이 달라 분리(2026-07-28). -->

    <!-- 배송지 주소록 (2026-07-24) — 기본 배송지 하나만 저장하던 자리를 대체한다 -->
    <div class="mt-8">
      <AddressBook />
    </div>

    <!-- 알림 설정 (2026-07-24) -->
    <div class="mt-8">
      <NotificationSettings />
    </div>

    <!-- 비밀번호 변경 -->
    <div class="card mt-8 flex flex-col gap-3 p-5">
      <h2 class="section-title">비밀번호 변경</h2>
      <p v-if="pw.err" class="alert-error">{{ pw.err }}</p>
      <p v-if="pw.msg" class="alert-success">{{ pw.msg }}</p>
      <label class="field">
        <span class="field-label">현재 비밀번호</span>
        <DxTextBox v-model:value="pw.current" mode="password" />
      </label>
      <label class="field">
        <span class="field-label">새 비밀번호 (8자 이상)</span>
        <DxTextBox v-model:value="pw.next" mode="password" @enter-key="onPassword" />
      </label>
      <button type="button" class="btn btn-secondary self-start" :disabled="pw.loading" @click="onPassword">
        {{ pw.loading ? '변경 중…' : '비밀번호 변경' }}
      </button>
    </div>

    <!-- 회원 탈퇴 — 되돌릴 수 없는 행동이라 맨 아래에 따로 둔다 -->
    <div class="card mt-8 flex flex-col gap-3 p-5">
      <h2 class="section-title">회원 탈퇴</h2>
      <p class="text-sm text-ink-700">탈퇴하면 계정이 삭제되고 되돌릴 수 없습니다. (작성한 글은 남습니다)</p>
      <button type="button" class="btn btn-danger self-start" @click="onWithdraw">회원 탈퇴</button>
    </div>
  </section>
</template>
