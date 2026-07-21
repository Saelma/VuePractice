<script setup>
/**
 * 배송지 입력 필드 묶음 — 주문서(/checkout)와 마이페이지 기본 배송지가 같은 폼을 쓴다.
 * form은 reactive 객체를 그대로 받아 필드를 채운다(부모가 소유, 여기선 값만 쓴다).
 */
import { DxTextBox } from 'devextreme-vue/text-box';

defineProps({
  form: { type: Object, required: true },
});
</script>

<template>
  <div class="grid gap-3 sm:grid-cols-2">
    <label class="field">
      <span class="field-label">수령인</span>
      <DxTextBox v-model:value="form.recipient" placeholder="홍길동" />
    </label>
    <label class="field">
      <span class="field-label">연락처</span>
      <DxTextBox v-model:value="form.phone" placeholder="010-1234-5678" />
    </label>
    <label class="field sm:col-span-2 sm:max-w-40">
      <span class="field-label">우편번호</span>
      <DxTextBox v-model:value="form.zipcode" placeholder="06134" />
    </label>
    <label class="field sm:col-span-2">
      <span class="field-label">주소</span>
      <DxTextBox v-model:value="form.address1" placeholder="서울시 강남구 테헤란로 1" />
    </label>
    <label class="field sm:col-span-2">
      <span class="field-label">상세 주소 <span class="text-ink-400">(선택)</span></span>
      <DxTextBox v-model:value="form.address2" placeholder="3층 301호" />
    </label>
  </div>
</template>
