import { createRouter, createWebHistory } from 'vue-router';
import { isLoggedIn, authState, isAdminRole, isSuperAdminRole } from '../stores/auth';
import { loadMe } from '../api/auth';
import HomeView from '../views/HomeView.vue';
import NoticeListView from '../views/NoticeListView.vue';
import NoticeDetailView from '../views/NoticeDetailView.vue';
import NoticeFormView from '../views/NoticeFormView.vue';
import LoginView from '../views/LoginView.vue';
import SignupView from '../views/SignupView.vue';
import ForgotPasswordView from '../views/ForgotPasswordView.vue';
import ResetPasswordView from '../views/ResetPasswordView.vue';
import FindIdView from '../views/FindIdView.vue';
import MyPageView from '../views/MyPageView.vue';
import BenefitsView from '../views/BenefitsView.vue';
import ProductListView from '../views/ProductListView.vue';
import ProductDetailView from '../views/ProductDetailView.vue';
import ProductFormView from '../views/ProductFormView.vue';
import OrderAdminView from '../views/OrderAdminView.vue';
import CategoryAdminView from '../views/CategoryAdminView.vue';
import CouponAdminView from '../views/CouponAdminView.vue';
import StatsAdminView from '../views/StatsAdminView.vue';
import MemberAdminView from '../views/MemberAdminView.vue';
import MemberDetailAdminView from '../views/MemberDetailAdminView.vue';
import AuditLogAdminView from '../views/AuditLogAdminView.vue';
import CartView from '../views/CartView.vue';
import WishlistView from '../views/WishlistView.vue';
import CheckoutView from '../views/CheckoutView.vue';
import OrderListView from '../views/OrderListView.vue';
import OrderDetailView from '../views/OrderDetailView.vue';
import NotificationsView from '../views/NotificationsView.vue';
import MockTrackingView from '../views/MockTrackingView.vue';

