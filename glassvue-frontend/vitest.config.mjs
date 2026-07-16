import { defineConfig } from 'vitest/config';

// 프론트 단위 테스트. DevExtreme 컴포넌트 마운트는 제외하고 순수 로직(api 헬퍼·스토어·client) 위주.
export default defineConfig({
  test: {
    environment: 'jsdom', // localStorage·window.location 제공
    include: ['src/**/*.test.js'],
  },
});
