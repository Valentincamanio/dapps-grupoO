// Lo que falta hasta un instante, actualizado cada medio minuto ("1d 12h 49m").
import { useEffect, useState } from 'react';
import { timeLeft } from '../utils/boosts';

const TICK_MS = 30_000;

export function useCountdown(endsAt: string): string {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), TICK_MS);
    return () => clearInterval(timer);
  }, []);

  return timeLeft(endsAt, now);
}
