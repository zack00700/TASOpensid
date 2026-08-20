# FactFront / TAS — conventions projet (frontend Vue 3 + Tailwind)

Lis ce fichier avant toute modification. Ces règles priment sur tes habitudes par défaut.
Stack : Vue 3 + TypeScript, Vite, Tailwind, axios, i18n (vue-i18n).

## Avant de dire qu'une tâche est terminée

Lance et vérifie la sortie — n'affirme jamais « ça marche » sans preuve :

```bash
npm run typecheck     # vue-tsc --noEmit : zéro erreur de type
npm run test          # vitest run : la suite doit passer
npm run build         # doit builder
```

## Design system — UTILISER `src/components/ui/`, ne pas créer de système parallèle

Le projet a un design system maison dans **`src/components/ui/`** : `Button.vue`, `AppButton.vue`,
`DataTable.vue`, `Modal.vue`, `Input.vue`, `Select.vue`, `StatusBadge.vue`, `PageHeader.vue`,
`ThirdPartyAutocomplete.vue`, `VesselAutocomplete.vue`, `ConfirmDialog.vue`, etc. (voir
`src/components/ui/index.ts`).

➡️ Réutilise ces composants. **Ne crée pas** de second système de boutons/cartes (classes CSS
globales type `.tide-btn`, `.tide-glass`, nouvelles palettes parallèles) qui doublent `ui/`. Si
un composant manque, ajoute-le **dans `ui/`** plutôt qu'en CSS global ailleurs.

- **Tailwind** : utilise les tokens existants (`rounded-btn`, `rounded-card`, palette `brand`).
  Ne modifie pas la valeur d'un token partagé (`borderRadius.btn`, `borderRadius.card`, polices)
  sans en mesurer l'impact : ça change l'apparence de **tous** les composants `ui/` d'un coup.
- Pas de refonte visuelle globale (changement de palette de marque, de police display) sans
  validation préalable du propriétaire. Une refonte ≠ un fix de recette : sépare-les.
- Pas de `@import url(...)` de polices Google en runtime dans `index.css` (perf/CSP/offline) —
  self-host si une police est nécessaire.

## Navigation / Sidebar — respecter les choix d'organisation

`SidebarMenu.vue` reflète des décisions délibérées :
- L'entrée de menu **« Events » est volontairement masquée** (la route `/events` reste câblée,
  mais on ne l'expose pas dans le menu). **Ne la ré-ajoute pas.**
- **« Event Config », « ISO Codes », « Container Archetypes » vivent sous Configuration**, pas
  sous Opérations. Ne déplace pas ces entrées sans accord.
- Conserve la persistance localStorage de l'état des sections si tu touches la Sidebar.
- N'ajoute pas de bloc UI (carte, bouton) qui émet un event sans listener côté récepteur :
  vérifie que le handler existe réellement, sinon ne l'ajoute pas (pas de feature morte).

## i18n — tout texte affiché passe par `$t`

Le projet n'a qu'un seul fichier de locale : **`src/locales/en.json`**. Toutes les chaînes
visibles par l'utilisateur passent par `$t('clé')` / `t('clé')`. **N'écris pas de texte en dur**
dans les templates (et surtout pas en français : il n'y a pas de `fr.json`, ça produit une UI
bilingue). Ajoute les nouvelles clés dans `en.json`, ne supprime pas de clé existante.

## Réseau — passer par l'axios partagé + la couche service

Utilise l'instance axios partagée **`src/plugin/axios.ts`** (baseURL + Bearer token Entra) via la
couche `src/services/*.ts`. **N'appelle jamais `fetch('/api/...')` en brut** : ça contourne la
baseURL et le token → 401/404 ou échec silencieux (ex. un delete qui ne supprime rien côté
serveur). Si un service manque, ajoute une méthode dans le `*Service.ts` adéquat.

## Comportements établis — ne pas régresser

- Ne transforme pas une vue lecture seule en édition (ex. « Voir » d'un contrat doit rester
  distinct de « Éditer ») sans accord explicite.
- Avant de changer le flux d'un wizard (étape d'ouverture en édition, etc.), vérifie l'attendu
  côté recette.

## Workflow

- Branche dédiée, commits petits et thématiques (1 sujet = 1 commit), PR. Sépare un fix de
  recette d'une refonte UI : ils ne vont pas dans la même PR.
- Référence l'item de recette (ex. `TC-14`) dans le message de commit quand applicable.
- Ignore / ne commit pas les fichiers dupliqués macOS (`Xxx 2.vue`, `package 2.json`).
