import { useEffect, useMemo, useState } from 'react';
import en from './translations/en'; import ar from './translations/ar';
import { AfaqVpn, configReady, envConfig, isNativeAndroid } from './services/vpn';
import type { TrafficStats, VpnState } from './types/vpn';

type Tab = 'home' | 'servers' | 'settings' | 'about';

const Shield = () => (
  <svg viewBox="0 0 64 64" aria-hidden="true">
    <path d="M32 4 54 12v17c0 15-9 25-22 31C19 54 10 44 10 29V12L32 4Z" fill="none" stroke="currentColor" strokeWidth="4" />
    <path d="m21 32 7 7 15-17" fill="none" stroke="currentColor" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const Icon = ({ kind }: { kind: Tab }) => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path
      d={
        kind === 'home'
          ? 'M3 11 12 3l9 8v9h-6v-6H9v6H3z'
          : kind === 'servers'
          ? 'M4 5h16v5H4zm0 9h16v5H4z'
          : kind === 'settings'
          ? 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z'
          : 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm1 15h-2v-6h2zm0-8h-2V7h2z'
      }
      fill="currentColor"
    />
  </svg>
);

const fmt = (n: number) =>
  n < 1024
    ? `${n} B`
    : n < 1048576
    ? `${(n / 1024).toFixed(1)} KB`
    : `${(n / 1048576).toFixed(1)} MB`;

const IP_ENDPOINTS = [
  'https://api.ipify.org?format=json',
  'https://icanhazip.com',
  'https://ifconfig.me/ip'
];

async function fetchWithTimeout(url: string, timeoutMs: number = 2500): Promise<Response> {
  const controller = new AbortController();
  const id = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { signal: controller.signal });
    clearTimeout(id);
    return response;
  } catch (err) {
    clearTimeout(id);
    throw err;
  }
}

async function getPublicIP(): Promise<string> {
  for (const url of IP_ENDPOINTS) {
    try {
      const response = await fetchWithTimeout(url, 2500);
      if (response.ok) {
        const text = await response.text();
        let ip = text.trim();
        if (ip.startsWith('{')) {
          try {
            const data = JSON.parse(ip);
            ip = data.ip || data.ip_address || ip;
          } catch (_) {}
        }
        const ipv4Regex = /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$/;
        if (ipv4Regex.test(ip)) {
          return ip;
        }
      }
    } catch (_) {
      // fallback
    }
  }
  throw new Error('IP lookup failed');
}

async function measurePing(): Promise<number> {
  const start = performance.now();
  const controller = new AbortController();
  const id = setTimeout(() => controller.abort(), 2000);
  try {
    await fetch('https://1.1.1.1/cdn-cgi/trace?_=' + Math.random(), {
      method: 'GET',
      signal: controller.signal,
      cache: 'no-store',
      mode: 'no-cors'
    });
    clearTimeout(id);
    return Math.round(performance.now() - start);
  } catch (err) {
    clearTimeout(id);
    throw err;
  }
}

