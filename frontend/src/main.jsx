import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import InstallPrompt from './InstallPrompt.jsx'
import { registerSW } from 'virtual:pwa-register'

// Register the service worker (autoUpdate).
// Registered only in production build; noop during `vite dev` unless devOptions enabled.
if (typeof window !== 'undefined') {
  try {
    const updateSW = registerSW({
      immediate: true,
      onRegisteredSW(_swUrl, registration) {
        if (!registration) return;
        // Check for updates every hour while app is open
        setInterval(() => {
          registration.update().catch(() => {});
        }, 60 * 60 * 1000);
      },
      onOfflineReady() {
        // App is usable offline
        // console.info('App ready to work offline.');
      },
      onNeedRefresh() {
        // Auto-update — service worker uses skipWaiting; just reload next navigation
        // console.info('New content available; will be applied on next load.');
      },
    });
    // Expose for optional manual triggers
    window.__pwaUpdate = updateSW;
  } catch {
    /* Virtual module not available (e.g. non-build env) */
      onOfflineReady() {},
      onNeedRefresh() {},
    });
    window.__pwaUpdate = updateSW;
  } catch {
    /* Virtual module not available */
  }
}

const hideSplash = () => {
  const el = document.getElementById('app-splash');
  if (!el) return;
  el.classList.add('is-hidden');
  setTimeout(() => el.remove(), 600);
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

// Hide splash once React has committed the first paint
if (typeof window !== 'undefined') {
  if (document.readyState === 'complete') {
    requestAnimationFrame(() => requestAnimationFrame(hideSplash));
  } else {
    window.addEventListener('load', () => {
      requestAnimationFrame(() => requestAnimationFrame(hideSplash));
    });
  }
// Hide the splash as soon as React has committed its first paint — we don't
// wait for window.load (which waits for every image on the page).
if (typeof window !== 'undefined') {
  requestAnimationFrame(() => requestAnimationFrame(hideSplash));
}
