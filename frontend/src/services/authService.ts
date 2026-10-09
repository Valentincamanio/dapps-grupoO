// Autenticación y cuenta (specs/001-auth-usuarios)
import type {
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
