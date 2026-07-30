<script setup>
/**
 * 관리자 회원 상세 (B-11).
 *
 * 한 회원의 기본정보 + 적립금·등급 + 주문(반품 포함) + 적립금 이력을 모은다. 각 조각은 **도메인별
 * admin 조회**를 프론트에서 합친 것이다 — member(기본), point(적립금), order(주문). 백엔드에서 한 도메인이
 * 다른 도메인을 참조하지 않게(order 가 이미 member 를 참조해 반대 방향은 순환) 조합을 여기서 한다.
 */
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import CustomStore from 'devextreme/data/custom_store';
import { DxDataGrid, DxColumn, DxPaging, DxPager } from 'devextreme-vue/data-grid';
import { authState } from '../stores/auth';
import {
  fetchAdminMember, roleText, suspendMember, unsuspendMember, changeMemberRole, deleteMember,
} from '../api/member';
import { fetchAdminMemberPointAccount, fetchAdminMemberPointHistory, gradeText, pointTypeText } from '../api/point';
import { fetchAdminMemberOrders, ORDER_STATUS_OPTIONS, orderStatusText, orderStatusClass } from '../api/order';
import { priceText } from '../api/product';

const route = useRoute();
const router = useRouter();
const memberId = route.params.id;

const error = ref('');
const member = ref(null);
const point = ref(null);
const busy = ref(false);
// 자기 계정은 정지·강등 못 한다(서버도 400으로 막지만 화면에서 버튼을 숨긴다 — 락아웃 방지).
const isSelf = computed(() => member.value?.id === authState.user?.id);
// 엄격 분리(2026-07-28): 일반 ADMIN 은 USER 만 정지, 역할변경·관리자 정지는 SUPER_ADMIN 전용,
// SUPER_ADMIN 계정은 아무도 못 건드림. 서버가 최종 방어선이고 화면은 그에 맞춰 버튼을 감춘다.
const viewerIsSuper = computed(() => authState.user?.role === 'SUPER_ADMIN');
const targetIsSuper = computed(() => member.value?.role === 'SUPER_ADMIN');
const canSuspend = computed(() =>
  !isSelf.value && !targetIsSuper.value && (viewerIsSuper.value || member.value?.role === 'USER'));
const canChangeRole = computed(() => !isSelf.value && !targetIsSuper.value && viewerIsSuper.value);
const orderStatus = ref(null); // 주문 상태 필터(반품만 보기 = RETURN_REQUESTED/RETURNED)
const orderGridRef = ref(null);

onMounted(async () => {
  try {
    // 기본정보·적립금은 상세 진입 즉시 함께 읽는다. 주문·이력은 그리드가 페이징으로 지연 로드.
    [member.value, point.value] = await Promise.all([
      fetchAdminMember(memberId),
      fetchAdminMemberPointAccount(memberId).catch(() => null), // 적립금 못 읽어도 나머지는 보여준다
    ]);
  } catch (e) {
    error.value = e.message;
  }
});

const orderStore = new CustomStore({
  key: 'id',
  load: async (options) => {
    const size = options.take || 10;
    const page = Math.floor((options.skip || 0) / size);
    const res = await fetchAdminMemberOrders(memberId, { status: orderStatus.value, page, size });
    return { data: res.content, totalCount: res.totalElements };
  },
});
function applyOrderStatus() {
  orderGridRef.value?.instance.refresh();
}

const pointStore = new CustomStore({
  key: 'id',
  load: async (options) => {
    const size = options.take || 10;
    const page = Math.floor((options.skip || 0) / size);
    const res = await fetchAdminMemberPointHistory(memberId, { page, size });
    return { data: res.content, totalCount: res.totalElements };
  },
});

async function toggleSuspend() {
  const t = member.value;
  const verb = t.suspended ? '정지 해제' : '정지';
  const warn = t.suspended ? '' : ' 로그인·주문이 막히고 기존 세션도 곧 끊깁니다.';
  if (!window.confirm(`${t.nickname}님을 ${verb}할까요?${warn}`)) return;
  error.value = ''; busy.value = true;
  try {
    member.value = t.suspended ? await unsuspendMember(t.id) : await suspendMember(t.id);
  } catch (e) { error.value = e.message; } finally { busy.value = false; }
}

async function toggleRole() {
  const t = member.value;
  const next = t.role === 'ADMIN' ? 'USER' : 'ADMIN';
  if (!window.confirm(`${t.nickname}님의 역할을 ${roleText(next)}(으)로 바꿀까요?`)) return;
  error.value = ''; busy.value = true;
  try {
    member.value = await changeMemberRole(t.id, next);
  } catch (e) { error.value = e.message; } finally { busy.value = false; }
}

