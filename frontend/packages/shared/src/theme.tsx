import { useEffect, useState } from 'react';
import { Moon, Sun } from 'lucide-react';

export type Theme = 'light' | 'dark';
const KEY = 'meterflow.theme';
const listeners = new Set<(t: Theme) => void>();

/** Saved choice, otherwise the operating system preference. */
export function initialTheme(): Theme {
  try {
    const saved = localStorage.getItem(KEY);
    if (saved === 'light' || saved === 'dark') return saved;
  } catch {
    /* storage unavailable */
  }
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function currentTheme(): Theme {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
}

export function applyTheme(theme: Theme, persist = false) {
  document.documentElement.dataset.theme = theme;
  if (persist) {
    try {
      localStorage.setItem(KEY, theme);
    } catch {
      /* ignore */
    }
  }
  listeners.forEach((l) => l(theme));
}

export function useTheme() {
  const [theme, setTheme] = useState<Theme>(() => currentTheme());
  useEffect(() => {
    listeners.add(setTheme);
    return () => {
      listeners.delete(setTheme);
    };
  }, []);
  const toggle = () => applyTheme(theme === 'dark' ? 'light' : 'dark', true);
  return { theme, toggle };
}

export function ThemeToggle({ className = 'icon-btn', label = false }: { className?: string; label?: boolean }) {
  const { theme, toggle } = useTheme();
  const next = theme === 'dark' ? 'light' : 'dark';
  return (
    <button type="button" className={`theme-toggle ${className}`} onClick={toggle} title={`Switch to ${next} mode`} aria-label={`Switch to ${next} mode`}>
      {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
      {label && <span>{theme === 'dark' ? 'Light mode' : 'Dark mode'}</span>}
    </button>
  );
}

/** Colours for Recharts, which needs concrete values rather than CSS classes. */
export function chartPalette(theme: Theme, app: 'admin' | 'client') {
  const dark = theme === 'dark';
  const accent = app === 'admin' ? (dark ? '#818cf8' : '#4f46e5') : dark ? '#34d399' : '#0d7a63';
  const accentSoft = app === 'admin' ? (dark ? '#3b3f7a' : '#c7d2fe') : dark ? '#1f5f4c' : '#a7e3d0';
  return {
    accent,
    accentSoft,
    grid: dark ? '#243049' : '#eaecf0',
    axis: dark ? '#8f9bb3' : '#667085',
    cursor: dark ? 'rgba(255,255,255,0.05)' : '#f2f4f7',
    tooltip: {
      background: dark ? '#182031' : '#ffffff',
      border: `1px solid ${dark ? '#33415c' : '#e4e7ec'}`,
      borderRadius: 8,
      color: dark ? '#e6eaf2' : '#101828',
    },
  };
}
