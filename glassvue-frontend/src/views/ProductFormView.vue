<script setup>
import { reactive, ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxTextArea } from 'devextreme-vue/text-area';
import { DxNumberBox } from 'devextreme-vue/number-box';
import { DxSelectBox } from 'devextreme-vue/select-box';
import { DxButton } from 'devextreme-vue/button';
import { getProduct, createProduct, updateProduct, STATUS_OPTIONS } from '../api/product';
import { fetchCategories } from '../api/category';

const props = defineProps({ id: { type: String, default: null } });
const router = useRouter();
const isEdit = computed(() => !!props.id);

const categories = ref([]);
const form = reactive({ name: '', description: '', price: null, stock: null, status: 'SELLING', categoryId: null });
const error = ref('');
const saving = ref(false);

onMounted(async () => {
  try {
    categories.value = await fetchCategories();
  } catch (e) {
    /* ignore */
  }
  if (isEdit.value) {
    try {
      const p = await getProduct(props.id);
      Object.assign(form, {
        name: p.name, description: p.description, price: p.price,
        stock: p.stock, status: p.status, categoryId: p.categoryId,
      });
    } catch (e) {
      error.value = e.message;
    }
  }
});

async function onSave() {
  error.value = '';
  if (!form.name.trim() || !form.description.trim()) { error.value = '상품명·설명은 필수입니다.'; return; }
  if (form.price == null || form.stock == null) { error.value = '가격·재고를 입력하세요.'; return; }
  if (!form.categoryId) { error.value = '카테고리를 선택하세요.'; return; }
  saving.value = true;
  try {
    const payload = {
      name: form.name, description: form.description, price: form.price,
      stock: form.stock, status: form.status, categoryId: form.categoryId,
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
  <section class="max-w-2xl p-6">
    <h2 class="mb-4 text-xl font-semibold text-slate-800">{{ isEdit ? '상품 수정' : '상품 등록' }}</h2>
    <div v-if="error" class="mb-4 rounded bg-red-50 p-3 text-red-600">{{ error }}</div>

    <div class="flex flex-col gap-4 rounded-lg border bg-white p-6">
      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">상품명</span>
        <DxTextBox v-model:value="form.name" />
      </label>
      <div class="flex gap-4">
        <label class="flex flex-1 flex-col gap-1">
          <span class="text-sm text-slate-600">카테고리</span>
          <DxSelectBox v-model:value="form.categoryId" :items="categories" value-expr="id" display-expr="name" placeholder="선택" />
        </label>
        <label class="flex flex-1 flex-col gap-1">
          <span class="text-sm text-slate-600">상태</span>
          <DxSelectBox v-model:value="form.status" :items="STATUS_OPTIONS" value-expr="value" display-expr="text" />
        </label>
      </div>
      <div class="flex gap-4">
        <label class="flex flex-1 flex-col gap-1">
          <span class="text-sm text-slate-600">가격(원)</span>
          <DxNumberBox v-model:value="form.price" :min="0" format="#,##0" />
        </label>
        <label class="flex flex-1 flex-col gap-1">
          <span class="text-sm text-slate-600">재고</span>
          <DxNumberBox v-model:value="form.stock" :min="0" format="#,##0" />
        </label>
      </div>
      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">설명</span>
        <DxTextArea v-model:value="form.description" :height="160" />
      </label>

      <div class="mt-2 flex gap-2">
        <DxButton :text="saving ? '저장 중…' : '저장'" type="default" styling-mode="contained" :disabled="saving" @click="onSave" />
        <DxButton text="취소" styling-mode="outlined" @click="router.back()" />
      </div>
    </div>
  </section>
</template>
