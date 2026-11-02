// Potenciados: el administrador elige 4 jugadores por semana y cuánto se potencia cada uno. Se recorre
// semana por semana: las cerradas quedan como registro; la en curso y las próximas se cambian, y
// guardar la en curso pide una confirmación, porque todos ya la ven en el inicio. Lo que se elige sin
// guardar se conserva por semana mientras se está en la pantalla.
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { AdminNav } from '../components/AdminNav';
import { BoostPicker } from '../components/BoostPicker';
import { BoostSlot, type BoostEntry } from '../components/BoostSlot';
import { ChalkButton } from '../components/ChalkButton';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { useBoostWeek } from '../hooks/useBoostWeek';
import { useCatalog } from '../hooks/useCatalog';
import { useCurrentBoosts } from '../hooks/useCurrentBoosts';
import { useCurrentQuotes } from '../hooks/useCurrentQuotes';
import { useMutation } from '../hooks/useMutation';
import { saveBoostWeek } from '../services/boostService';
import type { League, Player } from '../types/api';
import type { BoostInput, BoostedWeek } from '../types/quotes';
import {
  addWeeks,
  boostCandidates,
  boostWeekRange,
  boostWeekStatusLabel,
  BOOSTS_PER_WEEK,
  MIN_BOOST_PERCENT,
  relativeWeekLabel,
} from '../utils/boosts';
import styles from './AdminBoostsPage.module.css';

// Cuántas semanas se pueden recorrer hacia atrás (registro) y hacia adelante (planificar)
const WEEKS_BACK = 8;
const WEEKS_AHEAD = 4;
// Filas de la lista: con más, conviene buscar
const MAX_PICKER_ROWS = 30;
const SLOTS = Array.from({ length: BOOSTS_PER_WEEK }, (_, index) => index);

interface SaveArgs {
  day: string;
  players: BoostInput[];
}

function entriesOf(week: BoostedWeek): BoostEntry[] {
  return week.players.map(({ playerId, name, position, team, league, percent }) => ({
    player: { id: playerId, name, position, team, league },
    percent,
  }));
}

function sameEntries(a: BoostEntry[], b: BoostEntry[]): boolean {
  return (
    a.length === b.length &&
    a.every((entry, index) => entry.player.id === b[index].player.id && entry.percent === b[index].percent)
  );
}

function statusClass(week: BoostedWeek): string {
  if (week.status === 'CLOSED') return styles.done;
  if (week.players.length === 0) return styles.todo;
  return week.status === 'CURRENT' ? styles.live : styles.saved;
}

function savedMessage(week: BoostedWeek): string {
  if (week.players.length === 0) return 'listo: la semana quedó sin potenciados';
  if (week.status === 'CURRENT') return 'listo: se actualizaron los potenciados de esta semana';
  return `listo: los 4 potenciados rigen en el cierre de la semana del ${boostWeekRange(week.weekStart, week.weekEnd)}`;
}

