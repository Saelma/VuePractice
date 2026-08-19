<script setup>
import { reactive, ref, computed, onMounted } from 'vue';
import { useRouter, RouterLink } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxTextArea } from 'devextreme-vue/text-area';
import { DxNumberBox } from 'devextreme-vue/number-box';
import { DxSelectBox } from 'devextreme-vue/select-box';
import { getProduct, createProduct, updateProduct, priceText, STATUS_OPTIONS } from '../api/product';
import { fetchCategories } from '../api/category';
import ImageUploader from '../components/ImageUploader.vue';
import StockHistoryPanel from '../components/StockHistoryPanel.vue';

const props = defineProps({ id: { type: String, default: null } });
const router = useRouter();
const isEdit = computed(() => !!props.id);

const categories = ref([]);
// 옵션(variant): 최소 1개. 단일 옵션 상품은 이름 "기본" 한 줄이면 된다(2026-07-24 C-8).
const form = reactive({ name: '', tagline: '', description: '', price: null, listPrice: null, status: 'SELLING', categoryId: null, images: [], variants: [] });
/** 지금 걸린 세일(있으면). 판매가 칸 아래에 「지금 20% 세일 중 → 8,000원」을 띄우는 데만 쓴다. */
const activeSale = ref(null);

function newVariant() { return { name: '', priceDelta: 0, stock: null }; }
function addVariant() { form.variants.push(newVariant()); }
function removeVariant(i) { form.variants.splice(i, 1); }
const error = ref('');
const saving = ref(false);

onMounted(async () => {
  try {
    categories.value = await fetchCategories();
  } catch (e) {
    /* ignore */
  }
  if (!isEdit.value) {
    form.variants = [{ name: '기본', priceDelta: 0, stock: null }];
  }
  if (isEdit.value) {
    try {
      const p = await getProduct(props.id);
      // 🔴 **`p.price` 가 아니라 `p.regularPrice` 다**(2026-08-19, G-5).
      //    `price` 에는 기간 할인이 반영돼 있어서, 세일 중에 이 폼을 열어 저장하면
      //    **세일가가 원래 판매가로 굳는다.** 그리고 세일이 아직 살아 있으면 그 위에
      //    할인이 또 먹어 **두 번 깎인다.** 화면은 멀쩡히 돌고 값만 틀린다.
      //    ⚠ 세일이 없을 때는 두 값이 같으므로 이 줄은 그때 아무것도 바꾸지 않는다.
      Object.assign(form, {
        name: p.name, tagline: p.tagline || '', description: p.description,
        price: p.regularPrice, listPrice: p.listPrice,
        status: p.status, categoryId: p.categoryId,
      });
      // 세일 중이면 관리자에게 그 사실을 말해 준다 — 안 그러면 「내가 적은 값과 고객이 보는 값이
      // 다른」 상황을 폼 어디에서도 알 수 없다(G-8 에서 폼이 만들 것을 되읽어 준 것과 같은 이유).
      activeSale.value = p.discountRate != null
        ? { rate: p.discountRate, price: p.price }
        : null;
      form.images = p.images || [];
      form.variants = (p.variants || []).map((v) => ({ name: v.name, priceDelta: v.priceDelta, stock: v.stock }));
      if (!form.variants.length) form.variants = [newVariant()];
    } catch (e) {
      error.value = e.message;
    }
  }
});

