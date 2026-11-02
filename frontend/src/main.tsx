import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { SessionProvider } from './context/SessionContext';
import { applyStoredTheme } from './hooks/useTheme';
import { router } from './router/routes';
import './styles/global.css';

applyStoredTheme();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <SessionProvider>
      <RouterProvider router={router} />
    </SessionProvider>
  </StrictMode>,
);
