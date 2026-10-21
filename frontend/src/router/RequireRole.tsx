// Guarda de las rutas de administración (research D24): compone RequireSession (un visitante va a
// /login) y, con la sesión autenticada, exige el rol. Sin el rol muestra un aviso de falta de
// permiso con salida a la pizarra y ningún control de administración.
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { ChalkNote } from '../components/ChalkNote';
import { useSession } from '../hooks/useSession';
import type { Role } from '../types/api';
import { RequireSession } from './RequireSession';

interface RequireRoleProps {
  role?: Role;
  children: ReactNode;
}

function RoleCheck({ role, children }: Required<RequireRoleProps>) {
  const { profile } = useSession();

  if (profile?.role !== role) {
    return (
      <ChalkNote variant="error">
        no tenés permiso para entrar a la administración: es solo para administradores.{' '}
        <Link to="/pizarra">volver a la pizarra</Link>
      </ChalkNote>
    );
  }

  return <>{children}</>;
}

export function RequireRole({ role = 'ADMIN', children }: RequireRoleProps) {
  return (
    <RequireSession>
      <RoleCheck role={role}>{children}</RoleCheck>
    </RequireSession>
  );
}
