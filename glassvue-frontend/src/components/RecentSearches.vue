<script setup>
/**
 * 최근 검색어 목록 (2026-09-01, BACKLOG G-7).
 *
 * 🔴 **표시 규칙을 한 곳에 둔다.** 검색 입구가 둘이라(헤더 폼 · 목록 필터) 이 목록도 두 자리에
 * 서는데, 각자 그리면 «한쪽에만 ✕ 가 있다» 같은 어긋남이 생긴다 — `OrderItemPartialNote` 를
 * 컴포넌트로 뽑은 것과 **같은 판단**이다(2026-08-27, §I-7).
 *
 * ⚠ **감싸는 것은 부르는 쪽 몫이다** — 헤더에선 떠 있는 패널 안에, 목록 필터에선 그냥 인라인으로
 * 선다. 그래서 여기엔 위치·그림자·테두리를 두지 않는다(그걸 넣으면 한쪽에서 반드시 어색해진다).
 *
 * ⚠ **비었을 때는 아무것도 안 그린다** — 부르는 쪽이 «열지 말지» 를 정할 수 있게
 * (헤더 드롭다운은 빈 목록이면 아예 안 뜨는 게 맞고, 필터 안에선 자리만 비면 된다).
 */
import { recentSearches, removeRecentSearch, clearRecentSearches } from '../stores/recentSearches';

defineEmits(['pick']);
</script>

<template>
  <div v-if="recentSearches.length" class="text-sm">
    <div class="mb-1 flex items-center justify-between px-1">
      <span class="text-xs font-medium text-ink-500">최근 검색어</span>
      <button
        type="button"
        class="text-xs text-ink-500 underline-offset-2 hover:text-ink-900 hover:underline"
        @click="clearRecentSearches()"
      >
        전체 지우기
      </button>
    </div>

    <ul>
      <li v-for="term in recentSearches" :key="term" class="group flex items-center">
        <!-- 말 자체가 버튼이다 — 누르면 그 말로 다시 검색한다(부르는 쪽이 pick 을 처리). -->
        <button
          type="button"
          class="flex-1 truncate rounded-control px-2 py-1.5 text-left text-ink-700 transition-colors hover:bg-canvas hover:text-ink-900"
          @click="$emit('pick', term)"
        >
          {{ term }}
        </button>
        <!-- ⚠ 한 줄만 지우는 길 — 오타로 남은 말 때문에 목록 전체를 버리게 하지 않는다. -->
        <button
          type="button"
          class="rounded-control px-2 py-1.5 text-ink-400 transition-colors hover:bg-canvas hover:text-ink-700"
          :aria-label="`최근 검색어에서 ${term} 지우기`"
          @click="removeRecentSearch(term)"
        >
          ✕
        </button>
      </li>
    </ul>
  </div>
</template>
