import { defineConfig } from '@rsbuild/core';
import { pluginVue } from '@rsbuild/plugin-vue';

export default defineConfig({
  plugins: [pluginVue()],
  html: {
    template: './index.html',
  },
  source: {
    entry: {
      index: './src/main.js',
    },
  },
  server: {
    port: 3000,
    // 백엔드(Spring Boot :8080)로 API 프록시
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
});
