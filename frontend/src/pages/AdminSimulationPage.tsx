// Simulación (FR-067): calcula sin guardar nada cómo quedarían las cotizaciones con una estrategia
// guardada o con parámetros cargados acá, y las compara con las actuales, con los filtros de liga,
// equipo y posición y la paginación del catálogo.
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { AdminNav } from '../components/AdminNav';
import { ChalkButton } from '../components/ChalkButton';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { Pager } from '../components/Pager';
import { ParametersFields } from '../components/ParametersFields';
import { SimulationTable } from '../components/SimulationTable';
import { TapeTab } from '../components/TapeTab';
import { TeamSelect } from '../components/TeamSelect';
import { useCatalog } from '../hooks/useCatalog';
import { useSimulation, type SimulationSheet } from '../hooks/useSimulation';
import { useStrategies } from '../hooks/useStrategies';
import { LEAGUES, POSITIONS } from '../types/api';
import type { SimulationRequest } from '../types/quotes';
import { pendingReasonLabel } from '../utils/metrics';
import { leagueLabel, positionPlural } from '../utils/positions';
import { weekLabel } from '../utils/quotes';
import { deriveTeams } from '../utils/teams';
import { checkParameters, parametersFormState, parametersFromFormState } from '../utils/weights';
import styles from './AdminSimulationPage.module.css';

type Source = 'saved' | 'parameters';

const SOURCES: { source: Source; label: string }[] = [
  { source: 'saved', label: 'una estrategia guardada' },
  { source: 'parameters', label: 'parámetros nuevos' },
];

// Sin conexión o con el servidor caído tiene sentido reintentar; un 400 o un 404, no
function isRetryable(status: number): boolean {
  return status === 0 || status >= 500;
}

function SimulationSummary({ sheet }: { sheet: SimulationSheet }) {
  return (
    <div className={styles.summary}>
      <p>
        {sheet.strategy
          ? `simulada con ${sheet.strategy.name}, versión ${sheet.strategy.version}`
          : 'simulada con los parámetros cargados'}
        {` · ${sheet.weeksSimulated} ${sheet.weeksSimulated === 1 ? 'semana simulada' : 'semanas simuladas'}`}
      </p>
      {sheet.pendingWeeks.length > 0 ? (
        <p className={styles.pending}>
          sin simular:{' '}
          {sheet.pendingWeeks
            .map((week) => `${weekLabel(week.weekStart)} (${pendingReasonLabel(week.reason)})`)
            .join('; ')}
        </p>
      ) : null}
    </div>
  );
}

