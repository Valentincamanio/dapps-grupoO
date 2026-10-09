// Rutas de la app; cada historia agrega las suyas como hijas del layout.
import { createBrowserRouter, type RouteObject } from 'react-router';
import { AppLayout } from '../pages/AppLayout';
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
    ],
  },
];

export const router = createBrowserRouter(appRoutes);
