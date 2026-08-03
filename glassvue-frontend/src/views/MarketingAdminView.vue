<script setup>
/**
 * 관리자 마케팅 발송 (2026-08-03, B-21 후속).
 *
 * **이 화면이 특별한 이유**: 지금까지 알림은 전부 이벤트에서 자동 생성됐다(주문·재고·재입고·문의).
 * 여기가 **사람이 알림을 써서 보내는 첫 화면**이다.
 *
 * ⚠ **되돌릴 수 없는 조작**이다 — 만들어진 알림은 회수할 수 없고 전 회원에게 나간다.
 * 그래서 화면이 지는 책임이 셋이다:
 *   ① 누르기 **전에** 규모를 보여준다(대상 수)
 *   ② 실수로 눌리지 않게 **확인 단계**를 둔다
 *   ③ 보낸 **뒤에** 실제로 몇 명에게 갔는지 나눠서 보여준다
 *
 * ⚠ **"N명에게 발송됩니다" 라고 단정하지 않는다.** 대상 수는 *동의자 수*이고 실제 발송은
 * 그보다 적을 수 있다(수신 거부). 화면이 서버보다 확신하면 거짓말이 되는 자리다
 * (혜택 문구를 서버가 줄 때만 노출하기로 한 것과 같은 규칙, DESIGN §7).
 */
import { computed, onMounted, ref } from 'vue';
import { DxTextBox } from 'devextreme-vue/text-box';
// ⚠ DxTextArea 는 **text-box 가 아니라 text-area** 모듈이다(text-box 는 DxTextBox·DxButton 만 내보낸다).
// 처음에 text-box 에서 가져왔더니 undefined 가 되어 **내용 칸이 통째로 안 그려졌다** —
// 없는 named export 라 빌드도 테스트도 통과했고, 화면을 열어서야 드러났다(2026-08-03).
import { DxTextArea } from 'devextreme-vue/text-area';
import { fetchMarketingAudience, sendMarketing } from '../api/notification';

const form = ref({ title: '', message: '', link: '' });
const audience = ref(null);
const result = ref(null);
const error = ref('');
const loading = ref(false);
const confirming = ref(false);

const canSend = computed(() => form.value.title.trim() && form.value.message.trim());

async function loadAudience() {
  try {
    audience.value = await fetchMarketingAudience();
  } catch (e) {
    // 대상 수를 못 읽어도 발송 자체는 막지 않는다 — 서버가 최종 판단한다.
    audience.value = null;
  }
}
onMounted(loadAudience);

async function onSend() {
  error.value = '';
  result.value = null;
  loading.value = true;
  try {
    result.value = await sendMarketing({
      title: form.value.title.trim(),
      message: form.value.message.trim(),
      link: form.value.link.trim() || null,
    });
    // 보낸 뒤 입력을 비운다 — 같은 내용을 두 번 보내는 사고를 줄인다.
    form.value = { title: '', message: '', link: '' };
    confirming.value = false;
    await loadAudience();
  } catch (e) {
    error.value = e.message;
    confirming.value = false;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="page-narrow">
    <div class="mx-auto max-w-2xl">
      <div class="mb-5">
        <h1 class="page-title">마케팅 발송</h1>
        <p class="muted mt-1">
          마케팅 수신에 <strong>동의한 회원</strong>에게만 인앱 알림을 보냅니다.
        </p>
      </div>

      <!-- ⚠ 되돌릴 수 없다는 사실을 누르기 전에 알린다 -->
      <div class="alert-warning mb-5">
        <strong>보낸 알림은 회수할 수 없습니다.</strong>
        동의하지 않은 회원과 알림 설정에서 마케팅을 끈 회원에게는 가지 않습니다.
      </div>

      <p v-if="error" class="alert-error mb-5">{{ error }}</p>

      <!-- 발송 결과 — 세 숫자를 나눠서 보여준다 -->
      <div v-if="result" class="alert-success mb-5">
        <strong>{{ result.sent }}명에게 발송했습니다.</strong>
        <span v-if="result.optedOut > 0" class="block">
          동의자 {{ result.agreed }}명 중 {{ result.optedOut }}명은 알림 설정에서 마케팅을 꺼 제외됐습니다.
        </span>
        <span v-else-if="result.agreed === 0" class="block">
          아직 마케팅 수신에 동의한 회원이 없습니다.
        </span>
      </div>

      <div class="card p-6">
        <div class="flex flex-col gap-4">
          <label class="field">
            <span class="field-label">제목</span>
            <DxTextBox v-model:value="form.title" :max-length="100" placeholder="예: 여름 감사 쿠폰 안내" />
          </label>

          <label class="field">
            <span class="field-label">내용</span>
            <DxTextArea v-model:value="form.message" :height="120" :max-length="500"
                        placeholder="예: 이번 주말까지 전 상품 무료배송입니다." />
          </label>

          <label class="field">
            <span class="field-label">이동 경로 (선택)</span>
            <DxTextBox v-model:value="form.link" :max-length="200" placeholder="/products" />
            <span class="muted">
              알림을 눌렀을 때 갈 곳입니다. <strong>앱 안 경로</strong>만 넣으세요(예: <code>/products</code>).
            </span>
          </label>

          <div class="border-t border-line pt-4">
            <p class="muted mb-3">
              <!-- ⚠ "최대" 를 뺄 수 없다 — 수신 거부자가 빠지므로 실제 발송은 이보다 적을 수 있다 -->
              <template v-if="audience !== null">
                현재 마케팅 동의 회원 <strong class="text-ink-900">{{ audience }}명</strong> —
                수신을 끈 회원을 빼고 <strong>최대 {{ audience }}명</strong>에게 갑니다.
              </template>
              <template v-else>대상 수를 불러오지 못했습니다. 발송 후 결과에서 확인하세요.</template>
            </p>

            <!-- ② 확인 단계 — 한 번 더 누르게 한다 -->
            <template v-if="!confirming">
              <button type="button" class="btn btn-primary w-full"
                      :disabled="!canSend || loading" @click="confirming = true">
                발송하기
              </button>
            </template>
            <template v-else>
              <p class="mb-2 text-sm font-medium text-ink-900">정말 보낼까요? 되돌릴 수 없습니다.</p>
              <div class="flex gap-2">
                <button type="button" class="btn btn-secondary flex-1"
                        :disabled="loading" @click="confirming = false">취소</button>
                <button type="button" class="btn btn-danger flex-1"
                        :disabled="loading" @click="onSend">
                  {{ loading ? '보내는 중…' : '네, 보냅니다' }}
                </button>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
