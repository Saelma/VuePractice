<script setup>
/**
 * 연관 상품 (2026-08-03, 백로그 B-23).
 *
 * **왜**: 상품 상세를 보고 나면 갈 길이 **뒤로 가기뿐**이었다(실측: 상세에 연관/추천 영역 0개).
 *
 * ⚠ **새 API 를 만들지 않는다** — 기존 상품 목록 API 를 `categoryId` + 인기순으로 부르면 된다.
 * 홈이 이미 그렇게 쓴다(`fetchProducts({ sort: 'soldCount,desc' })`). 추천 로직이 아니라
 * **같은 분류에서 잘 팔리는 것**이라 서버가 새로 해 줄 일이 없다.
 *
 * ⚠ **"함께 구매한 상품" 은 하지 않는다** — 주문이 수십 건이라 **추천이 성립하지 않는다**
 * (선착순 쿠폰을 미룬 것과 같은 판단: 볼 게 없으면 만들어도 값이 없다).
 *
 * ⚠ **실패하면 섹션 자체를 감춘다.** 곁다리 영역이라 여기서 에러를 띄우면 상품 상세 전체가
 * 고장 난 것처럼 보인다(대시보드가 부분 실패를 삼키는 것과 같은 판단).
 */
import { computed, ref, watch } from 'vue';
import { fetchProducts } from '../api/product';
import ProductCard from './ProductCard.vue';

const props = defineProps({
  /** 현재 보고 있는 상품 — 목록에서 자기 자신을 빼야 한다. */
  productId: { type: String, required: true },
  categoryId: { type: String, default: null },
});

/** 보여줄 개수. 자기 자신이 섞여 올 수 있어 **한 칸 더** 받아서 걸러낸다. */
const SHOW = 4;

const items = ref([]);
const loading = ref(true);

const visible = computed(() => items.value.length > 0);

/**
 * ⚠ `watch(immediate)` 로 두는 이유: 이 컴포넌트는 **상품을 바꿔 가며 계속 머문다**
 * (연관 상품을 누르면 같은 화면이 다른 id 로 다시 그려진다). `onMounted` 만 쓰면
 * 두 번째 상품부터 **이전 상품의 연관 목록이 그대로 남는다.**
 */
watch(
  () => [props.productId, props.categoryId],
  async () => {
    loading.value = true;
    items.value = [];
    if (!props.categoryId) {
      loading.value = false;
      return; // 분류가 없으면 "같은 분류" 자체가 성립하지 않는다
    }
    try {
      const res = await fetchProducts({
        categoryId: props.categoryId,
        sort: 'soldCount,desc',
        size: SHOW + 1, // 현재 상품이 포함될 수 있으니 한 칸 더
      });
      items.value = (res.content || [])
        .filter((p) => p.id !== props.productId)
        .slice(0, SHOW);
    } catch {
      items.value = []; // 곁다리라 조용히 감춘다
    } finally {
      loading.value = false;
    }
  },
  { immediate: true },
);
</script>

<template>
  <section v-if="loading || visible" class="mt-10">
    <h2 class="section-title mb-3">같은 분류의 인기 상품</h2>

    <div v-if="loading" class="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <div v-for="n in 4" :key="n" class="card p-3">
        <div class="skeleton aspect-square w-full"></div>
        <div class="skeleton mt-3 h-4 w-3/4"></div>
        <div class="skeleton mt-2 h-4 w-1/2"></div>
      </div>
    </div>

    <!--
      ⚠ **카드를 직접 그리지 않는다** — 상품 목록·홈과 같은 `ProductCard` 를 쓴다.
      높이 고정(h-full·mt-auto·배지 오버레이)·할인 표시·품절 배지 규칙이 거기 다 들어 있어,
      직접 그리면 **같은 상품이 화면마다 다르게 보인다**(DESIGN §5 카드 규칙).
    -->
    <div v-else class="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <ProductCard v-for="p in items" :key="p.id" :product="p" />
    </div>
  </section>
</template>
