// Administración de estrategias (FR-065): la lista con sus versiones y la activa, el alta, la
// edición (versión nueva) y la activación eligiendo cuándo recotizar. Un rechazo, también un 409
// por un recálculo o una sincronización en curso, muestra el motivo sin cerrar lo que se estaba
// haciendo: el formulario conserva lo escrito y la activación, la opción elegida.
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { AdminNav } from '../components/AdminNav';
import { ChalkButton } from '../components/ChalkButton';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { ReportView } from '../components/ReportView';
import { DEFAULT_REQUOTE, RequoteChoice } from '../components/RequoteChoice';
import { StrategyForm, type StrategyFormValues } from '../components/StrategyForm';
import { StrategyList } from '../components/StrategyList';
import { useMutation } from '../hooks/useMutation';
import { useStrategies } from '../hooks/useStrategies';
import { activateStrategy, createStrategy, createVersion } from '../services/strategyService';
import type { RecalculationReport, RequoteTiming, StrategyDto, StrategyInput } from '../types/quotes';
import styles from './AdminStrategiesPage.module.css';

type Mode =
  | { kind: 'list' }
  | { kind: 'create' }
  | { kind: 'edit'; strategy: StrategyDto }
  | { kind: 'activate'; strategy: StrategyDto };

// Lo que dejó el último cambio guardado
interface Outcome {
  message: string;
  report: RecalculationReport | null;
}

interface CreateArgs {
  name: string;
  input: StrategyInput;
}

interface VersionArgs {
  id: number;
  input: StrategyInput;
  requote?: RequoteTiming;
}

interface ActivateArgs {
  id: number;
  requote: RequoteTiming;
}

const LIST: Mode = { kind: 'list' };

function latestVersion(strategy: StrategyDto) {
  return strategy.versions[strategy.versions.length - 1];
}

