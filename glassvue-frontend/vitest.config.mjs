import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';

// 프론트 단위 테스트. api 헬퍼·스토어·client 같은 순수 로직 + DevExtreme을 쓰지 않는
// 순수 .vue 컴포넌트(StarRating 등)를 @vue/test-utils로 마운트해 검증한다.
// DevExtreme 컴포넌트 마운트는 jsdom에서 불안정해 여전히 제외한다.
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom', // localStorage·window.location + 컴포넌트 마운트 DOM
    include: ['src/**/*.test.js'],
  },
});
