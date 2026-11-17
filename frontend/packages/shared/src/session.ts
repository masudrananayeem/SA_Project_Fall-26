import { useCallback, useEffect, useState } from 'react';
import { loadSession, saveSession, setUnauthorizedHandler, type Session } from './api';

/** Session for one app (admin or client). Stored per browser tab in sessionStorage. */
export function useSession(app: string) {
  const [session, setSession] = useState<Session | null>(() => loadSession(app));
  const [expired, setExpired] = useState(false);

  const login = useCallback(
    (s: Session) => {
      saveSession(app, s);
      setExpired(false);
      setSession(s);
    },
    [app],
  );

  const logout = useCallback(() => {
    saveSession(app, null);
    setSession(null);
  }, [app]);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      saveSession(app, null);
      setExpired(true);
      setSession(null);
    });
  }, [app]);

  return { session, login, logout, expired };
}

/** Minimal hash router: "#/invoices/123" -> ["invoices", "123"]. */
export function useHashRoute(defaultRoute: string) {
  const read = () => {
    const h = window.location.hash.replace(/^#\/?/, '');
    return h || defaultRoute;
  };
  const [route, setRoute] = useState(read);
  useEffect(() => {
    const on = () => setRoute(read());
    window.addEventListener('hashchange', on);
    return () => window.removeEventListener('hashchange', on);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const navigate = useCallback((to: string) => {
    window.location.hash = `/${to}`;
  }, []);
  const parts = route.split('/').filter(Boolean);
  return { route, page: parts[0] || defaultRoute, param: parts[1], navigate };
}
