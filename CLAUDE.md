# 환경 (반드시 이 버전으로 고정)

- OS: Rocky Linux 9 (dnf 기반)
- Node.js: 24.13.0  (nvm로 설치, 시스템 패키지 금지)
- pnpm: v10  (corepack로 활성화, npm/yarn 사용 금지)
- JDK: 25 GA (Oracle, /opt/java/jdk-25)
- Redis: 7.4.7  (소스 빌드)
- Nginx: 1.27.1 (소스 빌드)
- Vue: 3.3.0 / rsbuild: 1.7.3 / devextreme: 23.1.15 / tailwindcss: 4.0.14
- Oracle DB: 19c @ (DB_HOST, 계정은 .env 참고)

# 규칙
- sudo가 필요한 명령은 실행하지 말고 "제안"만 할 것. 내가 직접 확인 후 실행한다.
- 위 버전과 다른 걸 설치하려 하지 말 것.
