<script setup>
/**
 * 재입고 알림 신청 버튼 (B-9). 상품 상세에서 **상품 전체가 품절**일 때만 보여준다
 * (부분 품절은 다른 옵션을 살 수 있으니 신청이 필요 없다 — 부모가 v-if 로 가린다).
 *
 * 위시리스트 하트와 같은 규약: 비로그인은 막지 않고 로그인으로 보내(돌아올 곳 포함) 유입 경로로 쓴다.
 * 서버가 신청·취소를 멱등으로 처리하므로 화면은 낙관적으로 토글하고 실패 시 되돌린다.
 */
import { ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { isRestockSubscribed, toggleRestock } from '../stores/restock';
import { isLoggedIn } from '../stores/auth';

const props = defineProps({
  productId: { type: String, required: true },
});
const emit = defineEmits(['changed']);

const router = useRouter();
const route = useRoute();
const busy = ref(false);

async function onClick() {
  if (!isLoggedIn.value) {
    router.push({ path: '/login', query: { redirect: route.fullPath } });
    return;
  }
  if (busy.value) return;
  busy.value = true;
  try {
    emit('changed', await toggleRestock(props.productId));
  } catch (e) {
    window.alert(e.message);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <button
    type="button"
    class="btn w-full gap-1.5"
    :class="isRestockSubscribed(productId) ? 'btn-secondary' : 'btn-primary'"
    :aria-pressed="isRestockSubscribed(productId)"
    :disabled="busy"
    @click="onClick"
  >
    <span>{{ isRestockSubscribed(productId) ? '🔔' : '🔕' }}</span>
    <span>{{ isRestockSubscribed(productId) ? '재입고 알림 신청됨 (취소)' : '재입고되면 알림 받기' }}</span>
  </button>
</template>
