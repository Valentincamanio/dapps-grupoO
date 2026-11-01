// Pestañas de la administración: estrategias, recálculo, simulación, potenciados y auditoría.
import type { ElementType } from 'react';
import { ADMIN_SECTIONS } from '../utils/sections';
import { SectionNav } from './SectionNav';

export type AdminSection = 'strategies' | 'recalculation' | 'simulation' | 'boosts' | 'audit';

interface AdminNavProps {
  current: AdminSection;
  // Por defecto un <a>; la página pasa el Link del router
  LinkComponent?: ElementType;
}

const CURRENT_PATHS: Record<AdminSection, string> = {
  strategies: '/admin/estrategias',
  recalculation: '/admin/recalculo',
  simulation: '/admin/simulacion',
  boosts: '/admin/potenciados',
  audit: '/admin/auditoria',
};

export function AdminNav({ current, LinkComponent }: AdminNavProps) {
  return (
    <SectionNav
      label="administración"
      sections={ADMIN_SECTIONS}
      current={CURRENT_PATHS[current]}
      LinkComponent={LinkComponent}
    />
  );
}
