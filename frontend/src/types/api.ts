// Tipos de la API copiados de los contratos:
// specs/001-auth-usuarios/contracts/auth-api.yaml y
// specs/002-catalogo-jugadores/contracts/players-api.yaml.
// No se agregan campos que el backend no devuelve.

// Enums como arreglos `as const` para poder validar valores de la dirección contra la misma lista
export const POSITIONS = ['GOALKEEPER', 'DEFENDER', 'MIDFIELDER', 'FORWARD'] as const;
export type Position = (typeof POSITIONS)[number];

export const LEAGUES = ['PREMIER', 'BUNDESLIGA', 'LA_LIGA', 'SERIE_A', 'LIGUE_1'] as const;
export type League = (typeof LEAGUES)[number];

export const ROLES = ['USER', 'ADMIN'] as const;
export type Role = (typeof ROLES)[number];

// Catálogo de jugadores (002)

export interface Player {
  id: number;
  name: string;
  position: Position;
  team: string;
  league: League;
}

export interface PlayerPage {
  content: Player[];
  // Índice base cero
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasPrevious: boolean;
  hasNext: boolean;
}

// Parámetros de GET /players: page >= 0 y 1 <= size <= 50
export interface PlayerQuery {
  league?: League;
  team?: string;
  position?: Position;
  page?: number;
  size?: number;
}

// Autenticación y cuenta (001)

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface RegisterResponse {
  id: number;
  username: string;
  email: string;
  role: Role;
  balance: number;
  // Se muestra una sola vez; vive solo en el estado local de la página de registro
  apiKey: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  tokenType: 'Bearer';
  // ISO 8601
  expiresAt: string;
}

export interface Profile {
  id: number;
  username: string;
  email: string;
  role: Role;
  balance: number;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ApiKeyResponse {
  apiKey: string;
}

// Error común del backend

export interface Violation {
  field: string;
  message: string;
  rejectedValue?: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  violations?: Violation[];
}
