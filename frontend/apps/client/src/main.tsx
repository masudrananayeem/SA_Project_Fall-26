import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { ToastProvider, applyTheme, initialTheme } from '@meterflow/shared';
import '@meterflow/shared/styles.css';
import './app.css';
import App from './App';

applyTheme(initialTheme());

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ToastProvider>
      <App />
    </ToastProvider>
  </StrictMode>,
);
