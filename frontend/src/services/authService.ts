// Autenticación y cuenta (specs/001-auth-usuarios)
import type {
  ApiKeyResponse,
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  Profile,
  RegisterRequest,
  RegisterResponse,
} from '../types/api';
import { request } from './httpClient';

export function register(req: RegisterRequest): Promise<RegisterResponse> {
  return request<RegisterResponse>('POST', '/auth/register', { body: req });
}

export function login(req: LoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>('POST', '/auth/login', { body: req });
}

export function getProfile(): Promise<Profile> {
  return request<Profile>('GET', '/auth/me');
}

export function changePassword(req: ChangePasswordRequest): Promise<void> {
  return request<void>('PUT', '/auth/me/password', { body: req });
}

export function regenerateApiKey(): Promise<ApiKeyResponse> {
  return request<ApiKeyResponse>('POST', '/auth/me/api-key');
}
