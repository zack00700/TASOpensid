# FactBack — conventions projet (backend Quarkus / Java)

Lis ce fichier avant toute modification. Ces règles priment sur tes habitudes par défaut.
Stack : Quarkus, MongoDB (Panache), OIDC (Azure AD / Entra), Microsoft Graph.

## Avant de dire qu'une tâche est terminée

Lance et vérifie la sortie — n'affirme jamais « ça marche » sans preuve :

```bash
./mvnw -q compile          # doit compiler
./mvnw -q test             # la suite de tests doit passer
```

Si tu touches une logique couverte par un test (contracts, taxes, items…), exécute au moins
la classe de test concernée et colle la sortie.

## Sécurité & authentification — NE PAS RELÂCHER

L'auth passe par **Azure AD / OIDC**. Le login local par mot de passe est **désactivé
volontairement** (`AuthService` renvoie « Veuillez vous connecter via Azure AD »). Ne le
réactive pas et ne fabrique pas de jeton opaque (`UUID.randomUUID()`) en remplacement d'un JWT.

Dans `application.properties`, les valeurs par défaut (prod) sont **non négociables** :
- `quarkus.oidc.enabled=true`
- `quarkus.security.auth.enabled-in-dev-mode=true`

➡️ N'ajoute **pas** d'override `%dev.quarkus.oidc.enabled=false` ni
`%dev.quarkus.security.auth.enabled-in-dev-mode=false`. Désactiver l'auth, même scopé `%dev`,
ouvre un accès total sans rôle sur toute env qui tournerait avec ce profil. Si tu as besoin de
tester localement sans Entra, demande d'abord — ne le commit pas.

Tout endpoint d'écriture (POST/PUT/PATCH/DELETE) doit être protégé. Garde le `@RolesAllowed`
au niveau classe (ex. `@RolesAllowed("ROLE_ADMIN")` sur les resources admin) ; `@PermitAll`
est réservé aux endpoints `/status` qui ne renvoient qu'un booléen, sans donnée sensible.

## Sémantique des données — ne pas inverser un comportement établi

- **Statut des Items** : par défaut le statut est **dérivé du lifecycle** (`computeStatus`).
  Si tu fais primer une valeur stockée (recette TC-13), assure-toi qu'un `PUT` partiel **ne
  l'efface jamais** : ne fais pas `setStatus(null)` sur l'entité existante avant un merge si le
  payload peut omettre le champ. Utilise un merge JSON (`readerForUpdating`) qui ne touche que
  les champs présents.
- Avant de changer une règle métier (statut, validation, calcul), vérifie qui la consomme
  (front + autres services). Une inversion silencieuse casse les hypothèses ailleurs.

## Patterns à respecter

- **Erreurs** : renvoie un `ErrorResponse` structuré (code + message), pas une stacktrace brute.
  Garde la gestion : `IllegalArgumentException → 400`, `RuntimeException → 500` avec log
  contextuel. Le chemin nominal (201/200) ne doit pas changer.
- **Champs d'audit** : `createdAt` / `updatedAt` / `version` viennent de `EntityBase`. Pour les
  stamper, passe par `BaseEntityService` (`prepareForCreate`/`prepareForUpdate`) plutôt que de
  les poser à la main dans une Resource.
- **Accès données** : utilise Panache (`Entity.find…`, `.update()`). Évite l'accès Mongo brut
  (`mongoDatabase().getCollection("ITEM", Document.class)`) et le hardcode de noms de collection ;
  si tu n'as pas le choix, isole-le et commente pourquoi.
- **Services externes optionnels** (OpenAI/Anthropic, Graph) : ne plante pas au boot si une clé
  manque. Utilise le sentinel `not-configured` et expose un `/status`. Pas de
  `IllegalStateException` dans un `@PostConstruct`.
- Style Java : suis le code alentour (imports en tête, pas de noms pleinement qualifiés inline
  type `jakarta.ws.rs.PATCH` au milieu d'une méthode).

## Workflow

- Travaille sur une branche dédiée, commits petits et thématiques (1 sujet = 1 commit), ouvre
  une PR. Ne pousse pas plusieurs features non liées dans un seul gros commit.
- Référence l'item de recette concerné dans le message de commit (ex. `TC-13`) quand applicable.
