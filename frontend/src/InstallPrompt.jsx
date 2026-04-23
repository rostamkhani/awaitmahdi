import React, { useEffect, useRef, useState } from 'react';
import './InstallPrompt.css';

const DISMISS_KEY = 'pwa_install_dismissed_at';
const INSTALLED_KEY = 'pwa_installed';
const DISMISS_COOLDOWN_MS = 24 * 60 * 60 * 1000; // 1 day
const INITIAL_DELAY_MS = 10_000; // 10 seconds

const detectIOS = () => {
  if (typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent || navigator.vendor || '';
  const iOSClassic = /iPad|iPhone|iPod/.test(ua) && !window.MSStream;
  // iPadOS 13+ reports as Mac — use touch points to detect.
  const iPadOS = navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1;
  return iOSClassic || iPadOS;
};

const isStandalone = () => {
  if (typeof window === 'undefined') return false;
  const mq = window.matchMedia?.('(display-mode: standalone)').matches;
  const iosStandalone = window.navigator.standalone === true;
  return Boolean(mq || iosStandalone);
};

const wasDismissedRecently = () => {
  try {
    const ts = Number(localStorage.getItem(DISMISS_KEY) || 0);
    if (!ts) return false;
    return Date.now() - ts < DISMISS_COOLDOWN_MS;
  } catch {
    return false;
  }
};

const markDismissed = () => {
  try { localStorage.setItem(DISMISS_KEY, String(Date.now())); } catch { /* storage unavailable */ }
};

const markInstalled = () => {
  try { localStorage.setItem(INSTALLED_KEY, '1'); } catch { /* storage unavailable */ }
};

const isAlreadyInstalled = () => {
  try { return localStorage.getItem(INSTALLED_KEY) === '1'; } catch { return false; }
};

export default function InstallPrompt() {
  const [visible, setVisible] = useState(false);
  const [isIOS, setIsIOS] = useState(false);

  const deferredPromptRef = useRef(null);
  const timerRef = useRef(null);
  const iosRef = useRef(false);
  const disabledRef = useRef(false);

  useEffect(() => {
    if (isStandalone() || isAlreadyInstalled() || wasDismissedRecently()) {
      disabledRef.current = true;
      return;
    }

    const ios = detectIOS();
    iosRef.current = ios;
    setIsIOS(ios);

    const tryShow = () => {
      if (disabledRef.current) return;
      if (timerRef.current) return;
      timerRef.current = setTimeout(() => {
        timerRef.current = null;
        if (disabledRef.current) return;
        if (isStandalone() || wasDismissedRecently()) return;
        if (deferredPromptRef.current || iosRef.current) {
          setVisible(true);
        }
      }, INITIAL_DELAY_MS);
    };

    const onBeforeInstall = (e) => {
      e.preventDefault();
      deferredPromptRef.current = e;
      tryShow();
    };

    const onInstalled = () => {
      markInstalled();
      disabledRef.current = true;
      deferredPromptRef.current = null;
      setVisible(false);
    };

    const onInteract = () => {
      tryShow();
      window.removeEventListener('scroll', onInteract);
      window.removeEventListener('click', onInteract);
      window.removeEventListener('keydown', onInteract);
      window.removeEventListener('touchstart', onInteract);
    };

    window.addEventListener('beforeinstallprompt', onBeforeInstall);
    window.addEventListener('appinstalled', onInstalled);
    window.addEventListener('scroll', onInteract, { passive: true });
    window.addEventListener('click', onInteract);
    window.addEventListener('keydown', onInteract);
    window.addEventListener('touchstart', onInteract, { passive: true });

    // On iOS beforeinstallprompt never fires. Start timer unconditionally (interaction or delay).
    if (ios) tryShow();

    return () => {
      window.removeEventListener('beforeinstallprompt', onBeforeInstall);
      window.removeEventListener('appinstalled', onInstalled);
      window.removeEventListener('scroll', onInteract);
      window.removeEventListener('click', onInteract);
      window.removeEventListener('keydown', onInteract);
      window.removeEventListener('touchstart', onInteract);
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, []);

  const handleInstall = async () => {
    const evt = deferredPromptRef.current;
    if (!evt) return;
    try {
      evt.prompt();
      const choice = await evt.userChoice;
      if (choice?.outcome === 'accepted') {
        markInstalled();
        disabledRef.current = true;
      } else {
        markDismissed();
      }
    } catch {
      /* ignore */
    } finally {
      deferredPromptRef.current = null;
      setVisible(false);
    }
  };

  const handleDismiss = () => {
    markDismissed();
    setVisible(false);
  };

  if (!visible) return null;

  return (
    <div className="pwa-install-overlay" role="dialog" aria-live="polite" aria-label="نصب برنامه">
      <div className="pwa-install-card">
        <button
          type="button"
          className="pwa-install-close"
          onClick={handleDismiss}
          aria-label="بستن"
        >
          ×
        </button>

        <div className="pwa-install-icon">
          <img src="/icons/icon-192.png" alt="منتظر مهدی" />
        </div>

        <div className="pwa-install-text">
          <div className="pwa-install-title">منتظر مهدی</div>
          <div className="pwa-install-subtitle">
            برای تجربه بهتر، اپ را نصب کنید
          </div>
        </div>

        {isIOS ? (
          <div className="pwa-install-ios">
            <p className="pwa-install-ios-hint">
              برای نصب روی آیفون/آیپد:
            </p>
            <ol className="pwa-install-ios-steps">
              <li>
                <span>روی دکمه اشتراک‌گذاری بزنید</span>
                <span className="ios-icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
                    <path d="M12 2l4 4h-3v9h-2V6H8l4-4zM5 12h2v7h10v-7h2v7a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-7z"/>
                  </svg>
                </span>
              </li>
              <li>
                <span>گزینه «Add to Home Screen»</span>
                <span className="ios-icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
                    <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
                  </svg>
                </span>
              </li>
              <li><span>روی «Add» بزنید</span></li>
            </ol>
            <button
              type="button"
              className="pwa-install-btn pwa-install-btn-secondary"
              onClick={handleDismiss}
            >
              متوجه شدم
            </button>
          </div>
        ) : (
          <div className="pwa-install-actions">
            <button
              type="button"
              className="pwa-install-btn pwa-install-btn-primary"
              onClick={handleInstall}
            >
              نصب
            </button>
            <button
              type="button"
              className="pwa-install-btn pwa-install-btn-secondary"
              onClick={handleDismiss}
            >
              بعداً
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
