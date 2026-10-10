// Persistencia de la sesión en window.sessionStorage (nunca localStorage)
import type { Session } from '../types/board';

const SESSION_KEY = 'futbolmarket.session';

function isSession(value: unknown): value is Session {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as Session).token === 'string' &&
    typeof (value as Session).expiresAt === 'string'
  );
}

// Devuelve la sesión guardada si sigue vigente; si venció o está corrupta, la borra
export function readSession(): Session | null {
  const raw = window.sessionStorage.getItem(SESSION_KEY);
  if (raw === null) return null;

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    clearSession();
    return null;
  }

  if (!isSession(parsed)) {
    clearSession();
    return null;
  }

  const expiresAt = Date.parse(parsed.expiresAt);
  if (Number.isNaN(expiresAt) || expiresAt <= Date.now()) {
    clearSession();
    return null;
  }

  return { token: parsed.token, expiresAt: parsed.expiresAt };
}

export function writeSession(session: Session): void {
  window.sessionStorage.setItem(
    SESSION_KEY,
    JSON.stringify({ token: session.token, expiresAt: session.expiresAt }),
  );
}

export function clearSession(): void {
  window.sessionStorage.removeItem(SESSION_KEY);
}
