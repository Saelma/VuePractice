import { createRouter, createWebHistory } from 'vue-router';
import { isLoggedIn, authState } from '../stores/auth';
import NoticeListView from '../views/NoticeListView.vue';
import NoticeDetailView from '../views/NoticeDetailView.vue';
import NoticeFormView from '../views/NoticeFormView.vue';
import LoginView from '../views/LoginView.vue';
import SignupView from '../views/SignupView.vue';
import MyPageView from '../views/MyPageView.vue';
import ProductListView from '../views/ProductListView.vue';
import ProductDetailView from '../views/ProductDetailView.vue';
import ProductFormView from '../views/ProductFormView.vue';
import OrderAdminView from '../views/OrderAdminView.vue';
import CategoryAdminView from '../views/CategoryAdminView.vue';
import CartView from '../views/CartView.vue';
import CheckoutView from '../views/CheckoutView.vue';
import OrderListView from '../views/OrderListView.vue';
import OrderDetailView from '../views/OrderDetailView.vue';

// 정적 경로(/notices/new)가 동적(/notices/:id)보다 우선 매칭된다(vue-router는 구체성 순).
const routes = [
  { path: '/', name: 'notice-list', component: NoticeListView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/signup', name: 'signup', component: SignupView },
  { path: '/settings', name: 'settings', component: MyPageView, meta: { requiresAuth: true } },
  { path: '/notices/new', name: 'notice-create', component: NoticeFormView, meta: { requiresAuth: true } },
  { path: '/notices/:id', name: 'notice-detail', component: NoticeDetailView, props: true },
  { path: '/notices/:id/edit', name: 'notice-edit', component: NoticeFormView, props: true, meta: { requiresAuth: true } },

  { path: '/products', name: 'product-list', component: ProductListView },
  { path: '/products/new', name: 'product-create', component: ProductFormView, meta: { requiresAdmin: true } },
  { path: '/products/:id', name: 'product-detail', component: ProductDetailView, props: true },
  { path: '/products/:id/edit', name: 'product-edit', component: ProductFormView, props: true, meta: { requiresAdmin: true } },
  { path: '/admin/categories', name: 'category-admin', component: CategoryAdminView, meta: { requiresAdmin: true } },
  { path: '/cart', name: 'cart', component: CartView, meta: { requiresAuth: true } },
  { path: '/checkout', name: 'checkout', component: CheckoutView, meta: { requiresAuth: true } },
  { path: '/orders', name: 'order-list', component: OrderListView, meta: { requiresAuth: true } },
  { path: '/admin/orders', name: 'order-admin', component: OrderAdminView, meta: { requiresAdmin: true } },
  { path: '/orders/:id', name: 'order-detail', component: OrderDetailView, props: true, meta: { requiresAuth: true } },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 로그인 필요 경로 가드
router.beforeEach((to) => {
  if ((to.meta.requiresAuth || to.meta.requiresAdmin) && !isLoggedIn.value) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.meta.requiresAdmin && authState.user?.role !== 'ADMIN') {
    return { path: '/products' }; // 관리자 아님 → 상품 목록으로
  }
  return true;
});

export default router;
