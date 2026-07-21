<script setup>
/**
 * 주문 품목/장바구니 썸네일.
 *
 * 주문 품목의 이미지 URL은 **주문 시점 스냅샷**이라, 이후 상품이 삭제되면 파일이 정리돼 404가 될 수 있다
 * (이름·가격이라는 본질 기록은 남는다). 그래서 로드 실패를 정상 흐름으로 보고 대체 표시로 넘어간다.
 */
import { ref, watch } from 'vue';

const props = defineProps({
  src: { type: String, default: null },
  alt: { type: String, default: '' },
  size: { type: String, default: 'h-14 w-14' },
});

const failed = ref(false);
watch(() => props.src, () => { failed.value = false; });
</script>

<template>
  <div
    class="shrink-0 overflow-hidden rounded-control border border-line bg-canvas"
    :class="size"
  >
    <img
      v-if="src && !failed"
      :src="src"
      :alt="alt"
      class="h-full w-full object-cover"
      @error="failed = true"
    />
    <div v-else class="flex h-full w-full items-center justify-center text-lg text-ink-400" aria-hidden="true">🖼️</div>
  </div>
</template>
