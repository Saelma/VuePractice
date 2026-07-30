<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { authState } from '../stores/auth';
import { changeNickname, changeEmail, changePassword, withdraw,
         sendEmailVerification, confirmEmailVerification } from '../api/member';
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
  if (pw.next.length < 10) { pw.err = '새 비밀번호는 10자 이상이어야 합니다.'; return; }
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
    // ⚠ 주소가 바뀌면 서버가 인증을 푼다 — 화면의 코드 입력도 함께 접는다(옛 코드 오입력 방지).
    verify.codeOpen = false;
    verify.code = '';
    verify.msg = ''; verify.err = '';
  } catch (e) {
    email.err = e.message;
  } finally {
    email.loading = false;
  }
}

/*
 * 이메일 소유 인증 (B-14, 2026-07-29).
 *
 * ⚠ **인증은 주소에 대한 것**이라, 이메일을 바꾸면 서버가 인증을 자동으로 푼다
 * (Member.updateEmail). 그래서 저장 성공 시 여기서도 코드 입력 상태를 접는다 —
 * 안 그러면 옛 주소로 받은 코드를 새 주소 인증에 넣는 화면이 된다.
 */
const verify = reactive({ codeOpen: false, code: '', msg: '', err: '', loading: false });

async function onSendVerification() {
  verify.msg = ''; verify.err = ''; verify.loading = true;
  try {
    await sendEmailVerification();
    verify.codeOpen = true;
    verify.code = '';
    verify.msg = '인증번호를 보냈습니다. 메일함을 확인해 주세요. (10분 이내)';
  } catch (e) {
    verify.err = e.message;
  } finally {
    verify.loading = false;
  }
}

async function onConfirmVerification() {
  verify.msg = ''; verify.err = '';
  if (!/^\d{6}$/.test(verify.code.trim())) { verify.err = '인증번호 6자리를 입력하세요.'; return; }
  verify.loading = true;
  try {
    await confirmEmailVerification(verify.code.trim());
    verify.codeOpen = false;
    verify.code = '';
    verify.msg = '이메일 인증이 완료되었습니다.';
  } catch (e) {
    // 서버가 사유를 구분해 주지 않는다(만료/횟수초과/불일치) — 그대로 보여준다.
    verify.err = e.message;
  } finally {
    verify.loading = false;
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
        <span class="field-label">
          이메일
          <!-- 인증 상태 배지 — 등록된 주소가 있을 때만 의미가 있다 -->
          <span v-if="authState.user?.email" class="badge ml-2"
                :class="authState.user?.emailVerified ? 'badge-success' : 'badge-warning'">
            {{ authState.user?.emailVerified ? '인증됨' : '미인증' }}
          </span>
        </span>
        <DxTextBox v-model:value="email.value" mode="email" @enter-key="onEmail" />
      </label>
      <button type="button" class="btn btn-secondary self-start" :disabled="email.loading" @click="onEmail">
        {{ email.loading ? '저장 중…' : (authState.user?.email ? '이메일 변경' : '이메일 등록') }}
      </button>

      <!--
        이메일 소유 인증 (B-14). 등록됐고 아직 미인증일 때만 보인다.
        ⚠ 인증은 **주소**에 대한 것이라 주소를 바꾸면 서버가 인증을 푼다 → 이 블록이 다시 나타난다.
      -->
      <div v-if="authState.user?.email && !authState.user?.emailVerified"
           class="mt-1 flex flex-col gap-3 border-t border-line pt-4">
        <p class="text-sm text-ink-700">
          이 주소가 본인 것인지 아직 확인되지 않았습니다.
          <span class="muted">오타가 있으면 비밀번호 재설정 메일이 다른 사람에게 갈 수 있습니다.</span>
        </p>
        <p v-if="verify.err" class="alert-error">{{ verify.err }}</p>
        <p v-if="verify.msg" class="alert-success">{{ verify.msg }}</p>

        <div v-if="verify.codeOpen" class="flex flex-wrap items-end gap-2">
          <label class="field grow-0">
            <span class="field-label">인증번호 6자리</span>
            <DxTextBox v-model:value="verify.code" :max-length="6" placeholder="000000"
                       @enter-key="onConfirmVerification" />
          </label>
          <button type="button" class="btn btn-primary" :disabled="verify.loading" @click="onConfirmVerification">
            {{ verify.loading ? '확인 중…' : '확인' }}
          </button>
          <button type="button" class="btn btn-ghost" :disabled="verify.loading" @click="onSendVerification">
            다시 보내기
          </button>
        </div>
        <button v-else type="button" class="btn btn-secondary self-start"
                :disabled="verify.loading" @click="onSendVerification">
          {{ verify.loading ? '보내는 중…' : '인증메일 보내기' }}
        </button>
      </div>
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