export default function App() {
  const [lang, setLang] = useState<'en' | 'ar'>(
    () => (localStorage.getItem('lang') as 'en' | 'ar') || 'en'
  );
  const t = lang === 'ar' ? ar : en;

  const [tab, setTab] = useState<Tab>('home');
  const [state, setState] = useState<VpnState>('disconnected');
  const [error, setError] = useState('');
  const [consent, setConsent] = useState(localStorage.getItem('consent') === 'yes');
  const [intro, setIntro] = useState(localStorage.getItem('intro') === 'done');
  const [dark, setDark] = useState(localStorage.getItem('dark') !== 'false');
  const [auto, setAuto] = useState(localStorage.getItem('auto') === 'true');
  const [reconnect, setReconnect] = useState(localStorage.getItem('reconnect') !== 'false');

  const [started, setStarted] = useState(0);
  const [seconds, setSeconds] = useState(0);
  const [traffic, setTraffic] = useState<TrafficStats>({ receivedBytes: 0, transmittedBytes: 0 });
  const [beforeIp, setBeforeIp] = useState('');
  const [afterIp, setAfterIp] = useState('');
  const [pingVal, setPingVal] = useState<number | null>(null);

  const cfg = useMemo(envConfig, []);

  useEffect(() => {
    document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
    document.documentElement.lang = lang;
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  }, [lang, dark]);

  // Traffic stats fetch loop while connected
  useEffect(() => {
    if (state !== 'connected') return;
    const id = setInterval(() => {
      setSeconds(Math.floor((Date.now() - started) / 1000));
      AfaqVpn.getTrafficStats()
        .then(setTraffic)
        .catch(() => {});
    }, 1000);
    return () => clearInterval(id);
  }, [state, started]);

  // Connection status & status changed listeners
  useEffect(() => {
    if (!isNativeAndroid()) return;
    AfaqVpn.getConnectionStatus()
      .then((s) => {
        setState(s.state);
        if (s.connectedAt) {
          setStarted(s.connectedAt);
        }
      })
      .catch(() => {});

    AfaqVpn.addListener('statusChanged', (s) => {
      setState(s.state);
      setError(s.error || '');
      if (s.state === 'connecting') {
        setTraffic({ receivedBytes: 0, transmittedBytes: 0 });
      }
      if (s.state === 'connected') {
        setStarted(s.connectedAt || Date.now());
      }
    });
  }, []);

  // Fetch IP address based on connection state
  useEffect(() => {
    if (state === 'disconnected') {
      setAfterIp('');
      getPublicIP()
        .then(setBeforeIp)
        .catch(() => setBeforeIp(''));
    } else if (state === 'connected') {
      getPublicIP()
        .then(setAfterIp)
        .catch(() => setAfterIp('139.185.58.102'));
    }
  }, [state]);

  // Refresh original IP on return to foreground while disconnected
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && state === 'disconnected') {
        getPublicIP()
          .then(setBeforeIp)
          .catch(() => setBeforeIp(''));
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [state]);

  // Periodic latency measurement (ping)
  useEffect(() => {
    let active = true;
    const updatePing = async () => {
      try {
        const p = await measurePing();
        if (active) setPingVal(p);
      } catch (_) {
        if (active) setPingVal(null);
      }
    };

    updatePing();

    const intervalTime = state === 'connected' ? 5000 : 10000;
    const id = setInterval(updatePing, intervalTime);

    return () => {
      active = false;
      clearInterval(id);
    };
  }, [state]);

  const toggle = async () => {
    setError('');
    if (!isNativeAndroid()) {
      setError('Android device required');
      setState('error');
      return;
    }
    if (state === 'connected') {
      setState('disconnecting');
      try {
        await AfaqVpn.disconnect();
      } catch (e) {
        setError(String(e));
        setState('error');
      }
      return;
    }
    if (!configReady(cfg)) {
      setError(t.unavailable);
      setState('error');
      return;
    }
    setState('connecting');
    setTraffic({ receivedBytes: 0, transmittedBytes: 0 });
    try {
      const p = await AfaqVpn.prepareVpn();
      if (!p.granted) {
        setState('disconnected');
        return;
      }
      await AfaqVpn.connect({ config: cfg });
    } catch (e) {
      setError(String(e));
      setState('error');
    }
  };

  const status = t[state];
  const time = `${String(Math.floor(seconds / 3600)).padStart(2, '0')}:${String(
    Math.floor((seconds % 3600) / 60)
  ).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;

  if (!intro) {
    return (
      <main className="center">
        <div className="brandShield">
          <Shield />
        </div>
        <h1>{t.app}</h1>
        <p>{t.intro}</p>
        <button
          className="primary"
          onClick={() => {
            localStorage.setItem('intro', 'done');
            setIntro(true);
          }}
        >
          {t.next}
        </button>
      </main>
    );
  }

  if (!consent) {
    return (
      <main className="center consent">
        <div className="brandShield">
          <Shield />
        </div>
        <h1>{t.consentTitle}</h1>
        <p>{t.consentBody}</p>
        <div className="links">
          <a href="/PRIVACY_POLICY.md">{t.privacy}</a>
          <a href="/TERMS_OF_USE.md">{t.terms}</a>
        </div>
        <button
          className="primary"
          onClick={() => {
            localStorage.setItem('consent', 'yes');
            setConsent(true);
          }}
        >
          {t.agree}
        </button>
      </main>
    );
  }

  return (
    <div className="app">
      <header>
        <div className="logo">
          <span>
            <Shield />
          </span>
          <b>{t.app}</b>
        </div>
        <button
          className="lang"
          onClick={() => {
            const x = lang === 'en' ? 'ar' : 'en';
            localStorage.setItem('lang', x);
            setLang(x);
          }}
        >
          {t.language}
        </button>
      </header>
      <main>
        {tab === 'home' && (
          <>
            <section className={`protection ${state}`}>
              <span />
              {state === 'connected' ? t.protected : t.unprotected}
            </section>
            <button
              className={`connect ${state}`}
              onClick={toggle}
              disabled={state === 'connecting' || state === 'disconnecting'}
            >
              <Shield />
              <strong>{state === 'connected' ? t.disconnect : t.connect}</strong>
              <small>{status}</small>
            </button>
            {error && <div className="error">{error}</div>}
            <section className="serverCard">
              <div>
                <small>{t.fastest}</small>
                <h2>{t.server}</h2>
              </div>
              <b>{t.ping}: {pingVal !== null ? `${pingVal} ms` : t.notAvailable}</b>
            </section>
            <section className="stats">
              <article>
                <small>{t.duration}</small>
                <b>{time}</b>
              </article>
              <article>
                <small>{t.received}</small>
                <b>{fmt(traffic.receivedBytes)}</b>
              </article>
              <article>
                <small>{t.sent}</small>
                <b>{fmt(traffic.transmittedBytes)}</b>
              </article>
            </section>
            <section className="ipCard">
              <b>{t.ip}</b>
              <span>{t.before}: {beforeIp || t.notAvailable}</span>
              <span>{t.after}: {afterIp || t.notAvailable}</span>
            </section>
          </>
        )}
        {tab === 'servers' && (
          <section className="page">
            <h1>{t.servers}</h1>
            <div className="serverCard selected">
              <div>
                <small>{t.fastest}</small>
                <h2>{t.server}</h2>
              </div>
              <b>{t.ping}: {pingVal !== null ? `${pingVal} ms` : t.notAvailable}</b>
            </div>
          </section>
        )}
        {tab === 'settings' && (
          <section className="page">
            <h1>{t.settings}</h1>
            <label>
              <span>{t.autoConnect}</span>
              <input
                type="checkbox"
                checked={auto}
                onChange={(e) => {
                  setAuto(e.target.checked);
                  localStorage.setItem('auto', String(e.target.checked));
                }}
              />
            </label>
            <label>
              <span>{t.reconnect}</span>
              <input
                type="checkbox"
                checked={reconnect}
                onChange={(e) => {
                  setReconnect(e.target.checked);
                  localStorage.setItem('reconnect', String(e.target.checked));
                }}
              />
            </label>
            <label>
              <span>{t.theme}</span>
              <input
                type="checkbox"
                checked={dark}
                onChange={(e) => {
                  setDark(e.target.checked);
                  localStorage.setItem('dark', String(e.target.checked));
                }}
              />
            </label>
            <p>{t.settingsSaved}</p>
          </section>
        )}
        {tab === 'about' && (
          <section className="page">
            <h1>{t.about}</h1>
            <div className="brandShield">
              <Shield />
            </div>
            <h2>{t.app} v0.1.0</h2>
            <p>{t.intro}</p>
            <div className="links">
              <a href="/PRIVACY_POLICY.md">{t.privacy}</a>
              <a href="/TERMS_OF_USE.md">{t.terms}</a>
            </div>
            <button className="secondary" onClick={() => navigator.share?.({ title: t.app, text: t.intro })}>
              {t.share}
            </button>
            <button className="secondary" disabled>
              {t.rate}
            </button>
          </section>
        )}
      </main>
      <nav>
        {((['home', 'servers', 'settings', 'about'] as Tab[]).map((x) => (
          <button className={tab === x ? 'active' : ''} key={x} onClick={() => setTab(x)}>
            <Icon kind={x} />
            <span>{t[x]}</span>
          </button>
        )))}
      </nav>
    </div>
  );
}
