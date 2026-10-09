// Handlers de MSW con las formas de los contratos 001 y 002. Responden en /api/...
import { http, HttpResponse } from 'msw';
import type { ApiError, League, Player, PlayerPage, Position, Profile, Role } from '../../src/types/api';
import { LEAGUES, POSITIONS } from '../../src/types/api';
import { PLAYERS } from './players';

interface MockUser {
  id: number;
  username: string;
  email: string;
  password: string;
  role: Role;
  balance: number;
  apiKey: string;
}

// Usuario y sesión precargados en cada test
export const SEED_USER = {
  username: 'lionel10',
  email: 'lionel@mail.com',
  password: 'campeon2022',
};
export const SEED_TOKEN = 'token-semilla';

const SESSION_DURATION_MS = 60 * 60 * 1000;

let users: MockUser[] = [];
// token -> id de usuario
let tokens = new Map<string, number>();
let nextUserId = 2;
let nextTokenNumber = 1;
let nextKeyNumber = 1;

function newApiKey(): string {
  return `clave-de-api-${nextKeyNumber++}`.padEnd(43, 'x');
}

// Vuelve el estado en memoria al inicial: un usuario con una sesión válida
export function resetMockDb(): void {
  nextUserId = 2;
  nextTokenNumber = 1;
  nextKeyNumber = 1;
  users = [
    {
      id: 1,
      ...SEED_USER,
      role: 'USER',
      balance: 1000,
      apiKey: newApiKey(),
    },
  ];
  tokens = new Map([[SEED_TOKEN, 1]]);
}

resetMockDb();

function apiError(
  status: number,
  error: string,
  message: string,
  path: string,
  violations?: ApiError['violations'],
): Response {
  const body: ApiError = {
    timestamp: new Date().toISOString(),
    status,
    error,
    message,
    path,
    ...(violations ? { violations } : {}),
  };
  return HttpResponse.json(body, { status });
}

function badRequest(path: string, message: string, violations?: ApiError['violations']): Response {
  return apiError(400, 'Bad Request', message, path, violations);
}

function unauthorized(path: string, message: string): Response {
  return apiError(401, 'Unauthorized', message, path);
}

function conflict(path: string, message: string): Response {
  return apiError(409, 'Conflict', message, path);
}

// Usuario del token Bearer, o la respuesta 401 que devuelve el backend
function authenticate(request: Request, path: string): MockUser | Response {
  const header = request.headers.get('Authorization');
  if (!header) {
    return unauthorized(
      path,
      'Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).',
    );
  }
  const userId = tokens.get(header.replace(/^Bearer\s+/, ''));
  const user = users.find((candidate) => candidate.id === userId);
  return user ?? unauthorized(path, 'La credencial es inválida.');
}

function toProfile(user: MockUser): Profile {
  return {
    id: user.id,
    username: user.username,
    email: user.email,
    role: user.role,
    balance: user.balance,
  };
}

// Entero o NaN; sin valor usa el predeterminado
function parseInteger(raw: string | null, fallback: number): number {
  if (raw === null || raw === '') return fallback;
  return /^-?\d+$/.test(raw) ? Number(raw) : Number.NaN;
}

const PLAYERS_PATH = '/players';
const INVALID_PARAMS = 'Los parámetros enviados no son válidos.';
const INVALID_REQUEST = 'El request tiene datos inválidos.';

const playerHandlers = [
  http.get('*/api/players', ({ request }) => {
    const params = new URL(request.url).searchParams;
    const size = parseInteger(params.get('size'), 20);
    const page = parseInteger(params.get('page'), 0);
    const position = params.get('position');
    const league = params.get('league');
    const team = params.get('team');

    const invalid =
      !(size >= 1 && size <= 50) ||
      !(page >= 0) ||
      (position !== null && !POSITIONS.includes(position as Position)) ||
      (league !== null && !LEAGUES.includes(league as League));
    if (invalid) return badRequest(PLAYERS_PATH, INVALID_PARAMS);

    const filtered = PLAYERS.filter(
      (player) =>
        (position === null || player.position === position) &&
        (league === null || player.league === league) &&
        (team === null || player.team.toLowerCase() === team.trim().toLowerCase()),
    );
    const totalPages = Math.ceil(filtered.length / size);
    const body: PlayerPage = {
      content: filtered.slice(page * size, page * size + size),
      page,
      size,
      totalElements: filtered.length,
      totalPages,
      hasPrevious: page > 0 && totalPages > 0,
      hasNext: page + 1 < totalPages,
    };
    return HttpResponse.json(body);
  }),

  http.get('*/api/players/:id', ({ params }) => {
    const raw = String(params.id);
    const path = `${PLAYERS_PATH}/${raw}`;
    if (!/^\d+$/.test(raw)) return badRequest(path, INVALID_PARAMS);
    const player: Player | undefined = PLAYERS.find((candidate) => candidate.id === Number(raw));
    if (!player) {
      return apiError(404, 'Not Found', `No existe un jugador con id ${raw}.`, path);
    }
    return HttpResponse.json(player);
  }),
];

