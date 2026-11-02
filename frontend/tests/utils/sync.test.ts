import { describe, expect, it } from 'vitest';
import { catalogStatusLabel, countsText, snapshotLabel, statsStatusLabel } from '../../src/utils/sync';

describe('countsText', () => {
  it('muestra los nuevos y los actualizados, en singular o plural', () => {
    const both = countsText({ created: 3, updated: 12, skipped: 0 });
    const singular = countsText({ created: 1, updated: 1, skipped: 4 });

    expect(both).toBe('3 nuevos · 12 actualizados');
    expect(singular).toBe('1 nuevo · 1 actualizado');
  });

  it('omite las partes en cero y sin nada dice "sin cambios"', () => {
    const onlyUpdated = countsText({ created: 0, updated: 7, skipped: 0 });
    const none = countsText({ created: 0, updated: 0, skipped: 2 });

    expect(onlyUpdated).toBe('7 actualizados');
    expect(none).toBe('sin cambios');
  });
});

describe('etiquetas de los informes', () => {
  it('traduce los estados del catálogo, de las estadísticas y del snapshot', () => {
    const labels = [
      catalogStatusLabel('SUCCEEDED'),
      catalogStatusLabel('FAILED'),
      statsStatusLabel('PROCESSED'),
      statsStatusLabel('CUT'),
      statsStatusLabel('NOT_REACHED'),
      snapshotLabel('LOADED'),
      snapshotLabel('MISSING'),
      snapshotLabel('UNREADABLE'),
    ];

    expect(labels).toEqual([
      'sincronizada',
      'falló',
      'procesada',
      'cortada por WhoScored',
      'no se llegó',
      'snapshot cargado',
      'sin snapshot',
      'snapshot ilegible',
    ]);
  });
});
