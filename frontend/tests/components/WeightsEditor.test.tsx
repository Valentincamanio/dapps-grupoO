// Historia 8 de la 007: el editor de un juego de pesos, con la suma en vivo (FR-066).
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { WeightsEditor } from '../../src/components/WeightsEditor';
import { METRICS, type WeightsText } from '../../src/types/quotes';
import { metricLabel } from '../../src/utils/metrics';
import { emptyWeightsText } from '../../src/utils/weights';

function Harness({ initial = emptyWeightsText() }: { initial?: WeightsText }) {
  const [value, setValue] = useState(initial);
  return <WeightsEditor legend="juego por defecto" value={value} onChange={setValue} />;
}

describe('WeightsEditor', () => {
  it('muestra las 19 métricas con su etiqueta dentro del juego', () => {
    render(<Harness />);

    expect(screen.getByRole('group', { name: 'juego por defecto' })).toBeInTheDocument();
    for (const metric of METRICS) {
      expect(screen.getByLabelText(metricLabel(metric))).toBeInTheDocument();
    }
    expect(screen.getAllByRole('textbox')).toHaveLength(METRICS.length);
  });

  it('suma en vivo, exacta, con coma o punto', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    expect(screen.getByText('suma: 0,00')).toBeInTheDocument();
    await user.type(screen.getByLabelText('goles'), '0,1');
    expect(screen.getByText('suma: 0,10')).toBeInTheDocument();
    await user.type(screen.getByLabelText('asistencias'), '0.2');
    await user.type(screen.getByLabelText('tiros'), '0,7');

    expect(screen.getByText('suma: 1,00')).toBeInTheDocument();
    expect(screen.queryByText(/faltan|sobran/)).toBeNull();
  });

  it('avisa cuánto falta o sobra para 1,00', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.type(screen.getByLabelText('goles'), '0,95');
    expect(screen.getByText('faltan 0,05 para llegar a 1,00')).toBeInTheDocument();

    await user.type(screen.getByLabelText('asistencias'), '0,1');
    expect(screen.getByText('suma: 1,05')).toBeInTheDocument();
    expect(screen.getByText('sobran 0,05: tiene que dar 1,00')).toBeInTheDocument();
  });

  it('marca el campo con un peso inválido y lo deja fuera de la suma', async () => {
    const user = userEvent.setup();
    render(<Harness />);

    await user.type(screen.getByLabelText('goles'), '0,125');
    await user.type(screen.getByLabelText('asistencias'), '-1');

    expect(screen.getByLabelText('goles')).toHaveAccessibleDescription('admite a lo sumo dos decimales');
    expect(screen.getByLabelText('asistencias')).toHaveAccessibleDescription('no puede ser negativo');
    expect(screen.getByText('suma: 0,00')).toBeInTheDocument();
  });

  it('parte de los pesos que recibe', () => {
    render(<Harness initial={{ ...emptyWeightsText(), GOALS: '0,5', RATING: '0,5' }} />);

    expect(screen.getByLabelText('goles')).toHaveValue('0,5');
    expect(screen.getByLabelText('calificación del partido')).toHaveValue('0,5');
    expect(screen.getByText('suma: 1,00')).toBeInTheDocument();
  });
});
