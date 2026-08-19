<script setup>
/**
 * 상품 기간 할인(타임세일) 관리 — 2026-08-19, 백로그 G-5.
 *
 * 그전까지 할인은 관리자가 **정가를 손으로 넣고 손으로 되돌리는** 방식이라
 * *"이번 주말만 20%"* 를 하려면 **주말에 사람이 앉아 있어야 했다.** 이 화면이 그 일을 없앤다.
 *
 * ⚠ **상품 폼과 화면을 나눈 이유가 둘이다.**
 *   ① 상품 **등록** 시점에는 상품 id 가 없어 할인을 붙일 대상이 없다(폼은 등록·수정 겸용이다).
 *   ② 할인은 상품 저장과 **다른 트랜잭션**이다 — 한 폼에 섞으면 「저장을 안 눌렀는데 세일이
 *      이미 걸린」 상태가 생기고, 관리자는 그걸 되돌릴 방법을 폼에서 못 찾는다.
 *
 * 🔴 **프리셋 버튼(「오늘 하루」·「이번 주말」)을 일부러 안 뒀다.** DESIGN §7 은 관리자 기간 선택에
 *    프리셋을 먼저 두라고 하지만, 그 규약의 **핵심은 「오늘」의 기준이 브라우저 시계가 아니라는 것**이다
 *    (매출 화면은 서버가 준 `to` 를 오늘로 삼는다). 이 API 는 오늘을 안 주므로 프리셋을 만들면
 *    **`new Date()` 가 기준이 되어 자정 근처에서 갈린다.** 게다가 매출 기간과 달리 세일 기간은
 *    매번 다르다 — 「지난 달」 같은 되풀이되는 답이 없어 프리셋의 논거 자체가 약하다.
 *    ⚠ 필요해지면 목록 응답에 `today`(KST)를 실어 그때 붙인다.
 *
 * 🔴 **폼이 만들 것을 문장으로 되읽어 준다**(아래 `previewText`). G-8 에서 발급 마감일을 선택 칸으로만
 *    둬서 **상시 쿠폰이 조용히 만들어진** 사고가 있었다 — 값이 여러 칸에 흩어져 있으면 관리자는
 *    「내가 무엇을 만들고 있는지」를 못 읽는다. 여기서는 **금액까지** 되읽는다(그게 결과다).
 */
import { ref, computed, onMounted } from 'vue';
import {
  getProduct, priceText,
  fetchProductDiscounts, createProductDiscount, updateProductDiscount, deleteProductDiscount,
  discountStatusText,
} from '../api/product';
import EmptyState from '../components/EmptyState.vue';
import SkeletonList from '../components/SkeletonList.vue';

const props = defineProps({ id: { type: String, required: true } });

const product = ref(null);
const items = ref([]);
const loading = ref(true);
const error = ref('');
const formError = ref('');
const busy = ref('');
const saving = ref(false);

/** 수정 중인 할인 id. null 이면 「새로 등록」이다. */
const editingId = ref(null);
const form = ref({ rate: null, startDate: '', endDate: '' });

