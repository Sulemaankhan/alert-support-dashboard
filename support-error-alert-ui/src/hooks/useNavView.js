import { useEffect, useState } from 'react';
import { NAV_SECTION } from '../constants/nav.js';

/** @typedef {'home' | 'json' | 'email'} NavView */

function resolveViewFromHash() {
  const hash = window.location.hash;
  if (hash === `#${NAV_SECTION.EMAIL_ALERT}`) return 'email';
  if (hash === `#${NAV_SECTION.JSON_ALERT}`) return 'json';
  return 'home';
}

/** @returns {NavView} */
export function useNavView() {
  const [view, setView] = useState(() => resolveViewFromHash());

  useEffect(() => {
    const onHashChange = () => setView(resolveViewFromHash());
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  return view;
}
