// Rango de fechas de la evolución: dos fechas con etiqueta y tres rangos rápidos. Devuelve un
// QuoteRange (AAAA-MM-DD, ambos extremos incluidos; sin extremos es todo el historial).
import { useId, useState } from 'react';
import type { QuoteRange } from '../types/quotes';
import styles from './DateRangePicker.module.css';

interface DateRangePickerProps {
  value: QuoteRange;
  onChange: (range: QuoteRange) => void;
  // Hoy en AAAA-MM-DD; por defecto, el día de hoy en Argentina
  today?: string;
}

interface QuickRange {
  label: string;
  // null es todo el historial
  weeks: number | null;
}

export const QUICK_RANGES: QuickRange[] = [
  { label: 'Últimas 8 semanas', weeks: 8 },
  { label: 'Últimas 26 semanas', weeks: 26 },
  { label: 'Todo el historial', weeks: null },
];

const INVALID_RANGE = 'La fecha "desde" no puede ser posterior a la fecha "hasta".';

// Argentina, UTC−3 sin horario de verano
const ARGENTINA_OFFSET_MS = 3 * 60 * 60 * 1000;

function todayInArgentina(): string {
  return new Date(Date.now() - ARGENTINA_OFFSET_MS).toISOString().slice(0, 10);
}

function addDays(day: string, days: number): string {
  const date = new Date(`${day}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

// Las últimas N semanas terminan hoy; todo el historial no tiene extremos
export function quickRange(weeks: number | null, today: string): QuoteRange {
  return weeks === null ? {} : { from: addDays(today, -7 * weeks), to: today };
}

function sameRange(a: QuoteRange, b: QuoteRange): boolean {
  return (a.from ?? '') === (b.from ?? '') && (a.to ?? '') === (b.to ?? '');
}

export function DateRangePicker({ value, onChange, today }: DateRangePickerProps) {
  // Lo escrito en los campos; solo un rango válido llega a onChange
  const [draft, setDraft] = useState<QuoteRange>(value);
  const fromId = useId();
  const toId = useId();
  const errorId = useId();
  const day = today ?? todayInArgentina();

  const invalid = draft.from !== undefined && draft.to !== undefined && draft.from > draft.to;

  function update(next: QuoteRange) {
    setDraft(next);
    const valid = next.from === undefined || next.to === undefined || next.from <= next.to;
    if (valid) onChange(next);
  }

  // Un campo vacío (o una fecha incompleta) es un extremo sin límite
  function setEdge(edge: 'from' | 'to', raw: string) {
    update({ ...draft, [edge]: raw === '' ? undefined : raw });
  }

  return (
    <div className={styles.picker}>
      <div className={styles.fields}>
        <div className={styles.field}>
          <label htmlFor={fromId}>Desde</label>
          <input
            id={fromId}
            type="date"
            value={draft.from ?? ''}
            max={draft.to ?? day}
            aria-invalid={invalid || undefined}
            aria-describedby={invalid ? errorId : undefined}
            onChange={(event) => setEdge('from', event.target.value)}
          />
        </div>
        <div className={styles.field}>
          <label htmlFor={toId}>Hasta</label>
          <input
            id={toId}
            type="date"
            value={draft.to ?? ''}
            min={draft.from}
            aria-invalid={invalid || undefined}
            aria-describedby={invalid ? errorId : undefined}
            onChange={(event) => setEdge('to', event.target.value)}
          />
        </div>
      </div>
      {invalid ? (
        <p id={errorId} className={styles.error} role="alert">
          {INVALID_RANGE}
        </p>
      ) : null}
      <div className={styles.quick} role="group" aria-label="rangos rápidos">
        {QUICK_RANGES.map(({ label, weeks }) => {
          const range = quickRange(weeks, day);
          return (
            <button
              key={label}
              type="button"
              className={styles.quickButton}
              aria-pressed={sameRange(draft, range)}
              onClick={() => update(range)}
            >
              {label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