async function onSave() {
  error.value = '';
  if (!form.name.trim() || !form.description.trim()) { error.value = '상품명·설명은 필수입니다.'; return; }
  if (form.price == null) { error.value = '가격을 입력하세요.'; return; }
  if (!form.variants.length) { error.value = '옵션을 최소 1개 추가하세요.'; return; }
  for (const v of form.variants) {
    if (!v.name.trim()) { error.value = '옵션 이름을 입력하세요.'; return; }
    if (v.stock == null) { error.value = `'${v.name || '옵션'}'의 재고를 입력하세요.`; return; }
  }
  if (!form.categoryId) { error.value = '카테고리를 선택하세요.'; return; }
  // ⚠ **0 은 「없음」으로 읽는다** (2026-08-13). 정가 0원은 어떤 판매가에도 유효할 수 없고,
  //    DevExtreme 이 빈 칸을 min(0) 으로 되돌리는 경로가 있어 **사용자가 원한 「비움」이 0으로 온다.**
  //    여기서 되돌리지 않으면 아래 검증에 걸려 **저장할 수도, 비울 수도 없는 상태**가 된다.
  if (form.listPrice === 0) {
    form.listPrice = null;
  }
  // 정가가 판매가보다 작거나 같으면 할인이 아니다 — 화면에 취소선이 이상하게 뜨는 걸 미리 막는다.
  // ⚠ 서버도 같은 것을 막는다(PRODUCT-400L, 2026-08-13). 여기서 먼저 보는 건 왕복을 아끼려는 것뿐이다.
  if (form.listPrice != null && form.listPrice <= form.price) {
    error.value = '정가는 판매가보다 커야 합니다. 할인이 없으면 정가를 비워 두세요.';
    return;
  }
  saving.value = true;
  try {
    const payload = {
      name: form.name,
      // 빈 문자열이 아니라 null 로 보낸다 — 빈 문자열을 저장하면 카드의 v-if 가 통과해
      // 아무것도 없는 줄이 생긴다(높이만 차지). "없음"은 null 하나로 표현한다.
      tagline: form.tagline.trim() || null,
      description: form.description, price: form.price, listPrice: form.listPrice,
      status: form.status, categoryId: form.categoryId,
      imageIds: form.images.map((i) => i.id),
      variants: form.variants.map((v) => ({ name: v.name.trim(), priceDelta: v.priceDelta || 0, stock: v.stock })),
    };
    if (isEdit.value) {
      await updateProduct(props.id, payload);
      router.push(`/products/${props.id}`);
    } else {
      const id = await createProduct(payload);
      router.push(`/products/${id}`);
    }
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section class="page-narrow">
    <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
      <h1 class="page-title">{{ isEdit ? '상품 수정' : '상품 등록' }}</h1>
      <!--
        ⚠ **등록 화면에는 안 띄운다** — 상품 id 가 아직 없어 할인을 붙일 대상이 없다.
           v-if 를 빼면 저장 전에 눌러 `/admin/products/undefined/discounts` 로 간다.
      -->
      <RouterLink
        v-if="isEdit"
        class="btn btn-secondary btn-sm"
        :to="{ name: 'product-discount-admin', params: { id: props.id } }"
      >기간 할인 관리</RouterLink>
    </div>

    <!-- 전역/서버 에러는 상단 박스, 필드 단위 검증은 필드 아래(DESIGN.md §5) -->
    <div v-if="error" class="alert-error mb-4">{{ error }}</div>

    <div class="card flex flex-col gap-4 p-5">
      <label class="field">
        <span class="field-label">상품명</span>
        <DxTextBox v-model:value="form.name" />
      </label>
      <label class="field">
        <span class="field-label">한 줄 카피 (선택)</span>
        <DxTextBox v-model:value="form.tagline" placeholder="목록 카드에 상품명 아래로 보입니다" :max-length="100" />
        <span class="muted">비우면 카드에 안 보입니다. 100자까지.</span>
      </label>
      <div class="flex gap-4">
        <label class="field flex-1">
          <span class="field-label">카테고리</span>
          <DxSelectBox v-model:value="form.categoryId" :items="categories" value-expr="id" display-expr="name" placeholder="선택" />
        </label>
        <label class="field flex-1">
          <span class="field-label">상태</span>
          <DxSelectBox v-model:value="form.status" :items="STATUS_OPTIONS" value-expr="value" display-expr="text" />
        </label>
      </div>
      <div class="flex gap-4">
        <label class="field flex-1">
          <span class="field-label">판매가(원)</span>
          <DxNumberBox v-model:value="form.price" :min="0" format="#,##0" />
          <!--
            🔴 **세일 중이면 반드시 말해 준다** (2026-08-19, G-5).
            이 칸에는 **세일 전 판매가**가 들어 있는데(응답의 `regularPrice`), 고객이 보는 값은
            세일가다. 그걸 안 알려 주면 관리자는 «내가 9,000 이라 적었는데 왜 7,200 으로 팔리지» 를
            폼 어디에서도 확인할 수 없다 — 그리고 **여기 값을 고쳐서 맞추려 든다**(그러면 두 번 깎인다).
          -->
          <span v-if="activeSale" class="muted">
            지금 <b>{{ activeSale.rate }}%</b> 세일 중 — 고객에게는
            <b>{{ priceText(activeSale.price) }}</b> 으로 보입니다.
            이 칸은 <b>세일 전</b> 판매가입니다.
          </span>
        </label>
        <!--
          🔴 **「빈칸」과 「0」을 같은 뜻으로 받는다 — 할인 없음** (2026-08-13, 사용자 신고 → 사용자 제안).

          ⚠ **숫자 필드라서 못 비우는 게 아니다** — `list_price` 는 nullable 이고 「할인 없음」이 곧
          `null` 이다. 갇힌 건 **입력기가 빈 입력을 숫자 0 으로 되돌리기 때문**이고, 그러면 저장이
          「정가는 판매가보다 커야 합니다」로 막히는데 비울 방법이 없어 **그 상품을 영영 저장 못 하는
          상태**가 됐다.

          ⚠ **`:min="0"` 을 떼 보았지만 그대로였다**(2026-08-13 실측) — 즉 원인은 `min` 이 아니다.
          입력기를 더 파고들어 고치는 대신 **뜻을 넓혔다**: 0 도 「없음」으로 읽는다.
          🔴 정가 0원은 **어떤 판매가에도 유효할 수 없어** 다른 뜻을 가질 수가 없다 — 그래서 안전하다.
          → 저장 직전에 `0 → null` 로 바꾼다(위 `onSubmit`). 화면은 그 사실을 **문구로 말한다.**
        -->
        <label class="field flex-1">
          <span class="field-label">정가(원, 선택)</span>
          <DxNumberBox
            v-model:value="form.listPrice"
            :min="0"
            :show-clear-button="true"
            format="#,##0"
            placeholder="할인 없으면 비움"
          />
          <span class="muted">비우거나 <b>0</b> 이면 할인 없음 · 넣으려면 판매가보다 커야 합니다</span>
        </label>
      </div>

      <!-- 옵션 편집 (2026-07-24 C-8). 재고는 옵션마다. 단일 상품은 "기본" 한 줄이면 된다. -->
      <div class="field">
        <span class="field-label">옵션 · 재고</span>
        <p class="muted mb-2">옵션마다 재고를 둡니다. 사이즈·색상이 없으면 "기본" 한 줄만 두세요. 가격차는 기본가 대비 증감(±)입니다.</p>
        <div class="flex flex-col gap-2">
          <div v-for="(v, i) in form.variants" :key="i" class="flex flex-wrap items-end gap-2">
            <label class="field flex-1" style="min-width: 8rem">
              <span class="muted mb-1 block">옵션명</span>
              <DxTextBox v-model:value="v.name" placeholder="기본 / 검정 M" />
            </label>
            <label class="field" style="width: 8rem">
              <span class="muted mb-1 block">가격차(±)</span>
              <DxNumberBox v-model:value="v.priceDelta" format="#,##0" />
            </label>
            <label class="field" style="width: 7rem">
              <span class="muted mb-1 block">재고</span>
              <DxNumberBox v-model:value="v.stock" :min="0" format="#,##0" />
            </label>
            <button
              type="button"
              class="btn btn-secondary btn-sm"
              :disabled="form.variants.length <= 1"
              @click="removeVariant(i)"
            >삭제</button>
          </div>
        </div>
        <button type="button" class="btn btn-secondary btn-sm mt-2 self-start" @click="addVariant">+ 옵션 추가</button>
      </div>
      <label class="field">
        <span class="field-label">설명</span>
        <DxTextArea v-model:value="form.description" :height="160" />
      </label>

      <div class="field">
        <span class="field-label">이미지</span>
        <ImageUploader v-model="form.images" @error="error = $event" />
      </div>

      <div class="mt-2 flex gap-2">
        <button type="button" class="btn btn-primary" :disabled="saving" @click="onSave">
          {{ saving ? '저장 중…' : '저장' }}
        </button>
        <button type="button" class="btn btn-secondary" @click="router.back()">취소</button>
      </div>
    </div>

    <!--
      재고 변경 이력 (B-19, 2026-08-04) — **수정 화면에만** 둔다. 등록 화면엔 상품이 아직 없어
      물어볼 대상이 없다. 저장 버튼 아래 별도 카드로 두는 이유: 이건 **입력이 아니라 읽는 것**이라
      폼 안에 섞으면 "고쳐야 할 칸"으로 보인다.
    -->
    <div v-if="isEdit" class="card mt-5 p-5">
      <StockHistoryPanel :product-id="props.id" />
    </div>
  </section>
</template>
