import { ApiClient, ApiResult } from '../core/ApiClient';

export type Gender = 'MALE' | 'FEMALE';

export type RegisterRequest = {
  /** 3~15자 */
  loginId: string;
  email: string;
  /** yyyy-MM-dd */
  birthDate: string;
  gender: Gender;
  /** 4~50자 */
  password: string;
};

export type RegisterResponse = { id: number; loginId: string };

export type MemberProfileResponse = {
  id: number;
  loginId: string;
  email: string;
  birthDate: string;
  gender: Gender;
};

export class MemberClient {
  constructor(private readonly api: ApiClient) {}

  register(request: RegisterRequest): Promise<ApiResult<RegisterResponse>> {
    return this.api.post('/api/v1/members', { data: request });
  }

  getMe(memberId: number): Promise<ApiResult<MemberProfileResponse>> {
    return this.api.get('/api/v1/members/me', { memberId });
  }
}
