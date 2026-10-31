// Cómo se agrupan las pantallas: la barra superior muestra los grupos y cada grupo, sus pestañas.

export interface Section {
  to: string;
  label: string;
}

export type TopSection = 'players' | 'team' | 'admin';

export const PLAYER_SECTIONS: Section[] = [
  { to: '/pizarra', label: 'Todos' },
  { to: '/ranking', label: 'Ranking' },
  { to: '/mercado', label: 'Mercado' },
];

export const TEAM_SECTIONS: Section[] = [
  { to: '/portfolio', label: 'Mis jugadores' },
  { to: '/ordenes', label: 'Órdenes' },
  { to: '/operaciones', label: 'Movimientos' },
];

export const ADMIN_SECTIONS: Section[] = [
  { to: '/admin/estrategias', label: 'Estrategias' },
  { to: '/admin/recalculo', label: 'Recálculo' },
  { to: '/admin/simulacion', label: 'Simulación' },
];

const BACK_LABELS: Record<string, string> = {
  '/pizarra': 'volver a la pizarra',
  '/ranking': 'volver al ranking',
  '/mercado': 'volver al mercado',
  '/portfolio': 'volver a mi equipo',
  '/ordenes': 'volver a mis órdenes',
  '/operaciones': 'volver a mis movimientos',
};

// El grupo de una dirección, para marcarlo en la barra; las de cuenta no son de ninguno
export function topSectionOf(pathname: string): TopSection | null {
  if (pathname.startsWith('/admin')) return 'admin';
  if (TEAM_SECTIONS.some(({ to }) => to === pathname)) return 'team';
  if (PLAYER_SECTIONS.some(({ to }) => to === pathname)) return 'players';
  return null;
}

// El texto del enlace que cierra la ficha abierta desde una pantalla
export function backLabelOf(pathname: string): string {
  return BACK_LABELS[pathname] ?? 'volver';
}
