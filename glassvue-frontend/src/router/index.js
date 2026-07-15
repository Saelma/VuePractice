import { createRouter, createWebHistory } from 'vue-router';
import NoticeListView from '../views/NoticeListView.vue';
import NoticeDetailView from '../views/NoticeDetailView.vue';
import NoticeFormView from '../views/NoticeFormView.vue';

// 정적 경로(/notices/new)가 동적(/notices/:id)보다 우선 매칭된다(vue-router는 구체성 순).
const routes = [
  { path: '/', name: 'notice-list', component: NoticeListView },
  { path: '/notices/new', name: 'notice-create', component: NoticeFormView },
  { path: '/notices/:id', name: 'notice-detail', component: NoticeDetailView, props: true },
  { path: '/notices/:id/edit', name: 'notice-edit', component: NoticeFormView, props: true },
];

export default createRouter({
  history: createWebHistory(),
  routes,
});
