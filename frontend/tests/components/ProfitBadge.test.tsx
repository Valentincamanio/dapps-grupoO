// Historia 2 de la 008: la ganancia o la pérdida se entiende sin el color (FR-070): signo, texto y
// triángulo, como la variación de una cotización.
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ProfitBadge } from '../../src/components/ProfitBadge';

describe('ProfitBadge', () => {
  it('una ganancia lleva signo más, la rentabilidad, "gana" y el triángulo hacia arriba', () => {
    const { container } = render(<ProfitBadge amount={0.3} percent={3} />);

    expect(container).toHaveTextContent('+0,30 (+3,00 %) gana');
    expect(screen.getByText('▲')).toHaveAttribute('aria-hidden', 'true');
  });

  it('una pérdida lleva signo menos, "pierde" y el triángulo hacia abajo', () => {
    const { container } = render(<ProfitBadge amount={-0.3} percent={-3} />);

    expect(container).toHaveTextContent('-0,30 (-3,00 %) pierde');
    expect(screen.getByText('▼')).toBeInTheDocument();
  });

  it('sin diferencia dice "sin cambios" y no lleva triángulo', () => {
    const { container } = render(<ProfitBadge amount={0} percent={0} />);

    expect(container).toHaveTextContent('0,00 (0,00 %) sin cambios');
    expect(screen.queryByText(/[▲▼]/)).toBeNull();
  });

  it('sin rentabilidad muestra solo el monto', () => {
    const { container } = render(<ProfitBadge amount={0.2} percent={null} />);

    expect(container).toHaveTextContent('+0,20 gana');
    expect(container).not.toHaveTextContent('%');
  });

  it('sin valor muestra un guion que se lee "sin datos"', () => {
    const { container } = render(<ProfitBadge amount={null} />);

    expect(screen.getByText('—')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByText('sin datos')).toBeInTheDocument();
    expect(container).not.toHaveTextContent(/gana|pierde/);
  });
});
