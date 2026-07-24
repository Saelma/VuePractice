<script setup>
/**
 * 찜 하트 — 상품 목록 카드와 상품 상세가 같은 버튼을 쓴다.
 *
 * ⚠ 상품 목록에서는 **카드 전체가 <button>** 이라 이 버튼을 그 안에 넣을 수 없다(button 중첩은
 * 유효하지 않은 HTML이고 클릭 처리도 어긋난다). 카드 위에 절대배치로 얹고, 클릭이 카드로
 * 새어나가지 않게 `@click.stop` 을 건다.
 *
 * 비로그인은 막지 않고 **로그인으로 보낸다** — 찜은 로그인을 유도하기 좋은 자리다.
 * 돌아올 곳(redirect)을 함께 넘겨 로그인 후 보던 화면으로 복귀시킨다.
 */
import { ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { isWishlisted, toggleWishlist } from '../stores/wishlist';
import { isLoggedIn } from '../stores/auth';

const props = defineProps({
  productId: { type: String, required: true },
  // 'sm'은 목록 카드용(작고 동그란 배경), 'md'는 상세용(글자가 붙는다)
  size: { type: String, default: 'sm' },
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
    emit('changed', await toggleWishlist(props.productId));
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
    :aria-pressed="isWishlisted(productId)"
    :aria-label="isWishlisted(productId) ? '찜 해제' : '찜하기'"
    :title="isWishlisted(productId) ? '찜 해제' : '찜하기'"
    :disabled="busy"
    :class="
      size === 'sm'
        ? 'flex h-8 w-8 items-center justify-center rounded-full bg-white/90 text-lg shadow-sm transition-colors hover:bg-white'
        : 'btn btn-secondary gap-1.5'
    "
    @click.stop="onClick"
  >
    <span :class="isWishlisted(productId) ? 'text-danger' : 'text-ink-400'">
      {{ isWishlisted(productId) ? '♥' : '♡' }}
    </span>
    <span v-if="size !== 'sm'">{{ isWishlisted(productId) ? '찜 해제' : '찜하기' }}</span>
  </button>
</template>
