import { createRouter, createWebHistory } from 'vue-router';
import { isLoggedIn } from '../stores/auth';
import NoticeListView from '../views/NoticeListView.vue';
import NoticeDetailView from '../views/NoticeDetailView.vue';
import NoticeFormView from '../views/NoticeFormView.vue';
import LoginView from '../views/LoginView.vue';
import SignupView from '../views/SignupView.vue';

// 정적 경로(/notices/new)가 동적(/notices/:id)보다 우선 매칭된다(vue-router는 구체성 순).
const routes = [
  { path: '/', name: 'notice-list', component: NoticeListView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/signup', name: 'signup', component: SignupView },
  { path: '/notices/new', name: 'notice-create', component: NoticeFormView, meta: { requiresAuth: true } },
  { path: '/notices/:id', name: 'notice-detail', component: NoticeDetailView, props: true },
  { path: '/notices/:id/edit', name: 'notice-edit', component: NoticeFormView, props: true, meta: { requiresAuth: true } },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 로그인 필요 경로 가드
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !isLoggedIn.value) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  return true;
});

export default router;
