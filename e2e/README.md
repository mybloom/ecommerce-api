# e2e — 결제 시스템 테스트 (Playwright)

배포된 commerce-api 를 HTTP 로만 검증하는 블랙박스 테스트다. 앱 코드는 import 하지 않는다.
브라우저는 쓰지 않고 Playwright 의 `request`(APIRequestContext)로 API 만 호출한다.
레벨 구분과 결정 사항은 `docs/test/02-테스트-계획서.md` 3절, 케이스는 `docs/test/03-테스트-설계.md` 에 있다.

## 설치와 실행

```bash
# 1. 환경 기동 (저장소 루트에서)
docker compose -f docker/system-test-compose.yml up -d --build --wait

# 2. 테스트
cd e2e
npm ci
npx playwright test                    # 전체
npx playwright test --grep @smoke      # 태그로 고르기
npx playwright test --grep TC-PAY-040  # 케이스 ID 로 고르기
npx playwright show-report             # HTML 리포트 (playwright-report/)
```

주소는 환경변수로 바꾼다. 기본값은 compose 가 호스트에 여는 주소다.

| 변수 | 기본값 | 용도 |
|---|---|---|
| `API_BASE_URL` | `http://localhost:18080` | commerce-api |
| `PG_BASE_URL` | `http://localhost:18090` | PG 대역(WireMock). admin API 는 `/__admin` |
| `PG_CALLBACK_PATH` | `/api/v1/payments/pg/callback` | 테스트가 직접 보내는 콜백 경로 |
| `DB_HOST` `DB_PORT` `DB_USER` `DB_PASSWORD` `DB_NAME` | `localhost` `13306` `application` `application` `loopers` | 시드·상태 확인용 MySQL |

## 폴더 구조

```
core/         HTTP 래퍼(ApiClient), 환경변수(env), fixture, 테스트 카드 번호
clients/      엔드포인트별 클라이언트와 요청·응답 타입 (Member, Point, Product, Order, Payment)
db/           seed.ts — 브랜드·상품 생성 (생성 API 가 없어서, Q-E2)
              ReadOnlyDb.ts — 주문·결제 상태 확인 (주문 상세 API 가 없어서, D-2)
tests/        테스트
global-setup.ts  실행마다 한 번: WireMock 매핑 초기화, 공용 상품 생성
```

- 요청·응답은 매 호출마다 리포트에 첨부로 남는다. `cardNo`, `password` 는 `****` 로 가린다.
  trace 는 본문을 그대로 담아 카드번호가 평문으로 남으므로 켜지 않는다.
- PG 접수 응답은 `docker/system-test/wiremock/mappings/` 의 고정 매핑이 돌려준다 (D-1).
- `ReadOnlyDb` 는 세션을 읽기 전용으로 연다. 쓰기는 `db/seed.ts` 만 한다.

## 태그 규칙

테스트 제목에 케이스 ID 와 태그를 넣는다. `--grep` 은 제목을 본다.

```
TC-PAY-040 <기대 동작 한 문장> @smoke @regression @REQ-PAY-03
```

| 태그 | 의미 |
|---|---|
| `TC-PAY-nnn` | 설계서 케이스 ID. 제목 맨 앞 |
| `@smoke` | 핵심 흐름. 배포 직후 먼저 돌린다 |
| `@regression` | 회귀 묶음. 모든 시스템 테스트에 붙인다 |
| `@REQ-PAY-nn` | 요구사항. 여러 개면 모두 붙인다 |

## 새 테스트 추가

1. 설계서에서 위치가 PW 인 케이스를 고른다.
2. `tests/` 의 관련 파일(없으면 새 `*.spec.ts`)에 `import { test, expect } from '../core/fixtures'` 로 작성한다.
3. 데이터는 fixture 로 받는다.
   - `member` — 테스트마다 새 회원. 회원을 테스트끼리 공유하지 않는다
   - `sharedProduct` — 공용 상품. 재고를 검증하지 않는 테스트용
   - `seedProduct()` — 전용 상품. **재고를 정확히 검증하는 테스트는 반드시 이것을 쓴다** (공용 상품은 병렬 테스트가 재고를 바꾼다)
   - `db` — 주문·결제 상태 확인
4. 새 엔드포인트가 필요하면 `clients/` 에 메서드와 타입을 추가한다. 필드는 앱의 `*V1Dto` 를 보고 맞춘다.
5. PG 응답을 바꿔야 하는 케이스는 WireMock admin API 로 그 주문(`orderId`)에만 맞는 매핑을 고정 매핑보다 높은 우선순위(`priority` 숫자가 작을수록 우선)로 등록한다. 병렬 테스트와 섞이지 않게 하기 위해서다.
6. 결함을 재현하는 테스트는 수정 전까지 `test.fail()` 로 두고 제목에 이슈 번호를 적는다 (D-4).
