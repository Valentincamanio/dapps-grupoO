// El rayo de los potenciados; es decoración: el texto de al lado dice qué es.
export function BoltIcon({ size = 14 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="currentColor" aria-hidden="true" focusable="false">
      <path d="M13 2 4 14h6l-1 8 9-12h-6z" />
    </svg>
  );
}
