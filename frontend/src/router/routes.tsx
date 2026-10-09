// Rutas de la app; cada historia agrega las suyas como hijas del layout.
import { createBrowserRouter, type RouteObject } from 'react-router';
import { AppLayout } from '../pages/AppLayout';

// Se exportan sueltas para que los tests armen un router en memoria con las mismas rutas
export const appRoutes: RouteObject[] = [
  {
    path: '/',
    element: <AppLayout />,
    children: [],
  },
];

export const router = createBrowserRouter(appRoutes);