export function AdminSimulationPage() {
  const strategies = useStrategies();
  const simulation = useSimulation();
  const catalog = useCatalog();
  const [source, setSource] = useState<Source>('saved');
  // Sin elegir, la activa
  const [chosenId, setChosenId] = useState<number | null>(null);
  const [parameters, setParameters] = useState(() => parametersFormState());

  const strategyId = chosenId ?? strategies.active?.id ?? null;
  const canSimulate = source === 'saved' ? strategyId !== null : checkParameters(parameters).valid;
  const running = simulation.status === 'running';
  const { filters } = simulation;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSimulate || running) return;
    const request: SimulationRequest =
      source === 'saved' && strategyId !== null
        ? { strategyId }
        : { parameters: parametersFromFormState(parameters) };
    void simulation.simulate(request);
  }

  function renderSavedChoice() {
    if (strategies.status === 'loading' || strategies.status === 'idle') {
      return <ChalkNote variant="loading">cargando las estrategias...</ChalkNote>;
    }
    if (strategies.status === 'error' || !strategies.strategies) {
      return (
        <ChalkNote variant="error" action={{ label: 'reintentar', onClick: strategies.retry }}>
          no se pudieron cargar las estrategias
        </ChalkNote>
      );
    }
    return (
      <div className={styles.field}>
        <label htmlFor="simulation-strategy" className={styles.label}>
          estrategia
        </label>
        <select
          id="simulation-strategy"
          className={styles.select}
          value={strategyId ?? ''}
          disabled={running}
          onChange={(event) => setChosenId(Number(event.target.value))}
        >
          {strategies.strategies.map((strategy) => {
            const version = strategy.versions[strategy.versions.length - 1].version;
            return (
              <option key={strategy.id} value={strategy.id}>
                {strategy.name}, versión {version}
                {strategy.active ? ' (activa)' : ''}
              </option>
            );
          })}
        </select>
      </div>
    );
  }

  function renderResult() {
    const { status, sheet, error } = simulation;
    if (status === 'idle') {
      return <ChalkNote variant="info">elegí qué simular y tocá "Simular"</ChalkNote>;
    }
    if (status === 'running') return <ChalkNote variant="loading">simulando...</ChalkNote>;
    if (status === 'error' || !sheet) {
      const retryable = error ? isRetryable(error.status) : true;
      return (
        <ChalkNote
          variant="error"
          action={retryable ? { label: 'reintentar', onClick: simulation.retry } : undefined}
        >
          {error?.message ?? 'no se pudo simular'}
        </ChalkNote>
      );
    }
    if (sheet.isOutOfRange) {
      return (
        <ChalkNote
          variant="info"
          action={{ label: 'volver a la primera hoja', onClick: () => simulation.setPage(1) }}
        >
          esta hoja de la simulación está vacía
        </ChalkNote>
      );
    }
    return (
      <>
        <SimulationSummary sheet={sheet} />
        {sheet.isEmpty ? (
          <ChalkNote variant="info">no hay jugadores con esos filtros</ChalkNote>
        ) : (
          <>
            <SimulationTable rows={sheet.rows} />
            <Pager
              sheet={sheet.sheet}
              totalSheets={sheet.totalSheets}
              hasPrevious={sheet.hasPrevious}
              hasNext={sheet.hasNext}
              onPrevious={() => simulation.setPage(sheet.sheet - 1)}
              onNext={() => simulation.setPage(sheet.sheet + 1)}
            />
          </>
        )}
      </>
    );
  }

  return (
    <section className={styles.admin}>
      <ChalkText as="h1" variant="title">
        Administración
      </ChalkText>
      <AdminNav current="simulation" LinkComponent={Link} />

      <h2 className={styles.heading}>Simulación</h2>
      <p className={styles.hint}>
        calcula cómo quedarían las cotizaciones desde el principio, sin guardar nada ni cambiar la
        estrategia activa
      </p>

      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        <fieldset className={styles.sources} disabled={running}>
          <legend className={styles.legend}>¿qué simular?</legend>
          {SOURCES.map((option) => (
            <label key={option.source} className={styles.source}>
              <input
                type="radio"
                name="simulation-source"
                value={option.source}
                checked={source === option.source}
                onChange={() => setSource(option.source)}
              />
              {option.label}
            </label>
          ))}
        </fieldset>

        {source === 'saved' ? (
          renderSavedChoice()
        ) : (
          <ParametersFields value={parameters} onChange={setParameters} disabled={running} />
        )}

        {source === 'parameters' && !canSimulate ? (
          <p className={styles.blocked}>para simular, cada juego tiene que dar exactamente 1,00</p>
        ) : null}
        <div className={styles.actions}>
          <ChalkButton type="submit" busy={running} disabled={!canSimulate}>
            Simular
          </ChalkButton>
        </div>
      </form>

      <div className={styles.filters}>
        <div className={styles.tabs} role="group" aria-label="filtrar por liga">
          <TapeTab
            label="todas"
            active={filters.league === null}
            onSelect={() => simulation.setLeague(null)}
          />
          {LEAGUES.map((league) => (
            <TapeTab
              key={league}
              label={leagueLabel(league)}
              active={filters.league === league}
              onSelect={() => simulation.setLeague(league)}
            />
          ))}
        </div>
        <div className={styles.tabs} role="group" aria-label="filtrar por posición">
          <TapeTab
            label="todas las posiciones"
            active={filters.position === null}
            onSelect={() => simulation.setPosition(null)}
          />
          {POSITIONS.map((position) => (
            <TapeTab
              key={position}
              label={positionPlural(position)}
              active={filters.position === position}
              onSelect={() => simulation.setPosition(position)}
            />
          ))}
        </div>
        <div className={styles.team}>
          <TeamSelect
            teams={deriveTeams(catalog.players, filters.league)}
            value={filters.team}
            status={catalog.status}
            onOpen={() => void catalog.ensureLoaded()}
            onSelect={simulation.setTeam}
            onRetry={() => void catalog.retry()}
          />
        </div>
      </div>

      <div className={styles.result}>{renderResult()}</div>
    </section>
  );
}
