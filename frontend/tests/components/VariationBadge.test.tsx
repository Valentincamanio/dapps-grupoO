import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { VariationBadge } from '../../src/components/VariationBadge';

describe('VariationBadge', () => {
  it('una suba muestra el signo, el texto "sube" y el triángulo hacia arriba', () => {
    const { container } = render(<VariationBadge variation={{ amount: 3, percent: 3 }} />);

    expect(screen.getByText('+3,00 (+3,00 %)')).toBeInTheDocument();
    expect(screen.getByText('sube')).toBeInTheDocument();
    expect(container).toHaveTextContent('▲');
    expect(container).not.toHaveTextContent('▼');
  });

  it('una baja muestra el signo, el texto "baja" y el triángulo hacia abajo', () => {
    const { container } = render(<VariationBadge variation={{ amount: -2, percent: -1.94 }} />);

    expect(screen.getByText('-2,00 (-1,94 %)')).toBeInTheDocument();
    expect(screen.getByText('baja')).toBeInTheDocument();
    expect(container).toHaveTextContent('▼');
    expect(container).not.toHaveTextContent('▲');
  });

  it('sin cambios no muestra triángulo y lo dice con texto', () => {
    const { container } = render(<VariationBadge variation={{ amount: 0, percent: 0 }} />);

    expect(screen.getByText('sin cambios')).toBeInTheDocument();
    expect(container).not.toHaveTextContent(/[▲▼]/);
  });

  it('el triángulo es decorativo: el sentido se entiende por el texto, no por el color', () => {
    const { container } = render(
      <VariationBadge variation={{ amount: 3, percent: 3 }} tone="ink" />,
    );

    const triangle = screen.getByText('▲');

    expect(triangle).toHaveAttribute('aria-hidden', 'true');
    expect(container.textContent).toBe('▲+3,00 (+3,00 %)sube');
  });

  it('sin variación (cotización inicial) no muestra nada', () => {
    const { container } = render(<VariationBadge />);

    expect(container).toBeEmptyDOMElement();
  });
});
