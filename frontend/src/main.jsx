import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import InstallPrompt from './InstallPrompt.jsx'
import { registerSW } from 'virtual:pwa-register'

// Register the service worker (autoUpdate).
if (typeof window !== 'undefined') {
  try {
    const updateSW = registerSW({
      immediate: true,
      onRegisteredSW(_swUrl, registration) {
        if (!registration) return;
        setInterval(() => {
          registration.update().catch(() => {});
        }, 60 * 60 * 1000);
      },
      onOfflineReady() {},
      onNeedRefresh() {},
    });
    window.__pwaUpdate = updateSW;
  } catch {
    /* Virtual module not available */
  }
}

const hideSplash = () => {
  if (typeof window !== 'undefined' && typeof window.__hideAppSplash === 'function') {
    window.__hideAppSplash();
    return;
  }
  const el = document.getElementById('app-splash');
  if (!el) return;
  el.classList.add('is-hidden');
  setTimeout(() => el.remove(), 500);
};

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
    <InstallPrompt />
  </StrictMode>,
)

// Hide the splash as soon as React has committed its first paint — we don't
// wait for window.load (which waits for every image on the page).
if (typeof window !== 'undefined') {
  requestAnimationFrame(() => requestAnimationFrame(hideSplash));
}
