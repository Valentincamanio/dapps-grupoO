// Rutas de la app; cada historia agrega las suyas como hijas del layout.
import { createBrowserRouter } from 'react-router';
import { AppLayout } from '../pages/AppLayout';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [],
  },
]);
