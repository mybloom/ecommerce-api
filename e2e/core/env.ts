/**
 * 접속 주소는 모두 환경변수로 바꿀 수 있다. 기본값은 docker/system-test-compose.yml 이 호스트에 여는 주소다.
 */
export const env = {
  /** commerce-api */
  apiBaseUrl: process.env.API_BASE_URL ?? 'http://localhost:18080',
  /** PG 대역(WireMock). 매핑 등록·요청 검증은 `${pgBaseUrl}/__admin` */
  pgBaseUrl: process.env.PG_BASE_URL ?? 'http://localhost:18090',
  /** PG 대신 테스트가 직접 콜백을 보내는 경로 (계획서 D-3) */
  pgCallbackPath: process.env.PG_CALLBACK_PATH ?? '/api/v1/payments/pg/callback',
  db: {
    host: process.env.DB_HOST ?? 'localhost',
    port: Number(process.env.DB_PORT ?? 13306),
    user: process.env.DB_USER ?? 'application',
    password: process.env.DB_PASSWORD ?? 'application',
    database: process.env.DB_NAME ?? 'loopers',
  },
};