/**
 * 회원 강제 삭제(B-24) — 되돌릴 수 없어 확인을 **두 단계**로 받는다.
 *
 * ⚠ 지워지는 것과 남는 것을 문구에 그대로 적는다: 관리자가 "주문 기록도 날아가나?" 를 여기서 알아야
 * 하고(안 날아간다), 반대로 배송지·적립금이 사라진다는 것도 눌러 보고 알게 하면 안 된다.
 */
async function removeMember() {
  const t = member.value;
  if (!window.confirm(
    `${t.nickname}(${t.loginId})님을 삭제할까요?\n\n`
    + '함께 지워집니다: 배송지·적립금·찜·쿠폰·알림·재입고 알림 신청·상품 문의\n'
    + '남습니다: 주문 내역·리뷰(작성자명은 그대로 표시)\n\n'
    + '되돌릴 수 없습니다.')) return;
  if (!window.confirm('한 번 더 확인합니다. 정말 삭제할까요?')) return;
  error.value = ''; busy.value = true;
  try {
    await deleteMember(t.id);
    router.push('/admin/members'); // 삭제된 회원의 상세에 머물 이유가 없다
  } catch (e) { error.value = e.message; busy.value = false; }
}

function fmt(v) {
  return v ? new Date(v).toLocaleString('ko-KR') : '';
}
function signedPoint(n) {
  const v = Number(n) || 0;
  return `${v > 0 ? '+' : ''}${v.toLocaleString('ko-KR')}P`;
}
</script>

