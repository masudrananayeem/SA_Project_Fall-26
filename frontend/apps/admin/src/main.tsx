import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { ToastProvider } from '@meterflow/shared';
import '@meterflow/shared/styles.css';
import './app.css';
import App from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ToastProvider>
      <App />
    </ToastProvider>
  </StrictMode>,
);
