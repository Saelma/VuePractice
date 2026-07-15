<script setup>
import { reactive, ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxTextArea } from 'devextreme-vue/text-area';
import { DxCheckBox } from 'devextreme-vue/check-box';
import { DxButton } from 'devextreme-vue/button';
import { getNotice, createNotice, updateNotice } from '../api/notice';

// id가 있으면 수정, 없으면 작성
const props = defineProps({ id: { type: String, default: null } });
const router = useRouter();
const isEdit = computed(() => !!props.id);

const form = reactive({ title: '', content: '', author: '', pinned: false });
const error = ref('');
const saving = ref(false);

onMounted(async () => {
  if (isEdit.value) {
    try {
      const n = await getNotice(props.id);
      form.title = n.title;
      form.content = n.content;
      form.author = n.author;
      form.pinned = n.pinned;
    } catch (e) {
      error.value = e.message;
    }
  }
});

async function onSave() {
  error.value = '';
  if (!form.title.trim() || !form.content.trim() || (!isEdit.value && !form.author.trim())) {
    error.value = '제목 · 본문 · 작성자는 필수입니다.';
    return;
  }
  saving.value = true;
  try {
    if (isEdit.value) {
      // 수정은 title/content/pinned 만 (작성자는 변경하지 않음)
      await updateNotice(props.id, {
        title: form.title,
        content: form.content,
        pinned: form.pinned,
      });
      router.push(`/notices/${props.id}`);
    } else {
      const newId = await createNotice({
        title: form.title,
        content: form.content,
        author: form.author,
        pinned: form.pinned,
      });
      router.push(`/notices/${newId}`);
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
    <h2 class="mb-4 text-xl font-semibold text-slate-800">
      {{ isEdit ? '공지 수정' : '새 공지 작성' }}
    </h2>

    <div v-if="error" class="mb-4 rounded bg-red-50 p-3 text-red-600">{{ error }}</div>

    <div class="flex flex-col gap-4 rounded-lg border bg-white p-6">
      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">제목</span>
        <DxTextBox v-model:value="form.title" placeholder="제목" />
      </label>

      <label v-if="!isEdit" class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">작성자</span>
        <DxTextBox v-model:value="form.author" placeholder="작성자" />
      </label>

      <label class="flex flex-col gap-1">
        <span class="text-sm text-slate-600">본문</span>
        <DxTextArea v-model:value="form.content" :height="220" placeholder="본문" />
      </label>

      <DxCheckBox v-model:value="form.pinned" text="상단 고정" />

      <div class="mt-2 flex gap-2">
        <DxButton
          :text="saving ? '저장 중…' : '저장'"
          type="default"
          styling-mode="contained"
          :disabled="saving"
          @click="onSave"
        />
        <DxButton text="취소" styling-mode="outlined" @click="router.back()" />
      </div>
    </div>
  </section>
</template>
