// Pide confirmación antes de salir de la pantalla mientras `active` sea true (research R-10).
import { useEffect } from 'react';
import { useBlocker } from 'react-router';

export interface LeaveGuard {
  // true cuando hay una navegación interna esperando confirmación
  isBlocked: boolean;
  // salir igual
  proceed: () => void;
  // quedarse en la pantalla
  stay: () => void;
}

export function useLeaveGuard(active: boolean): LeaveGuard {
  const blocker = useBlocker(active);

  useEffect(() => {
    if (!active) return;
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [active]);

  return {
    isBlocked: blocker.state === 'blocked',
    proceed: () => blocker.proceed?.(),
    stay: () => blocker.reset?.(),
  };
}
