<script setup>
import { reactive, ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxTextArea } from 'devextreme-vue/text-area';
import { DxCheckBox } from 'devextreme-vue/check-box';
import { getNotice, createNotice, updateNotice } from '../api/notice';
import { authState, isAdminRole } from '../stores/auth';

// id가 있으면 수정, 없으면 작성. 작성자는 서버가 로그인 유저로 지정한다.
const props = defineProps({ id: { type: String, default: null } });
const router = useRouter();
const isEdit = computed(() => !!props.id);

const form = reactive({ title: '', content: '', pinned: false });
const error = ref('');
const saving = ref(false);
const blocked = ref(false);

onMounted(async () => {
  if (isEdit.value) {
    try {
      const n = await getNotice(props.id);
      if (n.authorId !== authState.user?.id && !isAdminRole(authState.user?.role)) {
        error.value = '본인 글만 수정할 수 있습니다.';
        blocked.value = true;
        return;
      }
      form.title = n.title;
      form.content = n.content;
      form.pinned = n.pinned;
    } catch (e) {
      error.value = e.message;
    }
  }
});

async function onSave() {
  error.value = '';
  if (!form.title.trim() || !form.content.trim()) {
    error.value = '제목 · 본문은 필수입니다.';
    return;
  }
  saving.value = true;
  try {
    const payload = { title: form.title, content: form.content, pinned: form.pinned };
    if (isEdit.value) {
      await updateNotice(props.id, payload);
      router.push(`/notices/${props.id}`);
    } else {
      const newId = await createNotice(payload);
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
  <section class="page-narrow">
    <div class="mb-5">
      <h1 class="page-title">{{ isEdit ? '공지 수정' : '새 공지 작성' }}</h1>
      <p class="muted mt-1">제목과 본문은 필수입니다. 작성자는 로그인 계정으로 기록됩니다.</p>
    </div>

    <div v-if="error" class="alert-error mb-4">{{ error }}</div>

    <div class="card flex flex-col gap-4 p-5">
      <label class="field">
        <span class="field-label">제목</span>
        <DxTextBox v-model:value="form.title" placeholder="제목" />
      </label>

      <label class="field">
        <span class="field-label">본문</span>
        <DxTextArea v-model:value="form.content" :height="220" placeholder="본문" />
      </label>

      <DxCheckBox v-model:value="form.pinned" text="상단 고정" />

      <div class="mt-2 flex gap-2 border-t border-line pt-4">
        <button type="button" class="btn btn-primary" :disabled="saving || blocked" @click="onSave">
          {{ saving ? '저장 중…' : '저장' }}
        </button>
        <button type="button" class="btn btn-secondary" @click="router.back()">취소</button>
      </div>
    </div>
  </section>
</template>
