<script setup>
/**
 * 배송 조회 **예시 페이지**.
 *
 * 진짜 택배사 사이트가 아니다. 연습 단계라 주문·송장번호가 전부 가짜여서 실제 택배사로 보내봐야
 * "조회 결과 없음"만 나오고, 통제할 수 없는 외부 의존만 생긴다(택배사가 URL을 바꾸면 링크가
 * 조용히 깨지는데 깨진 걸 알 방법이 없다). 그래서 조회 링크를 앱 안의 이 페이지로 받는다.
 *
 * 실제 배송이 필요해지면 백엔드 설정(`glassvue.delivery.tracking-url`)에 택배사별 실제 URL을
 * 넣기만 하면 이 페이지를 거치지 않는다 — 코드 변경 없이 바뀐다.
 *
 * **여기 보이는 배송 단계는 지어낸 값이다.** 실제 배송 데이터가 아니라는 걸 화면에도 명시한다 —
 * 없는 정보를 있는 것처럼 보여주면 안 된다(주문 상세가 값 없는 시각을 감추는 것과 같은 원칙).
 */
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { DELIVERY_CARRIERS } from '../api/order';

const route = useRoute();
const router = useRouter();

const trackingNo = computed(() => route.query.no || '');
const carrierCode = computed(() => route.query.carrier || '');
const carrierName = computed(
  () => DELIVERY_CARRIERS.find((c) => c.value === carrierCode.value)?.text || carrierCode.value || '알 수 없음',
);
</script>

<template>
  <section class="page-narrow">
    <h1 class="page-title">배송 조회</h1>

    <!-- 이 페이지가 진짜가 아니라는 걸 가장 먼저 알린다. -->
    <div class="alert-warning mt-4">
      <strong>예시 페이지입니다.</strong>
      실제 택배사 조회 페이지가 아니라 이 프로젝트가 만든 화면이고,
      아래 배송 단계는 <strong>실제 배송 정보가 아니라 예시</strong>입니다.
    </div>

    <div class="card mt-6 p-5">
      <dl class="space-y-2 text-sm">
        <div class="flex gap-4">
          <dt class="w-20 shrink-0 text-ink-500">택배사</dt>
          <dd class="text-ink-900">{{ carrierName }}</dd>
        </div>
        <div class="flex gap-4">
          <dt class="w-20 shrink-0 text-ink-500">송장번호</dt>
          <dd class="tabular-nums text-ink-900">{{ trackingNo || '(없음)' }}</dd>
        </div>
      </dl>
    </div>

    <div class="card mt-4 p-5">
      <h2 class="section-title">배송 단계 (예시)</h2>
      <ul class="mt-3 space-y-3">
        <li v-for="(s, i) in ['집화 처리', '간선 상차', '간선 하차', '배달 출발', '배달 완료']" :key="s"
            class="flex items-center gap-3 text-sm">
          <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full border border-line bg-canvas text-xs text-ink-400">
            {{ i + 1 }}
          </span>
          <span class="text-ink-500">{{ s }}</span>
          <span class="muted ml-auto">—</span>
        </li>
      </ul>
      <p class="muted mt-4">
        시각이 비어 있는 건 지어낸 값을 넣지 않기 위해서입니다.
      </p>
    </div>

    <div class="mt-6">
      <button type="button" class="btn btn-secondary" @click="router.back()">← 돌아가기</button>
    </div>
  </section>
</template>