export function AdminStrategiesPage() {
  const { status, strategies, error, retry } = useStrategies();
  const [mode, setMode] = useState<Mode>(LIST);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [timing, setTiming] = useState<RequoteTiming>(DEFAULT_REQUOTE);

  const create = useMutation(({ name, input }: CreateArgs) => createStrategy(name, input));
  const edit = useMutation(({ id, input, requote }: VersionArgs) => createVersion(id, input, requote));
  const activate = useMutation(({ id, requote }: ActivateArgs) => activateStrategy(id, requote));

  // Al abrir o cerrar un panel el foco va a su título, así el teclado sigue desde ahí
  // (al cargar la página no se mueve)
  const headingRef = useRef<HTMLHeadingElement>(null);
  const [focusRequest, setFocusRequest] = useState(0);
  useEffect(() => {
    if (focusRequest > 0) headingRef.current?.focus();
  }, [focusRequest]);

  function open(next: Mode) {
    create.reset();
    edit.reset();
    activate.reset();
    setTiming(DEFAULT_REQUOTE);
    setOutcome(null);
    setFocusRequest((request) => request + 1);
    setMode(next);
  }

  // Vuelve a la lista con el resultado del cambio y la pide de nuevo
  function finish(next: Outcome) {
    open(LIST);
    setOutcome(next);
    retry();
  }

  async function submitCreate(values: StrategyFormValues) {
    const created = await create.run({ name: values.name ?? '', input: values.input });
    if (created) finish({ message: `se creó la estrategia ${created.name}, inactiva`, report: null });
  }

  async function submitVersion(strategy: StrategyDto, values: StrategyFormValues) {
    const change = await edit.run({ id: strategy.id, input: values.input, requote: values.requote });
    if (!change) return;
    const version = latestVersion(change.strategy).version;
    finish({
      message: `se guardó la versión ${version} de ${change.strategy.name}`,
      report: change.requotation,
    });
  }

  async function confirmActivation(strategy: StrategyDto) {
    const change = await activate.run({ id: strategy.id, requote: timing });
    if (!change) return;
    const when =
      timing === 'AHORA'
        ? 'y se recotizó la última semana cerrada'
        : 'y las cotizaciones cambian desde el próximo cierre semanal';
    finish({
      message: `${change.strategy.name} es ahora la estrategia activa ${when}`,
      report: change.requotation,
    });
  }

  function renderList() {
    if (status === 'loading' || status === 'idle') return <ChalkNote variant="loading" />;
    if (status === 'error' || !strategies) {
      return (
        <ChalkNote variant="error" action={{ label: 'reintentar', onClick: retry }}>
          no se pudieron cargar las estrategias{error?.message ? `: ${error.message}` : ''}
        </ChalkNote>
      );
    }
    if (strategies.length === 0) return <ChalkNote variant="info">todavía no hay estrategias</ChalkNote>;
    return (
      <StrategyList
        strategies={strategies}
        onEdit={(strategy) => open({ kind: 'edit', strategy })}
        onActivate={(strategy) => open({ kind: 'activate', strategy })}
      />
    );
  }

  function renderPanel() {
    switch (mode.kind) {
      case 'create':
        return (
          <section className={styles.panel} aria-labelledby="strategy-panel">
            <h2 id="strategy-panel" ref={headingRef} tabIndex={-1} className={styles.heading}>
              Nueva estrategia
            </h2>
            <StrategyForm
              submitting={create.status === 'running'}
              error={create.error?.message}
              onSubmit={(values) => void submitCreate(values)}
              onCancel={() => open(LIST)}
            />
          </section>
        );
      case 'edit': {
        const { strategy } = mode;
        const version = latestVersion(strategy);
        return (
          <section className={styles.panel} aria-labelledby="strategy-panel">
            <h2 id="strategy-panel" ref={headingRef} tabIndex={-1} className={styles.heading}>
              Editar {strategy.name}
            </h2>
            <p className={styles.hint}>
              se guarda como la versión {version.version + 1}, a partir de la versión {version.version}
            </p>
            <StrategyForm
              version={version}
              isActive={strategy.active}
              submitting={edit.status === 'running'}
              error={edit.error?.message}
              onSubmit={(values) => void submitVersion(strategy, values)}
              onCancel={() => open(LIST)}
            />
          </section>
        );
      }
      case 'activate': {
        const { strategy } = mode;
        return (
          <section className={styles.panel} aria-labelledby="strategy-panel">
            <h2 id="strategy-panel" ref={headingRef} tabIndex={-1} className={styles.heading}>
              Activar {strategy.name}
            </h2>
            <p className={styles.hint}>
              pasa a ser la estrategia activa con su versión {latestVersion(strategy).version}
            </p>
            <RequoteChoice
              value={timing}
              onChange={setTiming}
              busy={activate.status === 'running'}
              onConfirm={() => void confirmActivation(strategy)}
              onCancel={() => open(LIST)}
            />
            {activate.error ? <ChalkNote variant="error">{activate.error.message}</ChalkNote> : null}
          </section>
        );
      }
      default:
        return null;
    }
  }

  return (
    <section className={styles.admin}>
      <ChalkText as="h1" variant="title">
        Administración
      </ChalkText>
      <AdminNav current="strategies" LinkComponent={Link} />

      {mode.kind === 'list' ? (
        <>
          <div className={styles.toolbar}>
            <h2 ref={headingRef} tabIndex={-1} className={styles.heading}>
              Estrategias
            </h2>
            <ChalkButton onClick={() => open({ kind: 'create' })}>Nueva estrategia</ChalkButton>
          </div>
          {outcome ? (
            <div className={styles.outcome}>
              <ChalkNote variant="success">{outcome.message}</ChalkNote>
              {outcome.report ? (
                <ReportView report={outcome.report} title="informe de la recotización" />
              ) : null}
            </div>
          ) : null}
          {renderList()}
        </>
      ) : (
        renderPanel()
      )}
    </section>
  );
}
