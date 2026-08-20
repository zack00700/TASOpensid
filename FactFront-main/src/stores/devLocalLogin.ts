/**
 * ┌───────────────────────────────────────────────────────────────────────────┐
 * │  LOCAL À TASOpensid — NE PAS RÉPLIQUER VERS pigch/FactFront               │
 * └───────────────────────────────────────────────────────────────────────────┘
 *
 * Connexion locale par identifiant / mot de passe, pour tester la stack sans
 * compte Azure AD.
 *
 * Pourquoi c'est côté front et pas côté back : `AuthService.login()` refuse
 * inconditionnellement le login local (« Veuillez vous connecter via Azure AD. »),
 * et `CLAUDE_back.md` interdit explicitement d'y toucher, de fabriquer un token
 * côté serveur ou d'ajouter des overrides `%dev.quarkus.oidc.enabled=false`.
 * Ce module ne touche donc à rien de tout ça : il court-circuite uniquement
 * l'écran de connexion du SPA.
 *
 * ⚠️ À utiliser avec `./start.sh --no-auth`, et seulement avec lui.
 * Le jeton produit ici est un marqueur local, pas une credential valide : le
 * back ne peut pas le vérifier. Sans `--no-auth`, la connexion réussit à
 * l'écran puis chaque appel `/api/*` répond 401, l'intercepteur axios déclenche
 * un logout et on retombe sur l'écran de login.
 *
 * Deux verrous empêchent ce code d'exister en production :
 *   1. `import.meta.env.DEV` est remplacé par `false` à la compilation, donc
 *      Vite élimine toute la branche de `npm run build` ;
 *   2. `VITE_DEV_LOCAL_LOGIN` n'est défini que dans `.env.development`, que
 *      Vite ne charge jamais en mode production.
 */

export interface DevLocalUser {
  id: string;
  username: string;
  email?: string;
  fullName?: string;
  roles?: string[];
  role?: string;
}

export interface DevLocalSession {
  token: string;
  user: DevLocalUser;
}

/**
 * Marqueur volontairement non-opaque : il ne ressemble pas à un JWT ni à un
 * UUID, pour qu'il soit immédiatement reconnaissable dans les logs, dans
 * localStorage et dans un header Authorization.
 */
const DEV_TOKEN = 'dev-local-no-auth';

/** Actif seulement en dev, et seulement si le flag est explicitement posé. */
export const isDevLocalLoginEnabled = (): boolean =>
  import.meta.env.DEV && import.meta.env.VITE_DEV_LOCAL_LOGIN === 'true';

function configured(key: string, fallback: string): string {
  const value = import.meta.env[key as keyof ImportMetaEnv];
  return typeof value === 'string' && value.length > 0 ? value : fallback;
}

/**
 * Renvoie une session si les identifiants correspondent à ceux configurés,
 * `null` s'ils ne correspondent pas, et `null` aussi lorsque le module est
 * désactivé — l'appelant enchaîne alors sur l'authentification normale.
 */
export function tryDevLocalLogin(credentials: {
  username: string;
  password: string;
}): DevLocalSession | null {
  if (!isDevLocalLoginEnabled()) return null;

  const expectedUser = configured('VITE_DEV_LOCAL_USERNAME', 'devuser');
  const expectedPass = configured('VITE_DEV_LOCAL_PASSWORD', 'devpass123');

  if (credentials.username !== expectedUser || credentials.password !== expectedPass) {
    return null;
  }

  const roles = configured('VITE_DEV_LOCAL_ROLES', 'ROLE_ADMIN,ROLE_USER')
    .split(',')
    .map((role) => role.trim())
    .filter(Boolean);

  console.warn(
    `[dev-local-login] Connexion locale acceptée pour "${expectedUser}". ` +
      'Le back doit tourner avec ./start.sh --no-auth, sinon les appels /api/* renverront 401.',
  );

  return {
    token: DEV_TOKEN,
    user: {
      id: 'dev-local',
      username: expectedUser,
      email: `${expectedUser}@local.test`,
      fullName: 'Dev local (TASOpensid)',
      roles,
      role: roles.includes('ROLE_ADMIN') ? 'admin' : 'user',
    },
  };
}
