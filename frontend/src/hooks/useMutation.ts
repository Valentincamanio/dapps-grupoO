// Ejecuta un comando (crear, editar, activar, recalcular, simular) cuando se lo pide con run, no
// por dependencias como useAsync. Si se vuelve a correr antes de que termine, gana el último.
import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiRequestError } from '../services/httpClient';
import type { ApiError } from '../types/api';

export type MutationStatus = 'idle' | 'running' | 'success' | 'error';

const CONFLICT_STATUS = 409;

export interface UseMutationResult<A, T> {
  status: MutationStatus;
  // El resultado del último comando que salió bien; se conserva al volver a correr o fallar
  data: T | undefined;
  // El error del último comando, con el mensaje y el status del backend
  error: ApiError | undefined;
  // El backend rechazó con 409 (recálculo o sincronización en curso, nombre repetido...)
  isConflict: boolean;
  // Devuelve el resultado, o undefined si falló (el error queda en el estado)
  run: (args: A) => Promise<T | undefined>;
  // Vuelve a 'idle' sin resultado ni error
  reset: () => void;
}

export function useMutation<A = void, T = unknown>(command: (args: A) => Promise<T>): UseMutationResult<A, T> {
  const [status, setStatus] = useState<MutationStatus>('idle');
  const [data, setData] = useState<T | undefined>(undefined);
  const [error, setError] = useState<ApiError | undefined>(undefined);

  // El comando más reciente, sin que run cambie en cada render
  const commandRef = useRef(command);
  useEffect(() => {
    commandRef.current = command;
  });

  // Número de la última corrida: solo esa actualiza el estado
  const latestRun = useRef(0);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const run = useCallback(async (args: A): Promise<T | undefined> => {
    const current = ++latestRun.current;
    const isLatest = () => mounted.current && current === latestRun.current;
    setStatus('running');
    setError(undefined);
    try {
      const result = await commandRef.current(args);
      if (isLatest()) {
        setData(result);
        setStatus('success');
      }
      return result;
    } catch (failure) {
      if (!(failure instanceof ApiRequestError)) throw failure;
      if (isLatest()) {
        setError(failure.apiError);
        setStatus('error');
      }
      return undefined;
    }
  }, []);

  const reset = useCallback(() => {
    latestRun.current++;
    setStatus('idle');
    setData(undefined);
    setError(undefined);
  }, []);

  return { status, data, error, isConflict: error?.status === CONFLICT_STATUS, run, reset };
}
