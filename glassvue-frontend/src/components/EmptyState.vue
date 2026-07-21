<script setup>
/**
 * 빈 상태 — 회색 한 줄로 끝내지 않고 아이콘 + 설명 + (가능하면) 행동을 준다(DESIGN.md §5).
 *
 * 7개 화면이 같은 마크업을 복사해 쓰고 있어 뽑았다. 문구·아이콘·행동은 상황마다 다르므로
 * 값으로 받고, 행동 버튼은 slot으로 열어 둔다(버튼 종류·개수가 화면마다 다르다).
 *
 * ※ "필터 때문에 비었다"와 "원래 없다"는 다른 상황이므로 문구·아이콘을 호출부가 골라 넘긴다
 *   (2026-07-20 §8-7 교훈 — 기본 필터로 빈 화면을 고장으로 오해했던 사고).
 */
defineProps({
  icon: { type: String, default: '📭' },
  message: { type: String, required: true },
  /** 보조 안내(선택). 행동 버튼이 없을 때 다음에 뭘 할지 알려주는 용도. */
  hint: { type: String, default: null },
  /** 섹션 안(리뷰·문의)에선 조금 좁게 — 'section'이면 py-12, 기본은 py-16 */
  density: { type: String, default: 'page' },
});
</script>

<template>
  <div
    class="flex flex-col items-center gap-3 text-center"
    :class="density === 'section' ? 'py-12' : 'py-16'"
  >
    <span class="text-4xl" aria-hidden="true">{{ icon }}</span>
    <p class="text-sm text-ink-500">{{ message }}</p>
    <p v-if="hint" class="muted">{{ hint }}</p>
    <!-- 행동 버튼(선택) — 종류·개수가 화면마다 달라 slot으로 연다 -->
    <slot />
  </div>
</template>
