// Auditoría del mercado (FR-053): cada orden creada, operación y cancelación, de la más nueva a la más
// vieja, con quién la hizo y cómo quedó cada parte. Se filtra por tipo, jugador, usuario (autor o
// cualquiera de las partes) y rango de fechas, y se pagina como el catálogo. Solo se consulta: ningún
// registro se puede cambiar.
import { useEffect, useId, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { AdminNav } from '../components/AdminNav';
import { AuditEntryCard } from '../components/AuditEntryCard';
import { ChalkButton } from '../components/ChalkButton';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { Pager } from '../components/Pager';
import { SearchBox } from '../components/SearchBox';
import { TapeTab } from '../components/TapeTab';
import { useAudit } from '../hooks/useAudit';
import { useCatalog } from '../hooks/useCatalog';
import type { Player } from '../types/api';
import { AUDIT_EVENT_TYPES, type AuditEventType } from '../types/audit';
import { eventTypeFilterLabel } from '../utils/audit';
import styles from './AdminAuditPage.module.css';

const AUDIT_SIZE = 10;
const USER_ID_PATTERN = /^\d+$/;

interface AuditFilters {
  eventType: AuditEventType | null;
  player: Player | null;
  userId: number | null;
  from: string;
  to: string;
}

const NO_FILTERS: AuditFilters = { eventType: null, player: null, userId: null, from: '', to: '' };

export function AdminAuditPage() {
  const [filters, setFilters] = useState<AuditFilters>(NO_FILTERS);
  // Hoja actual, desde 1
  const [sheet, setSheet] = useState(1);
  // El id del usuario se escribe y se aplica con el botón, para no pedir en cada tecla
  const [userText, setUserText] = useState('');
  const userInputId = useId();
  const fromId = useId();
  const toId = useId();
  const catalog = useCatalog();
  const audit = useAudit({
    eventType: filters.eventType ?? undefined,
    playerId: filters.player?.id,
    userId: filters.userId ?? undefined,
    from: filters.from || undefined,
    to: filters.to || undefined,
    page: sheet - 1,
    size: AUDIT_SIZE,
  });

  // El nombre de cada jugador sale del catálogo, que se pide una sola vez
  const { ensureLoaded } = catalog;
  useEffect(() => {
    void ensureLoaded();
  }, [ensureLoaded]);
  const names = new Map(catalog.players.map((player) => [player.id, player.name]));

  function change(next: Partial<AuditFilters>) {
    setFilters((current) => ({ ...current, ...next }));
    setSheet(1);
  }

  function applyUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = userText.trim();
    change({ userId: USER_ID_PATTERN.test(text) && Number(text) > 0 ? Number(text) : null });
  }

  function clearAll() {
    setUserText('');
    setFilters(NO_FILTERS);
    setSheet(1);
  }

  const filtered =
    filters.eventType !== null || filters.player !== null || filters.userId !== null || filters.from !== '' || filters.to !== '';

  function renderEntries() {
    if (audit.status === 'loading' || audit.status === 'idle') return <ChalkNote variant="loading" />;
    if (audit.status === 'error' || !audit.page) {
      return (
        <ChalkNote variant="error" action={{ label: 'reintentar', onClick: audit.retry }}>
          {audit.error?.message ?? 'no se pudo cargar la auditoría'}
        </ChalkNote>
      );
    }
    const { page } = audit;
    if (page.totalElements === 0) {
      // "limpiar filtros" ya está junto a los filtros
      return (
        <ChalkNote variant="info">{filtered ? 'no hay registros con esos filtros' : 'todavía no hay registros'}</ChalkNote>
      );
    }
    return (
      <>
        <p className={styles.total}>
          {page.totalElements === 1 ? '1 registro' : `${page.totalElements} registros`}, del más nuevo al más viejo
        </p>
        <ul className={styles.list} aria-label="registros de auditoría">
          {page.content.map((entry) => (
            <li key={entry.id}>
              <AuditEntryCard entry={entry} playerName={names.get(entry.playerId)} LinkComponent={Link} />
            </li>
          ))}
        </ul>
        <Pager
          sheet={page.page + 1}
          totalSheets={page.totalPages}
          hasPrevious={page.hasPrevious}
          hasNext={page.hasNext}
          onPrevious={() => setSheet(page.page)}
          onNext={() => setSheet(page.page + 2)}
        />
      </>
    );
  }

  return (
    <section className={styles.admin}>
      <ChalkText as="h1" variant="title">
        Administración
      </ChalkText>
      <AdminNav current="audit" LinkComponent={Link} />

      <h2 className={styles.heading}>Auditoría</h2>
      <p className={styles.hint}>
        cada orden creada, operación y cancelación del mercado, con quién la hizo y cómo quedaron el saldo
        y los tokens de cada parte. Solo se consulta: ningún registro se puede cambiar
      </p>

      <div className={styles.filters}>
        <div className={styles.chips} role="group" aria-label="filtrar por tipo">
          <TapeTab label="todos" active={filters.eventType === null} onSelect={() => change({ eventType: null })} />
          {AUDIT_EVENT_TYPES.map((type) => (
            <TapeTab
              key={type}
              label={eventTypeFilterLabel(type)}
              active={filters.eventType === type}
              onSelect={() => change({ eventType: type })}
            />
          ))}
        </div>
        <div className={styles.row}>
          {filters.player ? (
            <p className={styles.chosen}>
              <span>
                jugador <b>{filters.player.name}</b>
              </span>
              <button type="button" className={styles.clear} onClick={() => change({ player: null })}>
                quitar
              </button>
            </p>
          ) : (
            <div className={styles.search}>
              <SearchBox
                players={catalog.players}
                status={catalog.status}
                onFocus={() => void catalog.ensureLoaded()}
                onRetry={() => void catalog.retry()}
                onSelect={(player) => change({ player })}
              />
            </div>
          )}
          <form className={styles.user} onSubmit={applyUser}>
            <label htmlFor={userInputId} className={styles.label}>
              usuario #
            </label>
            <input
              id={userInputId}
              className={styles.input}
              inputMode="numeric"
              placeholder="id"
              value={userText}
              onChange={(event) => setUserText(event.target.value)}
            />
            <ChalkButton type="submit">Filtrar</ChalkButton>
          </form>
        </div>
        <div className={styles.row}>
          <label htmlFor={fromId} className={styles.label}>
            desde
          </label>
          <input
            id={fromId}
            type="date"
            className={styles.input}
            value={filters.from}
            max={filters.to || undefined}
            onChange={(event) => change({ from: event.target.value })}
          />
          <label htmlFor={toId} className={styles.label}>
            hasta
          </label>
          <input
            id={toId}
            type="date"
            className={styles.input}
            value={filters.to}
            min={filters.from || undefined}
            onChange={(event) => change({ to: event.target.value })}
          />
          {filtered ? (
            <button type="button" className={styles.clear} onClick={clearAll}>
              limpiar filtros
            </button>
          ) : null}
        </div>
      </div>

      {renderEntries()}
    </section>
  );
}
