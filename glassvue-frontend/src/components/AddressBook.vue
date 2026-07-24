<script setup>
/**
 * 배송지 주소록 관리 (2026-07-24, 백로그 B-5).
 *
 * 별칭을 붙인 여러 주소를 두고 그중 하나가 기본 배송지가 된다.
 * 검증(`validateAddress`)과 입력 필드(`ShippingAddressFields`)는 주문서와 공유한다 —
 * 화면마다 검증을 따로 두면 한쪽만 통과하고 서버에서 400을 받는다.
 *
 * 기본 배송지가 바뀌면 `authState.user.ship*`가 낡으므로 `loadMe()`로 다시 읽는다.
 * 안 하면 주문서 자동 채움이 **옛 주소**로 채워진다(화면엔 새 주소가 기본이라고 떠 있는데).
 */
import { ref, reactive, onMounted } from 'vue';
import { DxTextBox } from 'devextreme-vue/text-box';
import {
  fetchAddresses, addAddress, updateAddress, setDefaultAddress, deleteAddress, addressSummary,
} from '../api/address';
import { loadMe } from '../api/auth';
import { emptyAddress, validateAddress, trimAddress } from '../api/shipping';
import ShippingAddressFields from './ShippingAddressFields.vue';

const MAX = 10; // 서버 상한과 같은 값. 넘으면 서버가 ADDRESS-409로 막지만 버튼을 먼저 감춘다.

const list = ref([]);
const loading = ref(true);
const error = ref('');
const msg = ref('');

// editing: null이면 폼을 닫은 상태, 'new'면 추가, 그 외에는 수정 중인 주소 id
const editing = ref(null);
const form = reactive({ alias: '', ...emptyAddress(), setDefault: false });
const saving = ref(false);

