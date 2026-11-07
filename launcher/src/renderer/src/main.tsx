import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './fonts';
import './styles.css';

// A file dropped outside a drop zone would otherwise make the window navigate to it.
for (const type of ['dragover', 'drop'] as const) window.addEventListener(type, (e) => e.preventDefault());

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