<template>
  <section class="page">
    <div class="mb-5 flex items-center gap-3">
      <button type="button" class="btn btn-ghost btn-sm" @click="router.push('/admin/members')">← 목록</button>
      <h1 class="page-title">회원 상세</h1>
    </div>

    <div v-if="error" class="alert-error mb-4">{{ error }}</div>

    <div v-if="member" class="grid gap-4 md:grid-cols-2">
      <!-- 기본정보 + 관리 -->
      <div class="card p-5">
        <h2 class="section-title mb-3">기본 정보</h2>
        <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          <dt class="text-ink-500">아이디</dt><dd class="text-ink-900">{{ member.loginId }}</dd>
          <dt class="text-ink-500">닉네임</dt><dd class="text-ink-900">{{ member.nickname }}</dd>
          <dt class="text-ink-500">이메일</dt><dd class="text-ink-900">{{ member.email || '—' }}</dd>
          <dt class="text-ink-500">역할</dt>
          <dd><span class="badge" :class="member.role === 'ADMIN' ? 'badge-neutral' : 'bg-canvas text-ink-400'">{{ roleText(member.role) }}</span></dd>
          <dt class="text-ink-500">상태</dt>
          <dd>
            <span class="badge" :class="member.suspended ? 'badge-danger' : 'badge-success'">
              {{ member.suspended ? '정지됨' : '활성' }}
            </span>
          </dd>
          <dt class="text-ink-500">가입일</dt><dd class="text-ink-900">{{ fmt(member.createdAt) }}</dd>
        </dl>

        <!-- 관리: 계층·본인에 따라 버튼을 감추고 안내만 (서버가 최종 방어선) -->
        <div class="mt-4 border-t border-line pt-4">
          <p v-if="isSelf" class="muted">본인 계정입니다 — 정지·역할 변경은 다른 관리자만 할 수 있습니다.</p>
          <p v-else-if="targetIsSuper" class="muted">최상위 관리자 계정은 정지·역할 변경할 수 없습니다.</p>
          <template v-else>
            <div class="flex flex-wrap gap-2">
              <button
                v-if="canSuspend"
                type="button"
                class="btn btn-sm"
                :class="member.suspended ? 'btn-secondary' : 'btn-danger'"
                :disabled="busy"
                @click="toggleSuspend"
              >{{ member.suspended ? '정지 해제' : '정지' }}</button>
              <button
                v-if="canChangeRole"
                type="button"
                class="btn btn-secondary btn-sm"
                :disabled="busy"
                @click="toggleRole"
              >{{ member.role === 'ADMIN' ? '일반으로 강등' : '관리자로 승격' }}</button>
              <!-- 삭제는 되돌릴 수 없어 최상위 관리자만(B-24). 강등·정지와 같은 줄에 두되 맨 끝에 둔다. -->
              <button
                v-if="viewerIsSuper"
                type="button"
                class="btn btn-danger btn-sm"
                :disabled="busy"
                @click="removeMember"
              >회원 삭제</button>
            </div>
            <p v-if="!canSuspend && !canChangeRole" class="muted">
              관리자 계정은 최상위 관리자만 정지·역할 변경할 수 있습니다.
            </p>
          </template>
        </div>
      </div>

      <!-- 적립금·등급 -->
      <div class="card p-5">
        <h2 class="section-title mb-3">적립금 · 등급</h2>
        <template v-if="point">
          <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
            <dt class="text-ink-500">등급</dt>
            <dd class="text-ink-900">{{ gradeText(point.grade) }} <span class="muted">(적립 {{ point.earnPercent }}%)</span></dd>
            <dt class="text-ink-500">보유 적립금</dt><dd class="text-ink-900">{{ priceText(point.balance) }}</dd>
            <dt class="text-ink-500">누적 구매액</dt><dd class="text-ink-900">{{ priceText(point.totalPurchase) }}</dd>
            <template v-if="point.nextGrade">
              <dt class="text-ink-500">다음 등급</dt>
              <dd class="text-ink-900">{{ gradeText(point.nextGrade) }}까지 {{ priceText(point.amountToNextGrade) }}</dd>
            </template>
          </dl>
        </template>
        <p v-else class="muted">적립금 정보를 불러오지 못했습니다.</p>
      </div>
    </div>

    <!-- 주문 · 반품 -->
    <div v-if="member" class="mt-6">
      <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 class="section-title">주문 · 반품</h2>
        <label class="flex items-center gap-2 text-sm">
          <span class="muted">상태</span>
          <select v-model="orderStatus" class="field" @change="applyOrderStatus">
            <option v-for="o in ORDER_STATUS_OPTIONS" :key="o.value ?? 'all'" :value="o.value">{{ o.text }}</option>
          </select>
        </label>
      </div>
      <DxDataGrid
        ref="orderGridRef"
        :data-source="orderStore"
        :remote-operations="true"
        :show-borders="false"
        :show-column-lines="false"
        :column-auto-width="true"
        :hover-state-enabled="true"
        no-data-text="주문이 없습니다."
      >
        <DxColumn data-field="orderNo" caption="주문번호" :width="140" />
        <DxColumn data-field="createdAt" caption="주문일시" :width="160" :calculate-display-value="(r) => fmt(r.createdAt)" />
        <DxColumn data-field="summary" caption="상품" />
        <DxColumn data-field="payAmount" caption="금액" :width="120" alignment="right" :calculate-display-value="(r) => priceText(r.payAmount)" />
        <DxColumn data-field="status" caption="상태" :width="100" alignment="center" cell-template="statusCell" />
        <DxColumn caption="처리" :width="80" alignment="center" cell-template="actionCell" />
        <DxPaging :page-size="10" />
        <DxPager :show-info="true" info-text="{2}건 중 {0}-{1}" />
        <template #statusCell="{ data }">
          <span class="badge" :class="orderStatusClass(data.data.status)">{{ orderStatusText(data.data.status) }}</span>
        </template>
        <template #actionCell="{ data }">
          <button type="button" class="btn btn-ghost btn-sm" @click="router.push(`/orders/${data.data.id}`)">상세</button>
        </template>
      </DxDataGrid>
    </div>

    <!-- 적립금 이력 -->
    <div v-if="member" class="mt-6">
      <h2 class="section-title mb-3">적립금 이력</h2>
      <DxDataGrid
        :data-source="pointStore"
        :remote-operations="true"
        :show-borders="false"
        :show-column-lines="false"
        :column-auto-width="true"
        :hover-state-enabled="true"
        no-data-text="적립금 이력이 없습니다."
      >
        <DxColumn data-field="createdAt" caption="일시" :width="170" :calculate-display-value="(r) => fmt(r.createdAt)" />
        <DxColumn data-field="type" caption="구분" :width="90" alignment="center" :calculate-display-value="(r) => pointTypeText(r.type)" />
        <DxColumn data-field="amount" caption="변동" :width="120" alignment="right" :calculate-display-value="(r) => signedPoint(r.amount)" />
        <DxColumn data-field="balanceAfter" caption="잔액" :width="120" alignment="right" :calculate-display-value="(r) => priceText(r.balanceAfter)" />
        <DxColumn data-field="reason" caption="사유" />
        <DxPaging :page-size="10" />
        <DxPager :show-info="true" info-text="{2}건 중 {0}-{1}" />
      </DxDataGrid>
    </div>
  </section>
</template>