async function load() {
  try {
    list.value = await fetchAddresses();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
onMounted(load);

function openNew() {
  error.value = ''; msg.value = '';
  Object.assign(form, { alias: '', ...emptyAddress(), setDefault: list.value.length === 0 });
  editing.value = 'new';
}

function openEdit(a) {
  error.value = ''; msg.value = '';
  Object.assign(form, {
    alias: a.alias, recipient: a.recipient, phone: a.phone,
    zipcode: a.zipcode, address1: a.address1, address2: a.address2 || '',
    setDefault: a.isDefault,
  });
  editing.value = a.id;
}

function close() {
  editing.value = null;
}

async function save() {
  error.value = ''; msg.value = '';
  if (!form.alias.trim()) { error.value = '별칭을 입력하세요. (집·회사 등)'; return; }
  if (form.alias.trim().length > 30) { error.value = '별칭은 30자 이하여야 합니다.'; return; }
  const invalid = validateAddress(form);
  if (invalid) { error.value = invalid; return; }

  saving.value = true;
  const payload = { alias: form.alias.trim(), ...trimAddress(form), setDefault: form.setDefault };
  try {
    if (editing.value === 'new') {
      await addAddress(payload);
      msg.value = '배송지를 추가했습니다.';
    } else {
      await updateAddress(editing.value, payload);
      msg.value = '배송지를 수정했습니다.';
    }
    editing.value = null;
    await refresh();
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}

async function makeDefault(a) {
  error.value = ''; msg.value = '';
  try {
    await setDefaultAddress(a.id);
    msg.value = `'${a.alias}'을(를) 기본 배송지로 지정했습니다.`;
    await refresh();
  } catch (e) {
    error.value = e.message;
  }
}

async function remove(a) {
  if (!window.confirm(`'${a.alias}' 배송지를 삭제할까요?`)) return;
  error.value = ''; msg.value = '';
  try {
    await deleteAddress(a.id);
    // 기본 배송지를 지우면 남은 것 중 가장 먼저 등록한 주소가 승계한다(서버 규칙).
    msg.value = '배송지를 삭제했습니다.';
    if (editing.value === a.id) editing.value = null;
    await refresh();
  } catch (e) {
    error.value = e.message;
  }
}

/** 목록과 로그인 사용자 정보를 함께 갱신한다 — 기본 배송지는 양쪽에 영향을 준다. */
async function refresh() {
  list.value = await fetchAddresses();
  await loadMe();
}
</script>

<template>
  <div class="card flex flex-col gap-3 p-5">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h2 class="section-title">배송지 주소록</h2>
      <span class="muted">{{ list.length }} / {{ MAX }}</span>
    </div>
    <p class="text-sm text-ink-700">
      집·회사처럼 여러 배송지를 저장해 두고 주문서에서 골라 쓸 수 있습니다.
      기본 배송지는 주문서에 자동으로 채워집니다. <strong>이미 넣은 주문의 배송지는 바뀌지 않습니다.</strong>
    </p>

    <p v-if="error" class="alert-error">{{ error }}</p>
    <p v-if="msg" class="alert-success">{{ msg }}</p>

    <div v-if="loading" class="space-y-2">
      <div class="skeleton h-16 w-full"></div>
      <div class="skeleton h-16 w-full"></div>
    </div>

    <p v-else-if="!list.length && editing !== 'new'" class="muted py-2">
      저장된 배송지가 없습니다. 아래에서 추가하세요.
    </p>

    <ul v-else-if="list.length" class="flex flex-col gap-2">
      <li
        v-for="a in list"
        :key="a.id"
        class="rounded-lg border p-3"
        :class="a.isDefault ? 'border-brand-600' : 'border-line'"
      >
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div class="flex items-center gap-2">
            <span class="font-medium text-ink-900">{{ a.alias }}</span>
            <span v-if="a.isDefault" class="badge badge-neutral">기본 배송지</span>
          </div>
          <div class="flex flex-wrap gap-2">
            <button
              v-if="!a.isDefault"
              type="button"
              class="btn btn-secondary btn-sm"
              @click="makeDefault(a)"
            >
              기본으로
            </button>
            <button type="button" class="btn btn-secondary btn-sm" @click="openEdit(a)">수정</button>
            <button type="button" class="btn btn-danger btn-sm" @click="remove(a)">삭제</button>
          </div>
        </div>
        <p class="mt-1 text-sm text-ink-700">{{ a.recipient }} · {{ a.phone }}</p>
        <p class="text-sm text-ink-700">{{ addressSummary(a) }}</p>
      </li>
    </ul>

    <!-- 추가/수정 폼 — 한 번에 하나만 연다 -->
    <div v-if="editing" class="mt-2 rounded-lg border border-line p-4">
      <h3 class="mb-3 font-medium text-ink-900">
        {{ editing === 'new' ? '배송지 추가' : '배송지 수정' }}
      </h3>
      <label class="field mb-3 sm:max-w-60">
        <span class="field-label">별칭 <span class="text-ink-400">(집·회사 등)</span></span>
        <DxTextBox v-model:value="form.alias" placeholder="집" />
      </label>
      <ShippingAddressFields :form="form" />
      <label class="mt-3 flex items-center gap-2 text-sm text-ink-700">
        <input
          v-model="form.setDefault"
          type="checkbox"
          class="h-4 w-4 accent-brand-600"
          :disabled="editing !== 'new' && form.setDefault"
        />
        기본 배송지로 지정
      </label>
      <p v-if="!list.length" class="muted mt-1">첫 배송지는 자동으로 기본 배송지가 됩니다.</p>
      <div class="mt-4 flex gap-2">
        <button type="button" class="btn btn-primary" :disabled="saving" @click="save">
          {{ saving ? '저장 중…' : '저장' }}
        </button>
        <button type="button" class="btn btn-secondary" @click="close">취소</button>
      </div>
    </div>

    <button
      v-else-if="list.length < MAX"
      type="button"
      class="btn btn-secondary self-start"
      @click="openNew"
    >
      + 배송지 추가
    </button>
    <p v-else class="muted">배송지는 최대 {{ MAX }}개까지 저장할 수 있습니다.</p>
  </div>
</template>
