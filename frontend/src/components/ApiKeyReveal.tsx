// Muestra la clave de API una sola vez, con botón para copiarla.
import { useRef, useState } from 'react';
import { ChalkButton } from './ChalkButton';
import { ChalkNote } from './ChalkNote';
import { ChalkText } from './ChalkText';
import styles from './ApiKeyReveal.module.css';

interface ApiKeyRevealProps {
  apiKey: string;
  onContinue: () => void;
  continueLabel?: string;
}

type CopyState = 'idle' | 'copied' | 'failed';

export function ApiKeyReveal({ apiKey, onContinue, continueLabel = 'continuar' }: ApiKeyRevealProps) {
  const [copyState, setCopyState] = useState<CopyState>('idle');
  const inputRef = useRef<HTMLInputElement>(null);

  async function copy() {
    try {
      await navigator.clipboard.writeText(apiKey);
      setCopyState('copied');
    } catch {
      setCopyState('failed');
      inputRef.current?.select();
    }
  }

  return (
    <section className={styles.reveal} aria-labelledby="api-key-title">
      <ChalkText as="h2" variant="heading" className={styles.title}>
        <span id="api-key-title">tu clave de API</span>
      </ChalkText>
      <ChalkText variant="hint">esta clave no se vuelve a mostrar</ChalkText>
      <input
        ref={inputRef}
        className={styles.key}
        type="text"
        readOnly
        value={apiKey}
        aria-label="clave de API"
        onFocus={(event) => event.currentTarget.select()}
      />
      <div className={styles.actions}>
        <ChalkButton onClick={copy}>copiar</ChalkButton>
        <ChalkButton onClick={onContinue}>{continueLabel}</ChalkButton>
      </div>
      {copyState === 'copied' ? <ChalkNote variant="success">clave copiada</ChalkNote> : null}
      {copyState === 'failed' ? (
        <ChalkNote variant="error">
          la copia automática no funcionó, copiá la clave a mano
        </ChalkNote>
      ) : null}
    </section>
  );
}
