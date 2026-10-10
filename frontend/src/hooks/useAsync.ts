// Ejecuta una tarea asíncrona cancelable y expone su estado (research R-7)
import { useCallback, useEffect, useState, type DependencyList } from 'react';
import { ApiRequestError } from '../services/httpClient';
import type { AsyncState } from '../types/board';

export type UseAsyncResult<T> = AsyncState<T> & { retry: () => void };

function sameDeps(a: DependencyList, b: DependencyList): boolean {
  return a.length === b.length && a.every((value, i) => Object.is(value, b[i]));
}

export function useAsync<T>(
  task: (signal: AbortSignal) => Promise<T>,
  deps: DependencyList,
): UseAsyncResult<T> {
  // Cambiar este contador relanza la tarea sin cambiar las dependencias
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<AsyncState<T>>({ status: 'loading' });
  const [tracked, setTracked] = useState({ deps, attempt });

  // Si cambian las dependencias o se reintenta, vuelve a "cargando" en el mismo render
  if (!sameDeps(tracked.deps, deps) || tracked.attempt !== attempt) {
    setTracked({ deps, attempt });
    setState({ status: 'loading' });
  }

  useEffect(() => {
    const controller = new AbortController();

    task(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setState({ status: 'success', data });
      })
      .catch((error: unknown) => {
        // Un pedido cancelado no es un error para la pantalla
        if (controller.signal.aborted) return;
        if (error instanceof ApiRequestError) {
          setState({ status: 'error', error: error.apiError });
        } else {
          throw error;
        }
      });

    return () => controller.abort();
    // task cambia en cada render; las dependencias reales las pasa quien llama
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, attempt]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);

  return { ...state, retry };
}