async function load() {
  loading.value = true;
  try {
    // 상품과 할인을 함께 읽는다 — 화면 위쪽이 「지금 얼마에 팔리나」를 말해야 하는데
    // 그 값(price·regularPrice)은 상품 응답에만 있다.
    const [p, list] = await Promise.all([getProduct(props.id), fetchProductDiscounts(props.id)]);
    product.value = p;
    items.value = list;
    error.value = '';
  } catch (e) {
    // 실패를 빈 목록으로 위장하지 않는다 — 0건으로 그리면 「세일이 없다」로 읽힌다(DESIGN §7).
    error.value = e.message;
    items.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(load);

/** 세일 전 판매가 — 되읽기 문장이 쓸 기준값이다. ⚠ `price` 가 아니다(그건 이미 할인된 값일 수 있다). */
const basePrice = computed(() => product.value?.regularPrice ?? 0);

/**
 * 「8월 22일부터 8월 24일까지 20% — 10,000원이 8,000원이 됩니다」
 *
 * ⚠ 반올림은 **서버와 같은 식**이어야 한다: `(x * (100 - rate) + 50) / 100`.
 *    소수 연산(`Math.round(x * (100 - r) / 100)`)으로 쓰면 서버(Oracle NUMBER)와
 *    **어느 값에서 갈리는지 알 수 없다.** 미리보기가 1원 틀리면 관리자는 저장 뒤에야 안다.
 */
const previewText = computed(() => {
  const { rate, startDate, endDate } = form.value;
  if (!rate || !startDate || !endDate) return '';
  const sale = Math.floor((basePrice.value * (100 - rate) + 50) / 100);
  return `${dateText(startDate)}부터 ${dateText(endDate)}까지 ${rate}% — `
    + `${priceText(basePrice.value)}이 ${priceText(sale)}이 됩니다.`;
});

/** 「끝이 시작보다 앞」은 저장 전에 화면이 먼저 말한다 — 서버도 400 으로 막지만 왕복할 이유가 없다. */
const periodInvalid = computed(() => {
  const { startDate, endDate } = form.value;
  return !!startDate && !!endDate && endDate < startDate;
});

function dateText(iso) {
  if (!iso) return '';
  const [, m, d] = iso.split('-');
  return `${Number(m)}월 ${Number(d)}일`;
}

function resetForm() {
  editingId.value = null;
  form.value = { rate: null, startDate: '', endDate: '' };
  formError.value = '';
}

function startEdit(row) {
  // ⚠ **`startDate`·`endDate` 를 그대로 쓴다**(`startsAt`·`endsAt` 이 아니라).
  //    뒤엣것은 배타 경계라 **종료일이 하루 뒤로 보이고**, 그대로 저장하면 세일이 하루씩 길어진다.
  editingId.value = row.id;
  form.value = { rate: row.rate, startDate: row.startDate, endDate: row.endDate };
  formError.value = '';
}

async function onSubmit() {
  if (!form.value.rate || !form.value.startDate || !form.value.endDate) {
    formError.value = '할인율과 기간을 모두 입력해 주세요.';
    return;
  }
  if (periodInvalid.value) {
    formError.value = '종료일은 시작일보다 뒤여야 합니다.';
    return;
  }
  saving.value = true;
  formError.value = '';
  try {
    const payload = {
      rate: Number(form.value.rate),
      startDate: form.value.startDate,
      endDate: form.value.endDate,
    };
    if (editingId.value) {
      await updateProductDiscount(props.id, editingId.value, payload);
    } else {
      await createProductDiscount(props.id, payload);
    }
    resetForm();
    await load();
  } catch (e) {
    // 겹침 거절(PRODUCT-400DO)이 여기로 온다. 서버 문구를 그대로 쓴다 —
    // 화면이 문구를 다시 쓰면 규칙이 바뀌었을 때 화면만 낡는다.
    formError.value = e.message;
  } finally {
    saving.value = false;
  }
}

async function onDelete(row) {
  const warning = row.status === 'ACTIVE'
    ? '\n\n⚠ 지금 진행 중인 세일입니다 — 지우면 그 즉시 원래 가격으로 돌아갑니다.'
    : '';
  if (!window.confirm(`${row.rate}% 할인(${row.startDate} ~ ${row.endDate})을 지울까요?${warning}`)) return;
  busy.value = row.id;
  error.value = '';
  try {
    await deleteProductDiscount(props.id, row.id);
    if (editingId.value === row.id) resetForm();
    await load();
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = '';
  }
}

/**
 * ⚠ 있는 배지만 쓴다(`badge-neutral`·`badge-danger`). 「예정」에 `badge-warning`(amber)을 쓰면
 *    경고처럼 읽히는데 예정은 경고가 아니고, 무엇보다 **강조색은 CTA·상태에만**이 DESIGN 의 원칙이다.
 *    🔴 지금 돈이 달라지고 있는 것은 **진행 중** 하나뿐이라 거기만 강조한다.
 */
function badgeClass(status) {
  return status === 'ACTIVE' ? 'badge-danger' : 'badge-neutral';
}
</script>

<template>
  <section class="page">
    <div class="mb-5">
      <h1 class="page-title">기간 할인</h1>
      <p class="muted mt-1">
        기간을 정해 두면 <strong>시작·종료가 저절로 일어납니다.</strong>
        정가(할인 전 표시가)와는 다른 것으로, 세일이 끝나면 원래 판매가로 돌아갑니다.
      </p>
    </div>

    <p v-if="error" class="alert-error mb-3">불러오지 못했습니다. {{ error }}</p>

    <SkeletonList v-if="loading" :rows="3" trailing />

    <template v-else>
      <!--
        지금 얼마에 팔리는지를 맨 위에 둔다. 이 화면에서 관리자가 가장 먼저 확인할 것이 그것이고,
        아래 폼의 되읽기 문장도 이 값을 기준으로 계산한다.
      -->
      <div v-if="product" class="card mb-5 p-4">
        <p class="truncate font-medium text-ink-900">{{ product.name }}</p>
        <p class="mt-1 tabular-nums">
          <span class="muted">세일 전 판매가</span>
          <b class="ml-2 text-ink-900">{{ priceText(product.regularPrice) }}</b>
          <template v-if="product.discountRate != null">
            <span class="muted mx-2">→ 지금</span>
            <b class="text-danger">{{ priceText(product.price) }}</b>
            <span class="badge badge-danger ml-2">{{ product.discountRate }}% 세일 중</span>
          </template>
        </p>
      </div>

      <!-- 등록 · 수정 폼 -->
      <form class="card mb-6 grid gap-4 p-4" @submit.prevent="onSubmit">
        <p class="field-label">{{ editingId ? '할인 수정' : '할인 등록' }}</p>

        <div class="flex flex-wrap gap-4">
          <label class="field">
            <span class="field-label">할인율(%)</span>
            <input v-model="form.rate" type="number" min="1" max="99" class="ipt w-28" placeholder="20" />
          </label>
          <!--
            🔴 **네이티브 `<input type="date">` 다**(DESIGN §7). DevExtreme 컨트롤을 넣으면
               그 테마까지 따라 들어오고, 브라우저 기본이 라벨·포커스·스크린리더·모바일 달력을
               이미 갖췄다. 그리고 **화면은 날짜만 보내고 경계는 서버가 만든다.**
          -->
          <label class="field">
            <span class="field-label">시작일</span>
            <input v-model="form.startDate" type="date" class="ipt" />
          </label>
          <label class="field">
            <span class="field-label">종료일</span>
            <input v-model="form.endDate" type="date" class="ipt" />
            <span class="muted">이 날이 <b>끝날 때까지</b> 세일입니다</span>
          </label>
        </div>

        <!-- 만들 것을 문장으로 되읽어 준다 (G-8 의 「조용히 상시 쿠폰」 사고 이후의 규칙) -->
        <p v-if="previewText" class="muted">{{ previewText }}</p>
        <p v-if="periodInvalid" class="alert-error">종료일이 시작일보다 앞입니다.</p>
        <p v-if="formError" class="alert-error">{{ formError }}</p>

        <div class="flex gap-2">
          <button type="submit" class="btn btn-primary btn-sm" :disabled="saving || periodInvalid">
            {{ saving ? '저장 중…' : (editingId ? '수정' : '등록') }}
          </button>
          <button v-if="editingId" type="button" class="btn btn-secondary btn-sm" @click="resetForm">
            취소
          </button>
        </div>
      </form>

      <EmptyState
        v-if="!items.length"
        icon="🏷️"
        message="걸린 할인이 없습니다."
        hint="기간을 정해 등록하면 시작·종료가 저절로 일어납니다."
      />

      <!--
        ⚠ 지난 것도 함께 보여준다 — 「지금 세일 중인가」만 보이면 관리자는 **다음 세일을 언제
           걸어야 겹치지 않는지**를 알 수 없고, 겹침 거절(400)을 만난 뒤에야 찾게 된다.
      -->
      <ul v-else class="grid gap-3">
        <li
          v-for="row in items"
          :key="row.id"
          class="card flex flex-wrap items-center gap-4 p-4"
          :class="[editingId === row.id ? 'ring-1 ring-ink-900' : '', row.status === 'ENDED' ? 'opacity-60' : '']"
        >
          <span class="badge shrink-0" :class="badgeClass(row.status)">
            {{ discountStatusText(row.status) }}
          </span>
          <div class="min-w-0 flex-1">
            <p class="font-medium tabular-nums text-ink-900">{{ row.rate }}% 할인</p>
            <p class="muted mt-1 text-sm tabular-nums">{{ row.startDate }} ~ {{ row.endDate }}</p>
          </div>
          <button
            type="button"
            class="btn btn-secondary btn-sm shrink-0"
            @click="startEdit(row)"
          >수정</button>
          <button
            type="button"
            class="btn btn-danger btn-sm shrink-0"
            :disabled="busy === row.id"
            @click="onDelete(row)"
          >{{ busy === row.id ? '삭제 중…' : '삭제' }}</button>
        </li>
      </ul>
    </template>
  </section>
</template>
