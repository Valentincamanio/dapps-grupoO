// Formato de números para mostrar en pantalla

const balanceFormatter = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

// Saldo con dos decimales en formato argentino, p. ej. 1.000,00
export function formatBalance(balance: number): string {
  return balanceFormatter.format(balance);
}

// Hasta dos iniciales para el avatar, p. ej. "Kylian Mbappé" da "KM"
export function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join('');
}

// Tono de 0 a 359 que sale siempre igual para el mismo texto: un equipo tiene un solo color
export function hueOf(text: string): number {
  return Array.from(text).reduce((hue, char) => (hue * 31 + (char.codePointAt(0) ?? 0)) % 360, 7);
}