// 정적 경로(/notices/new)가 동적(/notices/:id)보다 우선 매칭된다(vue-router는 구체성 순).
const routes = [
  // 첫 화면은 스토어프론트 홈(B-8). 공지는 /notices 로 옮겼다 — 커머스의 첫 화면은 상품이어야 한다.
  { path: '/', name: 'home', component: HomeView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/signup', name: 'signup', component: SignupView },
  // 비밀번호 재설정 — 로그인 전 접근하므로 공개(requiresAuth 없음).
  { path: '/forgot-password', name: 'forgot-password', component: ForgotPasswordView },
  { path: '/find-id', name: 'find-id', component: FindIdView },
  { path: '/reset-password', name: 'reset-password', component: ResetPasswordView },
  { path: '/settings', name: 'settings', component: MyPageView, meta: { requiresAuth: true } },
  { path: '/benefits', name: 'benefits', component: BenefitsView, meta: { requiresAuth: true } },
  { path: '/notices', name: 'notice-list', component: NoticeListView },
  { path: '/notices/new', name: 'notice-create', component: NoticeFormView, meta: { requiresAuth: true } },
  { path: '/notices/:id', name: 'notice-detail', component: NoticeDetailView, props: true },
  { path: '/notices/:id/edit', name: 'notice-edit', component: NoticeFormView, props: true, meta: { requiresAuth: true } },

  { path: '/products', name: 'product-list', component: ProductListView },
  { path: '/products/new', name: 'product-create', component: ProductFormView, meta: { requiresAdmin: true } },
  { path: '/products/:id', name: 'product-detail', component: ProductDetailView, props: true },
  { path: '/products/:id/edit', name: 'product-edit', component: ProductFormView, props: true, meta: { requiresAdmin: true } },
  { path: '/admin/categories', name: 'category-admin', component: CategoryAdminView, meta: { requiresAdmin: true } },
  { path: '/admin/stats', name: 'stats-admin', component: StatsAdminView, meta: { requiresAdmin: true } },
  { path: '/admin/coupons', name: 'coupon-admin', component: CouponAdminView, meta: { requiresAdmin: true } },
  { path: '/admin/members', name: 'member-admin', component: MemberAdminView, meta: { requiresAdmin: true } },
  { path: '/admin/members/:id', name: 'member-admin-detail', component: MemberDetailAdminView, props: true, meta: { requiresAdmin: true } },
  // 감사 이력은 최상위 관리자만 — 조작 당사자(ADMIN)가 자기 이력을 보는 구조를 막는다.
  { path: '/admin/audit', name: 'audit-admin', component: AuditLogAdminView, meta: { requiresSuperAdmin: true } },
  { path: '/cart', name: 'cart', component: CartView, meta: { requiresAuth: true } },
  { path: '/wishlist', name: 'wishlist', component: WishlistView, meta: { requiresAuth: true } },
  { path: '/checkout', name: 'checkout', component: CheckoutView, meta: { requiresAuth: true } },
  { path: '/orders', name: 'order-list', component: OrderListView, meta: { requiresAuth: true } },
  { path: '/notifications', name: 'notifications', component: NotificationsView, meta: { requiresAuth: true } },
  { path: '/admin/orders', name: 'order-admin', component: OrderAdminView, meta: { requiresAdmin: true } },
  { path: '/orders/:id', name: 'order-detail', component: OrderDetailView, props: true, meta: { requiresAuth: true } },

  // 배송 조회 **예시** 페이지. 실제 택배사 사이트 대신 여기로 보낸다(연습 단계라 외부 의존을 만들지 않는다).
  // 백엔드 설정 glassvue.delivery.default-tracking-url 이 이 경로를 가리킨다.
  { path: '/mock-tracking', name: 'mock-tracking', component: MockTrackingView },

  // 정의되지 않은 경로(오타·삭제된 링크 등). 라우트가 없으면 meta 도 없어 위 가드가 관여를 못 한다
  // (예: `/admin/member` 처럼 `/admin/members` 오타는 빈 화면으로 "이동은 되는" 구멍이 됐다).
  // **`/admin/*` 미정의 경로는 관리 화면 존재를 노출하지 않게 `/products`**, 그 외는 홈으로 보낸다.
  { path: '/:pathMatch(.*)*', name: 'not-found', redirect: (to) => (to.path.startsWith('/admin') ? '/products' : '/') },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 로그인 필요 경로 가드
router.beforeEach(async (to) => {
  const needsAuth = to.meta.requiresAuth || to.meta.requiresAdmin || to.meta.requiresSuperAdmin;
  // 토큰은 있는데 내 정보가 아직 안 실렸으면(새로고침·URL 직접 진입) 먼저 로드해 역할을 확정한다.
  // App.vue의 loadMe는 mount 이후라 초기 네비게이션에는 늦다 — 안 기다리면 관리자가 admin 경로를
  // 직접 열 때 user가 null이라 아래 역할 체크에서 자기 화면인데도 튕긴다.
  if (needsAuth && isLoggedIn.value && !authState.user) {
    await loadMe();
  }
  // 관리자 경로는 권한이 없으면(비로그인 포함) **로그인 유도 없이** 상품 목록으로 보낸다 —
  // 관리 화면의 존재 자체를 노출하지 않는다(사용자 요청, 2026-07-28).
  if (to.meta.requiresAdmin && !isAdminRole(authState.user?.role)) {
    return { path: '/products' };
  }
  // 최상위 관리자 전용 경로(감사 이력)는 SUPER_ADMIN 이 아니면(일반 ADMIN·비로그인 포함) 상품 목록으로.
  if (to.meta.requiresSuperAdmin && !isSuperAdminRole(authState.user?.role)) {
    return { path: '/products' };
  }
  // 그 외 로그인 필요 경로: 비로그인 → 로그인(원래 경로로 복귀하도록 redirect 보존)
  if (to.meta.requiresAuth && !isLoggedIn.value) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  return true;
});

export default router;
