// Rutas de la app; cada historia agrega las suyas como hijas del layout.
import { createBrowserRouter, Navigate, type RouteObject } from 'react-router';
import { AppLayout } from '../pages/AppLayout';
import { BoardPage } from '../pages/BoardPage';
import { LockerRoomPage } from '../pages/LockerRoomPage';
import { LoginPage } from '../pages/LoginPage';
import { RegisterPage } from '../pages/RegisterPage';
import { RequireSession } from './RequireSession';

// Se exportan sueltas para que los tests armen un router en memoria con las mismas rutas
export const appRoutes: RouteObject[] = [
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/pizarra" replace /> },
      { path: 'pizarra', element: <BoardPage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
      {
        path: 'vestuario',
        element: (
          <RequireSession>
            <LockerRoomPage />
          </RequireSession>
        ),
      },
      { path: '*', element: <Navigate to="/pizarra" replace /> },
    ],
  },
];

export const router = createBrowserRouter(appRoutes);