function BoostWeeks({ currentStart }: { currentStart: string }) {
  const [offset, setOffset] = useState(0);
  const day = addWeeks(currentStart, offset);
  const loaded = useBoostWeek(day);
  const catalog = useCatalog();
  const save = useMutation(({ day: weekDay, players }: SaveArgs) => saveBoostWeek(weekDay, players));
  // Lo elegido sin guardar, por lunes
  const [drafts, setDrafts] = useState<Record<string, BoostEntry[]>>({});
  const [confirming, setConfirming] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [league, setLeague] = useState<League | null>(null);

  const week = loaded.status === 'success' ? loaded.week : undefined;
  const saved = week ? entriesOf(week) : [];
  const draft = drafts[day] ?? saved;
  const editable = week !== undefined && week.status !== 'CLOSED';
  const dirty = !sameEntries(draft, saved);
  const canSave = editable && dirty && (draft.length === BOOSTS_PER_WEEK || draft.length === 0);

  const candidates = boostCandidates(catalog.players, query, league);
  const rows = candidates.slice(0, MAX_PICKER_ROWS);
  const slotQuotes = useCurrentQuotes(draft.map((entry) => entry.player.id));
  const rowQuotes = useCurrentQuotes(editable ? rows.map((player) => player.id) : []);

  // La lista para elegir usa el catálogo completo, que se pide una sola vez
  const { ensureLoaded } = catalog;
  useEffect(() => {
    if (editable) void ensureLoaded();
  }, [editable, ensureLoaded]);

  function changeDraft(next: BoostEntry[]) {
    setMessage(null);
    setDrafts((current) => ({ ...current, [day]: next }));
  }

  function goTo(nextOffset: number) {
    setMessage(null);
    save.reset();
    setOffset(nextOffset);
  }

  function discard() {
    setDrafts((current) => Object.fromEntries(Object.entries(current).filter(([monday]) => monday !== day)));
  }

  async function confirmSave() {
    const result = await save.run({
      day,
      players: draft.map((entry) => ({ playerId: entry.player.id, percent: entry.percent })),
    });
    setConfirming(false);
    if (!result) return;
    discard();
    setMessage(savedMessage(result));
    loaded.retry();
  }

  function requestSave() {
    // La semana en curso ya se ve en el inicio: cambiarla pide confirmación
    if (week?.status === 'CURRENT') {
      setConfirming(true);
    } else {
      void confirmSave();
    }
  }

  function renderWeek() {
    if (loaded.status === 'loading' || loaded.status === 'idle') return <ChalkNote variant="loading" />;
    if (loaded.status === 'error' || !week) {
      return (
        <ChalkNote variant="error" action={{ label: 'reintentar', onClick: loaded.retry }}>
          no se pudieron cargar los potenciados de la semana
        </ChalkNote>
      );
    }
    return (
      <>
        {week.status === 'CLOSED' ? (
          <p className={styles.notice}>Semana cerrada: sus potenciados quedan como registro.</p>
        ) : null}
        {week.status === 'CURRENT' ? (
          <p className={styles.notice}>
            Esta semana ya empezó: si cambiás los potenciados, el cambio se ve enseguida en el inicio y rige en el
            cierre del domingo.
          </p>
        ) : null}
        <ul className={styles.slots} aria-label="potenciados de la semana">
          {SLOTS.map((index) => {
            const entry = draft[index] ?? null;
            return (
              <BoostSlot
                key={entry ? entry.player.id : `empty-${index}`}
                index={index}
                entry={entry}
                quote={entry ? slotQuotes.badgeFor(entry.player.id) : undefined}
                editable={editable}
                isNext={editable && index === draft.length}
                onPercentChange={(percent) =>
                  changeDraft(draft.map((current, at) => (at === index ? { ...current, percent } : current)))
                }
                onRemove={() => changeDraft(draft.filter((_, at) => at !== index))}
              />
            );
          })}
        </ul>
        {editable ? (
          <BoostPicker
            query={query}
            onQueryChange={setQuery}
            league={league}
            onLeagueChange={setLeague}
            rows={rows}
            total={candidates.length}
            chosenIds={draft.map((entry) => entry.player.id)}
            onAdd={(player: Player) => changeDraft([...draft, { player, percent: MIN_BOOST_PERCENT }])}
            quoteFor={rowQuotes.badgeFor}
            status={catalog.status}
            onRetry={() => void catalog.retry()}
          />
        ) : null}
      </>
    );
  }

  return (
    <>
      <div className={styles.row}>
        <h2 className={styles.heading}>Potenciados</h2>
        {editable ? (
          <div className={styles.actions}>
            <ChalkButton disabled={!dirty || save.status === 'running'} onClick={discard}>
              Descartar
            </ChalkButton>
            <ChalkButton
              className={styles.primary}
              busy={save.status === 'running'}
              disabled={!canSave}
              title={
                dirty && !canSave ? `elegí ${BOOSTS_PER_WEEK} jugadores, o quitá a todos para vaciar la semana` : undefined
              }
              onClick={requestSave}
            >
              Guardar potenciados
            </ChalkButton>
          </div>
        ) : null}
      </div>
      <p className={styles.hint}>
        Elegí 4 jugadores por semana. En el cierre de esa semana su factor de escala y su volatilidad se
        multiplican por el porcentaje que les pongas (de 200 % a 400 %), así su cotización se mueve más que la
        del resto. Mientras la semana está en curso, todos los ven destacados en el inicio y una recotización
        (cambiar la estrategia con "ahora") también los multiplica.
      </p>

      <div className={styles.weekbar}>
        <button
          type="button"
          className={styles.iconButton}
          aria-label="semana anterior"
          disabled={offset <= -WEEKS_BACK}
          onClick={() => goTo(offset - 1)}
        >
          ‹
        </button>
        <div className={styles.weekInfo}>
          <b>{relativeWeekLabel(offset)}</b>
          <span>
            {week ? `${boostWeekRange(week.weekStart, week.weekEnd)} · del lunes 00:00 al domingo 23:59` : '...'}
          </span>
        </div>
        {week ? (
          <span className={`${styles.status} ${statusClass(week)}`}>
            {boostWeekStatusLabel(week.status, week.players.length > 0)}
          </span>
        ) : null}
        <button
          type="button"
          className={styles.iconButton}
          aria-label="semana siguiente"
          disabled={offset >= WEEKS_AHEAD}
          onClick={() => goTo(offset + 1)}
        >
          ›
        </button>
      </div>

      {message ? <ChalkNote variant="success">{message}</ChalkNote> : null}
      {save.error ? <ChalkNote variant="error">{save.error.message}</ChalkNote> : null}
      {renderWeek()}

      {confirming ? (
        <ConfirmDialog
          title="¿Cambiar los potenciados de esta semana?"
          confirmLabel="Sí, cambiarlos"
          cancelLabel="Volver"
          danger
          busy={save.status === 'running'}
          onConfirm={() => void confirmSave()}
          onCancel={() => setConfirming(false)}
        >
          <p className={styles.warning}>
            La semana ya está en curso y todos ven sus potenciados en el inicio. El cambio se ve enseguida, y en
            el cierre del domingo la cotización de la semana se calcula con los potenciados que queden.
          </p>
          <p className={styles.warning}>
            {draft.length === 0
              ? 'La semana queda sin potenciados.'
              : `Quedan: ${draft.map((entry) => `${entry.player.name} (${entry.percent} %)`).join(', ')}.`}
          </p>
        </ConfirmDialog>
      ) : null}
    </>
  );
}

export function AdminBoostsPage() {
  // La semana en curso es la referencia para recorrer las demás
  const current = useCurrentBoosts();

  function renderContent() {
    if (current.status === 'loading' || current.status === 'idle') return <ChalkNote variant="loading" />;
    if (current.status === 'error' || !current.week) {
      return (
        <ChalkNote variant="error" action={{ label: 'reintentar', onClick: current.retry }}>
          no se pudieron cargar los potenciados
        </ChalkNote>
      );
    }
    return <BoostWeeks currentStart={current.week.weekStart} />;
  }

  return (
    <section className={styles.admin}>
      <ChalkText as="h1" variant="title">
        Administración
      </ChalkText>
      <AdminNav current="boosts" LinkComponent={Link} />
      {renderContent()}
    </section>
  );
}
