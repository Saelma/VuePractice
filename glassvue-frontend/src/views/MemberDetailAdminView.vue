<script setup>
/**
 * 관리자 회원 상세 (B-11).
 *
 * 한 회원의 기본정보 + 적립금·등급 + 주문(반품 포함) + 적립금 이력을 모은다. 각 조각은 **도메인별
 * admin 조회**를 프론트에서 합친 것이다 — member(기본), point(적립금), order(주문). 백엔드에서 한 도메인이
 * 다른 도메인을 참조하지 않게(order 가 이미 member 를 참조해 반대 방향은 순환) 조합을 여기서 한다.
 */
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import CustomStore from 'devextreme/data/custom_store';
import { DxDataGrid, DxColumn, DxPaging, DxPager } from 'devextreme-vue/data-grid';
import { fetchAdminMember, roleText } from '../api/member';
import { fetchAdminMemberPointAccount, fetchAdminMemberPointHistory, gradeText, pointTypeText } from '../api/point';
import { fetchAdminMemberOrders, ORDER_STATUS_OPTIONS, orderStatusText, orderStatusClass } from '../api/order';
import { priceText } from '../api/product';

const route = useRoute();
const router = useRouter();
const memberId = route.params.id;

const error = ref('');
const member = ref(null);
const point = ref(null);
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
      <!-- 기본정보 -->
      <div class="card p-5">
        <h2 class="section-title mb-3">기본 정보</h2>
        <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          <dt class="text-ink-500">아이디</dt><dd class="text-ink-900">{{ member.loginId }}</dd>
          <dt class="text-ink-500">닉네임</dt><dd class="text-ink-900">{{ member.nickname }}</dd>
          <dt class="text-ink-500">이메일</dt><dd class="text-ink-900">{{ member.email || '—' }}</dd>
          <dt class="text-ink-500">역할</dt>
          <dd><span class="badge" :class="member.role === 'ADMIN' ? 'badge-neutral' : 'bg-canvas text-ink-400'">{{ roleText(member.role) }}</span></dd>
          <dt class="text-ink-500">가입일</dt><dd class="text-ink-900">{{ fmt(member.createdAt) }}</dd>
        </dl>
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
        :show-borders="true"
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
        :show-borders="true"
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
