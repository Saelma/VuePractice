<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { authState } from '../stores/auth';
import { changeNickname, changePassword, withdraw } from '../api/member';

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
