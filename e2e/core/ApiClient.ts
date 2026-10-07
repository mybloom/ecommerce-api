import { APIRequestContext, APIResponse, test } from '@playwright/test';

/** commerce-api 공통 응답 형식 (ApiResponse) */
export type ApiBody<T> = {
  meta: { result: 'SUCCESS' | 'FAIL'; errorCode: string | null; message: string | null };
  data: T | null;
};

/** 성공 여부와 관계없이 상태 코드와 본문을 함께 돌려준다. 실패 응답을 검증하는 테스트도 같은 메서드를 쓴다 */
export type ApiResult<T> = {
  status: number;
  body: ApiBody<T>;
};

type RequestOptions = {
  memberId?: number;
  headers?: Record<string, string>;
  data?: unknown;
  params?: Record<string, string | number | boolean>;
};

/** 리포트에 남기기 전에 가리는 요청 필드 */
const MASKED_FIELDS = ['cardNo', 'password'];

/**
 * baseURL 은 playwright.config.ts 에서 request fixture 에 넣는다.
 * 여기서는 X-MEMBER-ID 헤더 주입, 공통 응답 파싱, 요청·응답 기록을 맡는다.
 */
export class ApiClient {
  constructor(private readonly request: APIRequestContext) {}

  get<T>(url: string, options: RequestOptions = {}): Promise<ApiResult<T>> {
    return this.send<T>('GET', url, options);
  }

  post<T>(url: string, options: RequestOptions = {}): Promise<ApiResult<T>> {
    return this.send<T>('POST', url, options);
  }

  /** 성공 응답의 data 를 꺼낸다. 테스트 데이터를 만드는 단계처럼 실패하면 더 진행할 수 없을 때 쓴다 */
  static dataOf<T>(result: ApiResult<T>): T {
    if (result.status !== 200 || result.body.meta.result !== 'SUCCESS' || result.body.data === null) {
      throw new Error(
        `API 호출이 성공하지 않았습니다. status=${result.status} errorCode=${result.body.meta.errorCode} message=${result.body.meta.message}`,
      );
    }
    return result.body.data;
  }

  private async send<T>(method: 'GET' | 'POST', url: string, options: RequestOptions): Promise<ApiResult<T>> {
    const headers: Record<string, string> = { ...options.headers };
    if (options.memberId !== undefined) {
      headers['X-MEMBER-ID'] = String(options.memberId);
    }

    const response = await this.request.fetch(url, {
      method,
      headers,
      data: options.data,
      params: options.params,
    });
    const body = await parseBody<T>(response);
    await attachExchange(method, url, headers, options.data, response, body);
    return { status: response.status(), body };
  }
}

async function parseBody<T>(response: APIResponse): Promise<ApiBody<T>> {
  const text = await response.text();
  try {
    return JSON.parse(text) as ApiBody<T>;
  } catch {
    // 앱의 공통 응답 형식이 아닌 경우 (게이트웨이 오류 페이지 등). 원문을 message 에 담아 실패 원인을 남긴다
    return { meta: { result: 'FAIL', errorCode: null, message: text }, data: null };
  }
}

/** 요청·응답을 리포트 첨부로 남긴다. 카드번호·비밀번호는 가린다 */
async function attachExchange(
  method: string,
  url: string,
  headers: Record<string, string>,
  requestData: unknown,
  response: APIResponse,
  body: unknown,
): Promise<void> {
  const exchange = {
    request: { method, url, headers, body: mask(requestData) },
    response: { status: response.status(), body },
  };
  await test.info().attach(`${method} ${url} → ${response.status()}`, {
    body: JSON.stringify(exchange, null, 2),
    contentType: 'application/json',
  });
}

function mask(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(mask);
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, v]) => [key, MASKED_FIELDS.includes(key) ? '****' : mask(v)]),
    );
  }
  return value;
}
