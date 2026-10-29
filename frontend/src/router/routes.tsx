// Rutas de la app; cada historia agrega las suyas como hijas del layout.
import { createBrowserRouter, Navigate, type RouteObject } from 'react-router';
import { AdminRecalculationPage } from '../pages/AdminRecalculationPage';
import { AdminSimulationPage } from '../pages/AdminSimulationPage';
import { AdminStrategiesPage } from '../pages/AdminStrategiesPage';
import { AppLayout } from '../pages/AppLayout';
import { BoardPage } from '../pages/BoardPage';
import { LockerRoomPage } from '../pages/LockerRoomPage';
import { LoginPage } from '../pages/LoginPage';
import { MarketPage } from '../pages/MarketPage';
import { MyOrdersPage } from '../pages/MyOrdersPage';
import { PlayerPage } from '../pages/PlayerPage';
import { PortfolioPage } from '../pages/PortfolioPage';
import { RankingPage } from '../pages/RankingPage';
import { RegisterPage } from '../pages/RegisterPage';
import { TransactionsPage } from '../pages/TransactionsPage';
import { RequireRole } from './RequireRole';
import { RequireSession } from './RequireSession';

// Se exportan sueltas para que los tests armen un router en memoria con las mismas rutas
export const appRoutes: RouteObject[] = [
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/pizarra" replace /> },
      { path: 'pizarra', element: <BoardPage /> },
      { path: 'jugadores/:id', element: <PlayerPage /> },
      { path: 'ranking', element: <RankingPage /> },
      // El listado del mercado es público, como en la API (FR-047)
      { path: 'mercado', element: <MarketPage /> },
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
      // Mercado, solo USER: un visitante va a /login y el administrador ve el aviso
      {
        path: 'portfolio',
        element: (
          <RequireRole role="USER">
            <PortfolioPage />
          </RequireRole>
        ),
      },
      {
        path: 'operaciones',
        element: (
          <RequireRole role="USER">
            <TransactionsPage />
          </RequireRole>
        ),
      },
      {
        path: 'ordenes',
        element: (
          <RequireRole role="USER">
            <MyOrdersPage />
          </RequireRole>
        ),
      },
      // Administración, solo ADMIN: un visitante va a /login y un usuario común ve el aviso
      { path: 'admin', element: <Navigate to="/admin/estrategias" replace /> },
      {
        path: 'admin/estrategias',
        element: (
          <RequireRole>
            <AdminStrategiesPage />
          </RequireRole>
        ),
      },
      {
        path: 'admin/recalculo',
        element: (
          <RequireRole>
            <AdminRecalculationPage />
          </RequireRole>
        ),
      },
      {
        path: 'admin/simulacion',
        element: (
          <RequireRole>
            <AdminSimulationPage />
          </RequireRole>
        ),
      },
      { path: '*', element: <Navigate to="/pizarra" replace /> },
    ],
  },
];

export const router = createBrowserRouter(appRoutes);
