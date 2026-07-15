<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxButton } from 'devextreme-vue/button';
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
  <section class="mx-auto max-w-md p-6">
    <h2 class="mb-4 text-xl font-semibold text-slate-800">내 정보</h2>

    <!-- 닉네임 변경 -->
    <div class="mb-6 flex flex-col gap-3 rounded-lg border bg-white p-6">
      <h3 class="font-medium text-slate-700">닉네임 변경</h3>
      <p v-if="nick.err" class="text-sm text-red-600">{{ nick.err }}</p>
      <p v-if="nick.msg" class="text-sm text-green-600">{{ nick.msg }}</p>
      <DxTextBox v-model:value="nick.value" @enter-key="onNickname" />
      <DxButton :text="nick.loading ? '변경 중…' : '닉네임 변경'" type="default" styling-mode="contained" :disabled="nick.loading" @click="onNickname" />
    </div>

    <!-- 비밀번호 변경 -->
    <div class="mb-6 flex flex-col gap-3 rounded-lg border bg-white p-6">
      <h3 class="font-medium text-slate-700">비밀번호 변경</h3>
      <p v-if="pw.err" class="text-sm text-red-600">{{ pw.err }}</p>
      <p v-if="pw.msg" class="text-sm text-green-600">{{ pw.msg }}</p>
      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">현재 비밀번호</span>
        <DxTextBox v-model:value="pw.current" mode="password" />
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">새 비밀번호 (8자 이상)</span>
        <DxTextBox v-model:value="pw.next" mode="password" @enter-key="onPassword" />
      </label>
      <DxButton :text="pw.loading ? '변경 중…' : '비밀번호 변경'" type="default" styling-mode="contained" :disabled="pw.loading" @click="onPassword" />
    </div>

    <!-- 회원 탈퇴 -->
    <div class="flex flex-col gap-3 rounded-lg border border-red-200 bg-white p-6">
      <h3 class="font-medium text-red-600">회원 탈퇴</h3>
      <p class="text-sm text-slate-500">탈퇴하면 계정이 삭제되고 되돌릴 수 없습니다. (작성한 글은 남습니다)</p>
      <DxButton text="회원 탈퇴" type="danger" styling-mode="contained" @click="onWithdraw" />
    </div>
  </section>
</template>
