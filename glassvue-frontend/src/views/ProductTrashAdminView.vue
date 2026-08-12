<script setup>
/**
 * 삭제 대기 상품 (관리자) — 2026-08-12, 백로그 F-7.
 *
 * 이 화면이 생기기 전까지 **상품 삭제는 되돌릴 수 없었다.** 지우면 옵션·재고 이력이 FK CASCADE 로
 * 함께 사라지고 이미지도 지워졌는데, 방어는 `confirm` 한 번뿐이었다.
 *
 * ⚠ **이 화면의 요점은 「남은 기간」이다.** 목록만 보여주면 관리자는 *"언제까지 되돌릴 수 있나"* 를
 *    모르고, 그걸 모르면 유예가 있으나 마나다. 그래서 **D-day 를 가장 눈에 띄게** 둔다.
 *
 * ⚠ **날짜 계산을 화면에서 하지 않는다** — 서버가 `purgeAt` 을 준다. 여기서 `deletedAt + 7일` 을
 *    더하면 **유예 설정을 바꿨을 때 화면만 낡는다**(재고 부족 임계값을 서버에서 받는 것과 같은 규칙).
 */
import { ref, onMounted } from 'vue';
import { fetchDeletedProducts, restoreProduct } from '../api/product';
import EmptyState from '../components/EmptyState.vue';
import SkeletonList from '../components/SkeletonList.vue';

const items = ref([]);
const loading = ref(true);
const error = ref('');
const busy = ref('');

async function load() {
  loading.value = true;
  try {
    items.value = await fetchDeletedProducts();
    error.value = '';
  } catch (e) {
    // 실패를 빈 목록으로 위장하지 않는다 — 0건으로 그리면 "되돌릴 것이 없다"로 읽힌다(DESIGN §7).
    error.value = e.message;
    items.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(load);

async function onRestore(row) {
  if (!window.confirm(`「${row.name}」을(를) 되살릴까요?\n\n상품 목록·검색에 다시 나오고, 장바구니에 담겨 있던 줄도 다시 구매할 수 있게 됩니다.`)) return;
  busy.value = row.id;
  error.value = '';
  try {
    await restoreProduct(row.id);
    await load();
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = '';
  }
}

/**
 * 남은 기간. ⚠ **서버가 준 `purgeAt` 으로만** 센다.
 * 하루 미만이면 「오늘 사라짐」이다 — "D-0"은 읽는 사람마다 뜻이 갈린다.
 */
function daysLeft(purgeAt) {
  const ms = new Date(purgeAt).getTime() - Date.now();
  if (ms <= 0) return { text: '곧 사라짐', urgent: true };
  const days = Math.ceil(ms / 86_400_000);
  return { text: `D-${days}`, urgent: days <= 1 };
}

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}
</script>

<template>
  <section class="page">
    <div class="mb-5">
      <h1 class="page-title">삭제 대기 상품</h1>
      <p class="muted mt-1">
        삭제한 상품은 <strong>바로 사라지지 않습니다.</strong> 남은 기간이 지나면
        옵션·재고 이력·이미지와 함께 <strong>영구히 삭제되고, 그다음에는 되돌릴 수 없습니다.</strong>
      </p>
    </div>

    <p v-if="error" class="alert-error mb-3">목록을 불러오지 못했습니다. {{ error }}</p>

    <SkeletonList v-if="loading" :rows="3" trailing />

    <EmptyState
      v-else-if="!items.length"
      icon="🗑️"
      message="삭제 대기 중인 상품이 없습니다."
      hint="상품을 삭제하면 여기에 머물다가 유예 기간이 지난 뒤 사라집니다."
    />

    <ul v-else class="grid gap-3">
      <li v-for="row in items" :key="row.id" class="card flex flex-wrap items-center gap-4 p-4">
        <div class="min-w-0 flex-1">
          <p class="truncate font-medium text-ink-900">{{ row.name }}</p>
          <p class="muted mt-1 text-sm">
            {{ row.categoryName }} · {{ fmt(row.deletedAt) }}
            <template v-if="row.deletedBy"> · {{ row.deletedBy }}</template>
          </p>
        </div>

        <!-- 남은 기간이 이 화면의 요점이라 값을 크게 둔다 -->
        <span
          class="badge shrink-0"
          :class="daysLeft(row.purgeAt).urgent ? 'badge-danger' : 'badge-neutral'"
        >{{ daysLeft(row.purgeAt).text }}</span>

        <button
          type="button"
          class="btn btn-secondary btn-sm shrink-0"
          :disabled="busy === row.id"
          @click="onRestore(row)"
        >{{ busy === row.id ? '복구 중…' : '복구' }}</button>
      </li>
    </ul>
  </section>
</template>
