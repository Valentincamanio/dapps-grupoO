// Guarda de las rutas por rol (research D24 de la 007 y D27 de la 008): compone RequireSession (un
// visitante va a /login) y, con la sesión autenticada, exige el rol. Sin el rol muestra un aviso de
// falta de permiso con salida a la pizarra y ningún control de la sección. Por defecto exige ADMIN,
// así las rutas de administración no cambian; las del mercado piden USER.
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

// El aviso según lo que pide la ruta: con dos roles, quien no tiene uno tiene el otro
const DENIED_MESSAGES: Record<Role, string> = {
  ADMIN: 'no tenés permiso para entrar a la administración: es solo para administradores.',
  USER: 'no tenés permiso para entrar acá: el administrador no opera en el mercado.',
};

function RoleCheck({ role, children }: Required<RequireRoleProps>) {
  const { profile } = useSession();

  if (profile?.role !== role) {
    return (
      <ChalkNote variant="error">
        {DENIED_MESSAGES[role]} <Link to="/pizarra">volver a la pizarra</Link>
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
