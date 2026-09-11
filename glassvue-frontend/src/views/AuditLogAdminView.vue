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
import { DxTagBox } from 'devextreme-vue/tag-box';
import DataSource from 'devextreme/data/data_source';
import {
  fetchAuditLogs, auditActionText, auditActionBadge, auditTargetTypeText,
  auditActionOptions, AUDIT_ACTION_GROUPS, AUDIT_TARGET_TYPE_LABEL,
} from '../api/audit';
import AdminPeriodPicker from '../components/AdminPeriodPicker.vue';
import { PAGER_INFO } from '../constants/labels';

const EMPTY_FORM = () => ({ actions: [], targetType: null, targetLogin: '', from: '', to: '' });
const form = ref(EMPTY_FORM());

/**
 * 기간 선택 (B-26 잔여, 2026-09-07).
 *
 * 🔴 **고르면 바로 검색한다** — 다른 필터는 「검색」 버튼을 눌러야 적용되는데 기간만 다르다.
 * 프리셋은 «누르는 것이 곧 질문»이라(「지난 달」) 한 번 더 누르게 하면 두 번 눌러야 한다.
 * ⚠ 직접 고르기도 **양쪽이 다 찼을 때만** 컴포넌트가 알려 준다(반쪽 기간은 질문이 안 된다).
 */
function applyPeriod({ from, to }) {
  form.value = { ...form.value, from, to };
  search();
}
const applied = ref({ ...form.value });
const gridRef = ref(null);

/**
 * 🔴 **조작 종류 — 검색되는 분류별 체크 드롭다운** (2026-09-11).
 *
 * 조작이 34개가 되자 한 줄 목록이 너무 길어졌다(사용자). 벤치마크: GitHub 감사 로그(분류.동작 —
 * 분류 통째로도 하나만도 거른다) · Datadog Audit Trail(체크박스 패싯 — 여러 개를 동시에).
 * → `DxTagBox` 하나로 **검색 + 분류 묶음 + 체크박스 + 여러 개**. 비우면 «전체» 다.
 * ⚠ **검색은 분류 이름에도 걸린다** — «주문» 만 쳐도 주문 분류가 통째로 나온다.
 * ⚠ 닫혀 있을 때는 한 칸 폭을 지킨다 — 둘 이상 고르면 «N개 선택» 한 조각으로 접는다(`onMultiTag`).
 */
const actionSource = new DataSource({
  store: auditActionOptions(),
  // 분류는 **정한 순서**로 — 이름으로 정렬되면 «공지·마케팅» 이 맨 위로 온다. 머리글은 템플릿이 이름으로 바꾼다.
  group: 'groupIndex',
  paginate: false,
});
function actionGroupTitle(key) {
  return AUDIT_ACTION_GROUPS[key] ?? '기타';
}
function onMultiTag(e) {
  e.text = `${e.selectedItems.length}개 선택`;
}
/**
 * 🔴 **입력칸을 누르면 «열기» 만 한다 — 닫지 않는다** (2026-09-11).
 * DevExtreme 기본(`openOnFieldClick`)은 누를 때마다 **열림↔닫힘을 뒤집는다** — 검색하려고 한 번 더 누르면
 * 목록이 닫혔다 열렸다 한다(사용자: *"나타났다가 사라졌다 하는 불쾌한 UX"*). jsdom 에서 재 보니 세 번 누르면
 * `[열림, 닫힘, 열림]` 이었다. 닫기는 **바깥 클릭·Esc** 로 한다(DevExtreme 기본 그대로).
 */
const actionBoxRef = ref(null);
function openActions() {
  actionBoxRef.value?.instance.open();
}

/**
 * 🔴 **대상 종류 필터 (2026-08-20, V53).** 상품·쿠폰 행은 targetLogin 이 비어 있어
 * 「대상 아이디」로 못 찾는다 — 그전에는 「조작 종류」를 하나씩 골라 보는 수밖에 없었다.
 * 이제 «상품에 일어난 일 전부»(등록·수정·삭제·복구 + **세일 조작**)를 한 번에 볼 수 있다.
 */
const targetTypeOptions = [
  { value: null, label: '전체' },
  ...Object.entries(AUDIT_TARGET_TYPE_LABEL).map(([value, label]) => ({ value, label })),
];

/**
 * 🔴 **검색 결과 건수 — 표 위에 늘 보인다** (2026-09-11). 페이저도 건수를 말하지만 **한 쪽에 다 들어가면
 * 페이저째 숨는다**(DevExtreme `visible: 'auto'`) — 20건을 거르자 건수가 사라졌다(사용자: *"몇 건인지 안 뜨네"*).
 * ⚠ 못 읽으면 `null` 로 — «0건» 으로 그리면 «아무 일도 없었다» 로 읽힌다(대시보드와 같은 규칙).
 */
const total = ref(null);

const store = new CustomStore({
  key: 'id',
  load: async (options) => {
    const size = options.take || 20;
    const page = Math.floor((options.skip || 0) / size);
    try {
      const res = await fetchAuditLogs({ ...applied.value, page, size });
      total.value = res.totalElements;
      return { data: res.content, totalCount: res.totalElements };
    } catch (e) {
      total.value = null;
      throw e;
    }
  },
});

function search() {
  applied.value = { ...form.value };
  gridRef.value?.instance.refresh();
}
function reset() {
  form.value = EMPTY_FORM();
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

    <!--
      ⚠ 기간을 **한 줄 위**에 둔다 — 「그날 누가 무엇을 했나」가 이 화면에서 제일 잦은 질문이라
      조작 종류·대상보다 먼저 눈에 와야 한다(B-26 이 그 질문을 「감사 로그의 존재 이유에 가깝다」고 적었다).
    -->
    <div class="card mb-4 space-y-3 p-4">
      <AdminPeriodPicker :from="form.from" :to="form.to" @change="applyPeriod" />
      <div class="flex flex-wrap items-end gap-3 border-t border-line pt-3">
      <label class="field" data-test="action-field" @click="openActions">
        <span class="field-label">조작 종류</span>
        <DxTagBox
          ref="actionBoxRef"
          v-model:value="form.actions"
          :open-on-field-click="false"
          :data-source="actionSource"
          value-expr="value"
          display-expr="label"
          :grouped="true"
          group-template="actionGroup"
          :search-enabled="true"
          :search-expr="['label', 'group']"
          :show-selection-controls="true"
          :max-displayed-tags="1"
          :show-multi-tag-only="true"
          :on-multi-tag-preparing="onMultiTag"
          :drop-down-options="{ wrapperAttr: { class: 'audit-action-popup' } }"
          placeholder="전체"
          :width="220"
        >
          <template #actionGroup="{ data }">{{ actionGroupTitle(data.key) }}</template>
        </DxTagBox>
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
    </div>

    <p v-if="total !== null" class="muted mb-2" data-test="audit-total">
      총 <strong class="tabular-nums text-ink-900">{{ total.toLocaleString('ko-KR') }}</strong>건
    </p>
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
      <DxPager :show-page-size-selector="true" :allowed-page-sizes="[20, 50, 100]" :show-info="true" :info-text="PAGER_INFO" />

      <template #actionCell="{ data }">
        <span class="badge" :class="actionBadge(data.data.action)">
          {{ auditActionText(data.data.action) }}
        </span>
      </template>
    </DxDataGrid>
  </section>
</template>
