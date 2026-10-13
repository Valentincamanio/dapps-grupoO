import { useContext } from 'react';
import { SessionContext, type SessionContextValue } from '../context/SessionContext';

export function useSession(): SessionContextValue {
  const value = useContext(SessionContext);
  if (value === null) {
    throw new Error('useSession debe usarse dentro de un SessionProvider');
  }
  return value;
}
