// Sesión de la app (data-model §2, research R-5). El token vive en memoria y en sessionStorage.
import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import type { Profile } from '../types/api';
import type { Session, SessionEndReason } from '../types/board';
import { getProfile, login as loginRequest } from '../services/authService';
import { setAuthHandlers } from '../services/httpClient';
import { clearSession, readSession, writeSession } from '../services/sessionStorage';

export type SessionStatus = 'anonymous' | 'authenticating' | 'authenticated';

export interface SessionContextValue {
  status: SessionStatus;
  session: Session | null;
  profile: Profile | null;
  endReason: SessionEndReason;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  refreshProfile: () => Promise<void>;
}

export const SessionContext = createContext<SessionContextValue | null>(null);

// setTimeout no admite demoras mayores a 2^31 - 1 ms
const MAX_TIMEOUT_MS = 2 ** 31 - 1;

export function SessionProvider({ children }: { children: ReactNode }) {
  // Se lee una sola vez; si hay sesión vigente se queda "autenticando" hasta traer el perfil
  const [initialSession] = useState<Session | null>(() => readSession());
  const [session, setSession] = useState<Session | null>(initialSession);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [status, setStatus] = useState<SessionStatus>(
    initialSession ? 'authenticating' : 'anonymous',
  );
  const [endReason, setEndReason] = useState<SessionEndReason>(null);

  // El cliente HTTP lee el token de acá, así ve el valor nuevo sin esperar un render
  const tokenRef = useRef<string | null>(initialSession?.token ?? null);

  const endSession = useCallback((reason: SessionEndReason) => {
    tokenRef.current = null;
    clearSession();
    setSession(null);
    setProfile(null);
    setStatus('anonymous');
    setEndReason(reason);
  }, []);

  useEffect(() => {
    setAuthHandlers({
      getToken: () => tokenRef.current,
      onUnauthorized: () => endSession('expired'),
    });
  }, [endSession]);

  // Con sesión recuperada de sessionStorage, confirma el token pidiendo el perfil
  useEffect(() => {
    if (!initialSession) return;
    let cancelled = false;
    getProfile()
      .then((loaded) => {
        if (cancelled) return;
        setProfile(loaded);
        setStatus('authenticated');
      })
      .catch(() => {
        // Un 401 ya cerró la sesión por onUnauthorized; otro error la descarta sin aviso
        if (cancelled || tokenRef.current === null) return;
        endSession(null);
      });
    return () => {
      cancelled = true;
    };
  }, [initialSession, endSession]);

  // Expira la sesión al llegar a expiresAt
  const expiresAt = session?.expiresAt;
  useEffect(() => {
    if (!expiresAt) return;
    const remaining = Date.parse(expiresAt) - Date.now();
    const timer = setTimeout(
      () => endSession('expired'),
      Math.min(Math.max(remaining, 0), MAX_TIMEOUT_MS),
    );
    return () => clearTimeout(timer);
  }, [expiresAt, endSession]);

  const login = useCallback(
    async (username: string, password: string) => {
      const response = await loginRequest({ username, password });
      const next: Session = { token: response.token, expiresAt: response.expiresAt };
      tokenRef.current = next.token;
      writeSession(next);
      setSession(next);
      setEndReason(null);
      setStatus('authenticating');
      try {
        setProfile(await getProfile());
        setStatus('authenticated');
      } catch (error) {
        // Si el perfil falla no queda una sesión a medias
        if (tokenRef.current !== null) endSession(null);
        throw error;
      }
    },
    [endSession],
  );

  const logout = useCallback(() => endSession('logout'), [endSession]);

  const refreshProfile = useCallback(async () => {
    setProfile(await getProfile());
  }, []);

  const value = useMemo<SessionContextValue>(
    () => ({ status, session, profile, endReason, login, logout, refreshProfile }),
    [status, session, profile, endReason, login, logout, refreshProfile],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}
