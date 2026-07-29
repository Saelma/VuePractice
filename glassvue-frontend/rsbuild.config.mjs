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
    // 백엔드로 API 프록시. 기본은 운영과 같은 :8080.
    //
    // ⚠ **운영을 안 내리고 dev 백엔드를 붙일 때** 환경변수로 대상을 바꾼다:
    //     API_TARGET=http://127.0.0.1:8084 pnpm dev
    //   메일(비밀번호 재설정·이메일 인증)은 **dev 프로파일에서만 나가므로**(운영은 spring.mail 키가 없다)
    //   브라우저로 메일 흐름을 보려면 dev 백엔드가 필요한데, 운영이 :8080 을 쓰고 있어 포트가 겹친다.
    proxy: {
      '/api': {
        target: process.env.API_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
});
