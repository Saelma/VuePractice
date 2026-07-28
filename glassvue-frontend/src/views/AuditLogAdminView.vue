<script setup>
/**
 * 관리자 감사 이력 (SUPER_ADMIN 전용).
 *
 * 관리자 조작(회원 정지·해제·역할변경)이 지금까지 서버 로그로만 흘러가 조회할 수 없었다. 여기서
 * 누가(actor) 누구를(target) 언제 어떻게 바꿨는지 append-only 이력을 조회한다. 조회 권한은 서버가
 * /api/admin/audit = SUPER_ADMIN 으로 막고, 이 화면 진입도 라우터가 SUPER 만 통과시킨다.
 * 스냅샷(관리자 닉네임·대상 loginId)을 그대로 실어, 대상이 탈퇴·개명해도 그대로 읽힌다.
 */
import { ref } from 'vue';
import CustomStore from 'devextreme/data/custom_store';
import { DxDataGrid, DxColumn, DxPaging, DxPager } from 'devextreme-vue/data-grid';
import { DxTextBox } from 'devextreme-vue/text-box';
import { DxSelectBox } from 'devextreme-vue/select-box';
import { fetchAuditLogs, auditActionText, AUDIT_ACTION_LABEL } from '../api/audit';

const form = ref({ action: null, targetLogin: '' });
const applied = ref({ ...form.value });
const gridRef = ref(null);

const actionOptions = [
  { value: null, label: '전체' },
  ...Object.entries(AUDIT_ACTION_LABEL).map(([value, label]) => ({ value, label })),
];

const store = new CustomStore({
  key: 'id',
  load: async (options) => {
    const size = options.take || 20;
    const page = Math.floor((options.skip || 0) / size);
    const res = await fetchAuditLogs({ ...applied.value, page, size });
    return { data: res.content, totalCount: res.totalElements };
  },
});

function search() {
  applied.value = { ...form.value };
  gridRef.value?.instance.refresh();
}
function reset() {
  form.value = { action: null, targetLogin: '' };
  search();
}

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}

// 조작 종류별 뱃지 색: 정지=위험, 해제=성공, 역할변경=중립.
function actionBadge(action) {
  if (action === 'MEMBER_SUSPEND') return 'badge-danger';
  if (action === 'MEMBER_UNSUSPEND') return 'badge-success';
  return 'badge-neutral';
}
</script>

<template>
  <section class="page">
    <!-- 셸만 토큰/공용 클래스로. 표는 운영 화면이라 DataGrid 그대로 (DESIGN.md §7) -->
    <div class="mb-5">
      <h1 class="page-title">감사 이력</h1>
      <p class="muted mt-1">관리자 조작(정지·해제·역할변경) 이력입니다. 최상위 관리자만 조회합니다.</p>
    </div>

    <div class="card mb-4 flex flex-wrap items-end gap-3 p-4">
      <label class="field">
        <span class="field-label">조작 종류</span>
        <DxSelectBox
          v-model:value="form.action"
          :items="actionOptions"
          value-expr="value"
          display-expr="label"
          :width="160"
        />
      </label>
      <label class="field">
        <span class="field-label">대상 아이디</span>
        <DxTextBox v-model:value="form.targetLogin" placeholder="loginId 부분일치" :width="200" @enter-key="search" />
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
      :show-borders="false"
      :show-column-lines="false"
      :column-auto-width="true"
      :hover-state-enabled="true"
      no-data-text="감사 이력이 없습니다."
    >
      <DxColumn data-field="createdAt" caption="일시" :width="180" :calculate-display-value="(r) => fmt(r.createdAt)" />
      <DxColumn data-field="action" caption="조작" :width="110" alignment="center" cell-template="actionCell" />
      <DxColumn data-field="actorName" caption="관리자" :width="140" />
      <DxColumn data-field="targetLogin" caption="대상 아이디" :width="160" />
      <DxColumn data-field="detail" caption="내용" :calculate-display-value="(r) => r.detail || '—'" />

      <DxPaging :page-size="20" />
      <DxPager :show-page-size-selector="true" :allowed-page-sizes="[20, 50, 100]" :show-info="true" info-text="{2}건 중 {0}-{1}" />

      <template #actionCell="{ data }">
        <span class="badge" :class="actionBadge(data.data.action)">
          {{ auditActionText(data.data.action) }}
        </span>
      </template>
    </DxDataGrid>
  </section>
</template>