const authHandlers = [
  http.post('*/api/auth/register', async ({ request }) => {
    const path = '/auth/register';
    const body = (await request.json()) as { username?: string; email?: string; password?: string };
    const username = body.username ?? '';
    const email = body.email ?? '';
    const password = body.password ?? '';

    const violations: NonNullable<ApiError['violations']> = [];
    if (username.length < 3 || username.length > 30) {
      violations.push({
        field: 'username',
        message: 'El nombre de usuario debe tener entre 3 y 30 caracteres.',
        rejectedValue: username,
      });
    }
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
      violations.push({ field: 'email', message: 'El correo electrónico no es válido.', rejectedValue: email });
    }
    if (password.length < 8 || password.length > 72) {
      violations.push({ field: 'password', message: 'La contraseña debe tener entre 8 y 72 caracteres.' });
    }
    if (violations.length > 0) return badRequest(path, INVALID_REQUEST, violations);

    const usernameTaken = users.some((user) => user.username.toLowerCase() === username.toLowerCase());
    const emailTaken = users.some((user) => user.email.toLowerCase() === email.toLowerCase());
    if (usernameTaken && emailTaken) {
      return conflict(path, 'El nombre de usuario y el correo electrónico ya están registrados.');
    }
    if (usernameTaken) return conflict(path, 'El nombre de usuario ya está registrado.');
    if (emailTaken) return conflict(path, 'El correo electrónico ya está registrado.');

    const user: MockUser = {
      id: nextUserId++,
      username,
      email,
      password,
      role: 'USER',
      balance: 1000,
      apiKey: newApiKey(),
    };
    users.push(user);
    return HttpResponse.json({ ...toProfile(user), apiKey: user.apiKey }, { status: 201 });
  }),

  http.post('*/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { username?: string; password?: string };
    const user = users.find(
      (candidate) => candidate.username === body.username && candidate.password === body.password,
    );
    if (!user) return unauthorized('/auth/login', 'Credenciales inválidas.');

    const token = `token-${user.id}-${nextTokenNumber++}`;
    tokens.set(token, user.id);
    return HttpResponse.json({
      token,
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + SESSION_DURATION_MS).toISOString(),
    });
  }),

  http.get('*/api/auth/me', ({ request }) => {
    const user = authenticate(request, '/auth/me');
    return user instanceof Response ? user : HttpResponse.json(toProfile(user));
  }),

  http.put('*/api/auth/me/password', async ({ request }) => {
    const path = '/auth/me/password';
    const user = authenticate(request, path);
    if (user instanceof Response) return user;

    const body = (await request.json()) as { currentPassword?: string; newPassword?: string };
    const newPassword = body.newPassword ?? '';
    if (newPassword.length < 8 || newPassword.length > 72) {
      return badRequest(path, INVALID_REQUEST, [
        { field: 'newPassword', message: 'La contraseña debe tener entre 8 y 72 caracteres.' },
      ]);
    }
    if (body.currentPassword !== user.password) {
      return badRequest(path, 'La contraseña actual es incorrecta.');
    }
    if (newPassword === user.password) {
      return badRequest(path, 'La nueva contraseña debe ser distinta de la actual.');
    }
    user.password = newPassword;
    return new HttpResponse(null, { status: 204 });
  }),

  http.post('*/api/auth/me/api-key', ({ request }) => {
    const user = authenticate(request, '/auth/me/api-key');
    if (user instanceof Response) return user;
    user.apiKey = newApiKey();
    return HttpResponse.json({ apiKey: user.apiKey });
  }),
];

export const handlers = [...playerHandlers, ...authHandlers];
