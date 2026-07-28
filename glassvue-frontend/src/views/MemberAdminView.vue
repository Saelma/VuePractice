<script setup>
/**
 * 관리자 회원 목록 (B-11).
 *
 * 관리자 도구가 주문·매출에 쏠려 있어 "회원을 들여다볼" 화면이 없었다. 여기서 회원을 찾고,
 * 행을 눌러 상세(주문·반품·적립금·등급)로 들어간다. 조회 전용 — 정지·역할변경은 다음 단계.
 * 탈퇴는 하드 삭제라 목록엔 현존 회원만 보인다.
 */
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import CustomStore from 'devextreme/data/custom_store';
import { DxDataGrid, DxColumn, DxPaging, DxPager } from 'devextreme-vue/data-grid';
import { DxTextBox } from 'devextreme-vue/text-box';
import { fetchAdminMembers, roleText } from '../api/member';

const router = useRouter();
const form = ref({ keyword: '' });
const applied = ref({ ...form.value });
const gridRef = ref(null);

const store = new CustomStore({
  key: 'id',
  load: async (options) => {
    const size = options.take || 10;
    const page = Math.floor((options.skip || 0) / size);
    const res = await fetchAdminMembers({ ...applied.value, page, size });
    return { data: res.content, totalCount: res.totalElements };
  },
});

function search() {
  applied.value = { ...form.value };
  gridRef.value?.instance.refresh();
}
function reset() {
  form.value = { keyword: '' };
  search();
}

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}
</script>

<template>
  <section class="page">
    <!-- 셸만 토큰/공용 클래스로. 표는 운영 화면이라 DataGrid 그대로 (DESIGN.md §7) -->
    <div class="mb-5">
      <h1 class="page-title">회원 관리</h1>
      <p class="muted mt-1">회원을 찾아 주문·반품·적립금을 확인합니다.</p>
    </div>

    <div class="card mb-4 flex flex-wrap items-end gap-3 p-4">
      <label class="field">
        <span class="field-label">검색</span>
        <DxTextBox v-model:value="form.keyword" placeholder="아이디·닉네임·이메일" :width="220" @enter-key="search" />
      </label>
      <div class="flex gap-2">
        <button type="button" class="btn btn-primary" @click="search">검색</button>
        <button type="button" class="btn btn-secondary" @click="reset">초기화</button>
      </div>
    </div>

    <DxDataGrid
      ref="gridRef"
      :data-source="store"
      :remote-operations="true"
      :show-borders="true"
      :column-auto-width="true"
      :hover-state-enabled="true"
      no-data-text="회원이 없습니다."
      @row-click="router.push(`/admin/members/${$event.data.id}`)"
    >
      <DxColumn data-field="loginId" caption="아이디" :width="160" />
      <DxColumn data-field="nickname" caption="닉네임" :width="160" />
      <DxColumn data-field="email" caption="이메일" :calculate-display-value="(r) => r.email || '—'" />
      <DxColumn data-field="role" caption="역할" :width="90" alignment="center" cell-template="roleCell" />
      <DxColumn data-field="suspended" caption="상태" :width="90" alignment="center" cell-template="statusCell" />
      <DxColumn data-field="createdAt" caption="가입일" :width="170" :calculate-display-value="(r) => fmt(r.createdAt)" />
      <DxColumn caption="처리" :width="90" alignment="center" cell-template="actionCell" />

      <DxPaging :page-size="10" />
      <DxPager :show-page-size-selector="true" :allowed-page-sizes="[10, 20, 50]" :show-info="true" info-text="{2}명 중 {0}-{1}" />

      <template #roleCell="{ data }">
        <span class="badge" :class="data.data.role === 'ADMIN' ? 'badge-neutral' : 'bg-canvas text-ink-400'">
          {{ roleText(data.data.role) }}
        </span>
      </template>

      <template #statusCell="{ data }">
        <span class="badge" :class="data.data.suspended ? 'badge-danger' : 'badge-success'">
          {{ data.data.suspended ? '정지' : '활성' }}
        </span>
      </template>

      <template #actionCell="{ data }">
        <button type="button" class="btn btn-ghost btn-sm" @click.stop="router.push(`/admin/members/${data.data.id}`)">
          상세
        </button>
      </template>
    </DxDataGrid>
  </section>
</template>
