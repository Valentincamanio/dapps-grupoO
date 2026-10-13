// Formato de números para mostrar en pantalla

const balanceFormatter = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

// Saldo con dos decimales en formato argentino, p. ej. 1.000,00
export function formatBalance(balance: number): string {
  return balanceFormatter.format(balance);
}
