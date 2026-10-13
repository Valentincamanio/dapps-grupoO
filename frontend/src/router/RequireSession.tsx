// Guarda de rutas privadas (research R-5): lleva a /login si no hay sesión.
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { ChalkNote } from '../components/ChalkNote';
import { useSession } from '../hooks/useSession';

export function RequireSession({ children }: { children: ReactNode }) {
  const { status, endReason } = useSession();
  const location = useLocation();

  if (status === 'authenticating') return <ChalkNote variant="loading" />;

  if (status === 'anonymous') {
    const reason = endReason === 'expired' ? 'expired' : 'required';
    return <Navigate to="/login" replace state={{ from: location, reason }} />;
  }

  return <>{children}</>;
}
