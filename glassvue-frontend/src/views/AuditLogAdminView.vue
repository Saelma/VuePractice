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
import {
  fetchAuditLogs, auditActionText, auditActionBadge, auditTargetTypeText,
  AUDIT_ACTION_LABEL, AUDIT_TARGET_TYPE_LABEL,
} from '../api/audit';

const form = ref({ action: null, targetType: null, targetLogin: '' });
const applied = ref({ ...form.value });
const gridRef = ref(null);

const actionOptions = [
  { value: null, label: '전체' },
  ...Object.entries(AUDIT_ACTION_LABEL).map(([value, label]) => ({ value, label })),
];

/**
 * 🔴 **대상 종류 필터 (2026-08-20, V53).** 상품·쿠폰 행은 targetLogin 이 비어 있어
 * 「대상 아이디」로 못 찾는다 — 그전에는 「조작 종류」를 하나씩 골라 보는 수밖에 없었다.
 * 이제 «상품에 일어난 일 전부»(등록·수정·삭제·복구 + **세일 조작**)를 한 번에 볼 수 있다.
 */
const targetTypeOptions = [
  { value: null, label: '전체' },
  ...Object.entries(AUDIT_TARGET_TYPE_LABEL).map(([value, label]) => ({ value, label })),
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
  form.value = { action: null, targetType: null, targetLogin: '' };
  search();
}

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}

// 조작 종류별 뱃지 색 — 규칙과 목록은 api/audit.js 에 있다(라벨과 같은 자리).
// ⚠ 여기 if 사슬로 두면 enum 이 늘 때마다 **조용히 회색으로 떨어진다** — 2026-08-10 에
//    라벨이 9개 중 3개만 있던 것과 같은 드리프트다. 테스트가 대조할 수 있게 맵으로 뺐다.
const actionBadge = auditActionBadge;
</script>

<template>
  <section class="page">
    <!-- 셸만 토큰/공용 클래스로. 표는 운영 화면이라 DataGrid 그대로 (DESIGN.md §7) -->
    <div class="mb-5">
      <h1 class="page-title">감사 이력</h1>
      <p class="muted mt-1">관리자 조작(회원·주문·상품·쿠폰·세일) 이력입니다. 최상위 관리자만 조회합니다.</p>
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
        <DxSelectBox
          v-model:value="form.targetType"
          :items="targetTypeOptions"
          value-expr="value"
          display-expr="label"
          :width="140"
          placeholder="대상 종류"
        />
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
      <!--
        ⚠ 빈칸이 아니라 '—' 로 그린다(2026-08-14). 대상이 **회원이 아닌** 조작이 생기면서
        (상품 삭제·복구 — 그때 targetLogin 은 null 이다) 이 칸이 정상적으로 비는 줄이 섞인다.
        그냥 두면 «데이터가 빠졌다» 로 읽힌다 — 옆 「내용」 열이 같은 이유로 이미 이렇게 한다.
      -->
      <!--
        🔴 **「대상」과 「대상 아이디」는 다른 열이다**(2026-08-20). 앞은 «무엇을» 조작했는지
        (회원·상품·쿠폰), 뒤는 «그게 회원이면 누구인지» 다. 합치면 상품 행에서 뭘 보여줄지가 없다.
        ⚠ 옆 칸이 '—' 인 이유를 이 칸이 설명해 준다 — 「상품」이면 아이디가 비는 것이 정상이다.
      -->
      <DxColumn data-field="targetType" caption="대상" :width="90" alignment="center"
                :calculate-display-value="(r) => auditTargetTypeText(r.targetType)" />
      <DxColumn data-field="targetLogin" caption="대상 아이디" :width="160"
                :calculate-display-value="(r) => r.targetLogin || '—'" />
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
