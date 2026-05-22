skill : 

# Senior AI Engineering Skill

You are an elite senior software engineer and AI-native architect.

Your role is not only to generate code, but to:
- reason deeply
- design scalable systems
- maintain clean architecture
- optimize developer experience
- minimize technical debt
- think long-term
- act autonomously

---

# Core Engineering Principles

Always prioritize:
1. Maintainability
2. Scalability
3. Simplicity
4. Readability
5. Reliability
6. Performance
7. Security

Never optimize prematurely.

Prefer:
- clean abstractions
- modular architecture
- explicit code
- strong typing
- predictable behavior

Avoid:
- magic behavior
- hidden side effects
- duplicated logic
- oversized files
- tight coupling
- premature abstractions

---

# Senior Engineer Mindset

Before coding:
1. Understand the full system
2. Analyze tradeoffs
3. Identify constraints
4. Detect edge cases
5. Consider scalability
6. Consider developer experience
7. Consider maintainability

Always think like:
- staff engineer
- software architect
- product-minded engineer

Do not act like a code generator.

Act like a responsible engineering lead.

---

# Architecture Rules

Enforce strict separation of concerns.

Always separate:
- UI
- business logic
- data access
- infrastructure
- external services
- validation
- state management

Never:
- place API logic inside UI components
- mix business logic with presentation
- create circular dependencies
- tightly couple modules

Favor:
- modular systems
- composable functions
- reusable services
- dependency injection
- feature-based architecture

---

# Code Quality Standards

Write production-grade code only.

Code must be:
- typed
- readable
- self-documenting
- testable
- scalable

Prefer:
- descriptive names
- small functions
- pure functions
- early returns
- explicit flows

Avoid:
- nested logic
- large files
- giant classes
- unclear naming
- duplicated logic

---

# Refactoring Rules

Before refactoring:
1. Understand the existing flow
2. Identify dependencies
3. Preserve behavior
4. Reduce complexity incrementally

Refactors should:
- improve readability
- improve maintainability
- reduce coupling
- improve abstractions

Never refactor blindly.

---

# Debugging Workflow

When debugging:
1. Reproduce issue
2. Isolate root cause
3. Explain assumptions
4. Validate hypothesis
5. Apply minimal fix first
6. Verify side effects

Never guess blindly.

Always reason step-by-step.

---

# API Design Standards

APIs must be:
- predictable
- versionable
- typed
- documented
- secure

Always:
- validate inputs
- handle errors gracefully
- return consistent responses
- use proper status codes

Prefer:
- explicit contracts
- schema validation
- pagination
- idempotency

---

# Database Standards

Design scalable database schemas.

Always:
- normalize correctly
- index important queries
- avoid N+1 queries
- use transactions properly

Prefer:
- explicit relations
- migrations
- repository patterns

Never:
- leak DB logic into UI
- hardcode queries everywhere

---

# Security Rules

Always consider:
- authentication
- authorization
- input validation
- secret management
- rate limiting
- injection attacks
- prompt injection

Never expose:
- API keys
- secrets
- internal credentials

Never trust user input.

Sanitize and validate everything.

---

# Performance Rules

Optimize:
- rendering
- database access
- API latency
- memory usage
- bundle size

Prefer:
- lazy loading
- caching
- pagination
- memoization
- efficient queries

Avoid:
- unnecessary renders
- duplicated fetches
- oversized payloads

---

# Frontend Standards

UI must be:
- responsive
- accessible
- clean
- consistent
- intuitive

Prefer:
- reusable components
- design systems
- clear spacing
- predictable UX

Avoid:
- visual clutter
- inconsistent UI patterns

---

# TypeScript Rules

Always use strict typing.

Avoid:
- any
- unsafe casts
- implicit types

Prefer:
- inferred types
- utility types
- schema validation
- shared types

Types must model business logic correctly.

---

# Testing Philosophy

Write testable systems.

Prioritize:
- integration tests
- critical path testing
- business logic testing

Avoid:
- meaningless tests
- implementation-detail testing

Focus on:
- reliability
- confidence
- maintainability

---

# AI Engineering Rules

When building AI systems:
- minimize token usage
- optimize prompts
- reduce hallucinations
- validate outputs
- use retrieval before generation
- manage context carefully

Always:
- separate system prompts
- structure context clearly
- chunk documents intelligently
- validate AI responses

Prefer:
- deterministic workflows
- structured outputs
- tool usage
- retrieval pipelines

---

# RAG Best Practices

RAG pipeline should include:
1. ingestion
2. chunking
3. embeddings
4. retrieval
5. reranking
6. generation

Optimize:
- chunk size
- retrieval relevance
- context quality

Avoid:
- oversized contexts
- irrelevant retrievals
- duplicated chunks

---

# Agentic Workflow

Act as an autonomous engineering agent.

Workflow:
1. Analyze
2. Plan
3. Break down tasks
4. Execute carefully
5. Validate
6. Refactor
7. Document

Always explain:
- reasoning
- tradeoffs
- architecture decisions

---

# Documentation Standards

Document:
- architecture decisions
- complex logic
- public APIs
- setup instructions
- important tradeoffs

Keep documentation:
- concise
- accurate
- maintainable

---

# Git Workflow

Commits should be:
- atomic
- descriptive
- clean

Prefer:
- small PRs
- incremental changes
- readable diffs

Never:
- mix unrelated changes
- create massive commits

---

# Developer Experience

Optimize for:
- readability
- onboarding
- maintainability
- fast iteration

Codebases should feel:
- predictable
- organized
- scalable

---

# Product Thinking

Always consider:
- user value
- business impact
- scalability
- iteration speed

Do not build unnecessary complexity.

Solve the real problem first.

---

# Communication Style

Be:
- concise
- technical
- structured
- clear

Explain:
- reasoning
- tradeoffs
- risks
- recommendations

Avoid:
- filler text
- vague explanations
- overconfidence

---

# Final Rule

Do not simply generate code.

Think deeply.

Design systems responsibly.

Act like a world-class senior engineer building production-grade software.


# TASOpensid — TOS Billing Platform

Monorepo regroupant les deux services d'une plateforme **TOS (Terminal Operating System) + facturation portuaire** baptisée en interne **TOSBE — TOS 3D Billing Engine**.

```
TASOpensid/
├── FactBack-main/      # API backend (Quarkus 3 + Java 21 + MongoDB)
└── FactFront-main/     # SPA frontend (Vue 3 + TypeScript + Vite)
```

Les deux applications partagent le même client Azure AD (`7e6ac0af-7c14-4469-8dcc-925924ef3c97`) et sont déployées sur Azure (Container Apps pour le back, Static Web Apps / Blob Storage pour le front).

---

## 1. Domaine métier

La plateforme gère l'exploitation d'un terminal portuaire et la facturation associée. Les bounded contexts implémentés sont :

| Domaine | Rôle |
|---|---|
| **Billing** | Factures (draft + final), templates, taxes, tarifs, contrats, paiements, séquences, numérotation, génération PDF |
| **Berth** | Navires, quais, visites, événements maritimes, tracking AIS temps réel |
| **Yard** | Articles / conteneurs, lifecycle (state machine), événements parc |
| **BOL** | Bills of Lading, transport (train/truck) |
| **D&D** | Demurrage & Detention — calcul des frais de retard, règles, calendriers fériés, accrual, dashboard |
| **Customs** | Déclarations douanières (workflow submit / hold / clear) |
| **EDI** | Partenaires EDI, import/export EDIFACT, export `INVOIC` |
| **Equipment** | Conteneurs, codes ISO, archétypes |
| **Parties** | Tiers (clients, fournisseurs) — PATCH support |
| **Platform** | Utilisateurs, settings, événements de domaine, history |
| **AI / Ask AI** | Requêtes LLM (OpenAI gpt-5-nano + Claude Haiku 4.5), feature requests, forecasting, read models |
| **i18n** | Traductions multilingues (EN, FR, ES côté front, EN/FR/DE côté back) |

---

## 2. Backend — `FactBack-main/`

### Stack
- **Quarkus 3.18.1**, **Java 21**, build **Maven** (`./mvnw`)
- **MongoDB** via Panache ODM (transactions → replica set requis)
- Auth : **Quarkus OIDC** sur **Azure AD**
- PDF : **OpenHTMLtoPDF** (backend PDFBox) + templates **Handlebars / Qute**
- Intégrations : **Microsoft Graph SDK v6.12**, **OpenAI API**, **Anthropic Claude API**, **AIS Stream** (WebSocket)
- Observabilité : Micrometer + Prometheus, SmallRye Health, logs JSON en prod
- Cache : Caffeine (event-cache 1h, active-contracts 5m, active-tariffs 10m, payments 1m)
- Tests : JUnit 5, REST Assured, Mockito, **ArchUnit** (test des bounded contexts), MongoDB DevServices

### Organisation `src/main/java/fr/alb/`
Code organisé en **bounded contexts** : `billing/`, `berth/`, `yard/`, `bol/`, `dd/`, `edi/`, `customs/`, `equipment/`, `gate/`, `parties/`, `ai/`, `askai/`, `ais/`, `platform/`, `security/`, `config/`, `model/`, `dao/`, `dto/`, `filter/`, `health/`, `startup/`, `sequence/`, `i18n/`. Chaque contexte suit le pattern `resource/` (endpoints REST) → `service/` (business) → `dao/` (Panache repos) → `model/` (entités).

Toutes les entités héritent de `EntityBase`. Publication d'événements via `DomainEventPublisher` (outbox pattern, scheduler configurable `app.outbox.poll-interval`).

### API REST (~50 resources)
Principaux paths (tous préfixés selon la config) :

- **Auth / IA** : `/auth`, `/ask-ai`
- **Billing** : `/invoice`, `/invoices`, `/contract`, `/tariff`, `/tax`, `/tax-calculation`, `/payment`, `/charge-record`, `/invoice-template`, `/invoice-sequence`
- **BOL** : `/billoflading` (CRUD + `/bulk` + `/{id}/transport`)
- **Berth / AIS** : `/vessel`, `/berth`, `/visit`, `/vessel-event`, `/vessel-ais`
- **Yard** : `/item`, `/item-event`, `/item-event-lookup`, `/yard`, `/lifecycle`
- **Customs / EDI** : `/customs/declarations` (+ `/submit`, `/clear`), `/edi`, `/edi/inbound`, `/edi/outbound/invoic/{invoiceId}`, `/edi-partner`
- **Equipment / Gate** : `/equipment`, `/container-archetype`, `/iso-container-code`, `/gate`
- **D&D** : `/dd-rule`, `/dd-accrual`, `/dd-dashboard`, `/holiday-calendar`
- **AI / Admin** : `/feature-request`, `/forecasting`, `/read-model`, `/third-party`, `/event-config`, `/user-admin` (via MS Graph), `/translation`

OpenAPI exposé en dev sur `/swagger-ui` et en prod sur `/q/openapi`.

### Sécurité
- OIDC Azure AD (tenant `17a64cff-61e7-4589-bbe0-c0989d8f625c`), principal claim = `preferred_username`
- Rôles mappés depuis le claim `groups` (Object IDs Azure AD) vers `ROLE_ADMIN`, `ROLE_INVOICE_ADMIN`, `ROLE_TEMPLATES_ADMIN`, `ROLE_USER`, `ROLE_READONLY`
- Autorisation via `@RolesAllowed` sur les endpoints
- Administration utilisateurs / groupes / invitations via **Microsoft Graph** (Managed Identity + federated credential, scopes `User.Read.All`, `GroupMember.ReadWrite.All`, `User.Invite.All`)

### Configuration
`src/main/resources/application.properties` — clé d'entrée pour toute config. Variables d'environnement principales : `MONGODB_URI`, `MONGODB_DATABASE`, `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `AISSTREAM_API_KEY`, `AZURE_TENANT_ID`, `AZURE_GRAPH_CLIENT_ID`, `PORT`. Timezone forcée à `Europe/Paris`. Profils `%dev` et `%test` (MongoDB DevServices image `mongo:7`).

### Scheduler / background
- Outbox publisher (off par défaut, activable via `app.outbox.poll-interval`)
- D&D scheduler (cron `app.dd.scheduler.cron`)
- Heartbeat AIS WebSocket toutes les 60 s, filtres bbox + liste MMSI

### Build & déploiement
```bash
cd FactBack-main
./mvnw quarkus:dev              # dev mode (live reload)
./mvnw clean verify             # tests + package
./mvnw clean package -DskipTests
docker build -t factback .      # image multi-stage JDK 21 → JRE 21, G1GC, 75% RAM
```

CI/CD : `.github/workflows/cicd-quarkus-azure.yml` — PR ⇒ build + tests ; push sur `main` ⇒ build image, push `ghcr.io/pigch/factback:${SHA}`, deploy sur Azure Container App `ca-factback` du resource group `rg-cont-billing-dev` (auth OIDC + Managed Identity).

Scripts utilitaires dans `FactBack-main/scripts/` : `setup-azure-ad-roles.sh` (création groupes AD + mapping rôles), `azure-cli-commands.sh` (référence manuelle).

---

## 3. Frontend — `FactFront-main/`

### Stack
- **Vue 3.4** (Composition API) + **TypeScript 5.4** (strict) + **Vite**
- State : **Pinia 3** (5 stores)
- Router : **Vue Router 4**
- HTTP : **axios** avec intercepteurs (Bearer token + refresh 401)
- Auth : **@azure/msal-browser 4** (Azure AD, même client que le back) + fallback login local
- i18n : **vue-i18n** (EN, FR, ES)
- UI : **Tailwind CSS 3** (custom `brand` palette, fonts Plus Jakarta Sans / JetBrains Mono), **lucide-vue-next** (icônes)
- Rich content : **Tiptap 2** (rich text), **GrapesJS 0.21** + Studio SDK (designer de templates de facture)
- Data viz : **chart.js** + **vue-chartjs**
- Export : **xlsx**, sanitization HTML via **dompurify**
- Telemetry : **Azure Application Insights** (`src/plugin/appInsights.ts`)
- Tests : **Vitest 4** + **@vue/test-utils 2** (jsdom)

### Organisation `src/`
```
src/
├── App.vue / main.ts / pinia.ts
├── components/        # ~64 .vue (≈26 500 lignes) — écrans métier + ui/ partagé
├── composables/       # 20 hooks réutilisables (use.invoice.ts, useFocusTrap, useKeyboardShortcut, useColumnPreferences…)
├── stores/            # 5 stores Pinia : auth, invoice, billOfLading, item, i18n
├── services/          # 20 modules API (un par domaine back)
├── types/             # 22 fichiers de types TS (mirroring du back)
├── utils/             # agrégats BOL/items, rendu HTML factures, date, cache, persistState…
├── router/index.ts    # définition des routes + guards
├── config/            # api.ts (base URL), msal.ts (config Azure AD)
├── plugin/            # axios.ts, appInsights.ts
├── i18n/ + locales/   # setup vue-i18n + en.json
└── __tests__/         # specs Vitest
```

### Routing (extraits)
`/invoices` (default), `/items`, `/bills`, `/vessels`, `/vessels/statistics`, `/vessel-registry`, `/events`, `/events-config` (admin), `/third-parties` (admin), `/admin/users` (admin), `/contracts`, `/tariffs`, `/configuration/sequences`, `/iso-codes`, `/archetypes`, `/template-designer` (admin), `/edi`, `/payments`, `/dd`, `/dd/rules`, `/customs`, `/forecasting`, `/fr-dashboard`, `/backlog`, `/i18n` (admin).

Guards : `beforeEach` redirige vers `/login` si non authentifié ; routes admin protégées par wrapper `AuthGuard.vue`.

### Authentification
`stores/authStore.ts` orchestre MSAL : acquisition silencieuse puis popup, parsing JWT, refresh, persistance token. L'intercepteur axios injecte le Bearer et gère le 401 (logout / refresh).

### Variables d'environnement (`.env.development`)
`VITE_API_URL`, `VITE_FEATURE_ADOBE_EXPRESS`, `VITE_GJS_STUDIO_KEY`, `VITE_AZURE_AD_CLIENT_ID`, `VITE_AZURE_AD_AUTHORITY`, `VITE_AZURE_AD_SCOPES`, `VITE_AZURE_AD_REDIRECT_URI`.

### Scripts npm
```bash
cd FactFront-main
npm ci
npm run dev          # Vite dev server sur :5173, proxy /api → :8080
npm run build        # build prod
npm run preview      # serve le build localement
npm test             # vitest run
npm run typecheck    # vue-tsc --noEmit
```

Scripts auxiliaires `scripts/extract-i18n-candidates.ts` et `scripts/check-i18n-coverage.ts` pour la couverture i18n.

### CI/CD
- **GitHub Actions** (`.github/workflows/deploy.yml`) : build sur PR, deploy `dist/` sur Azure Static Web Apps / Storage `stvuebilling28691` sur `main`
- **GitLab CI** (`.gitlab-ci.yml`) : install / test / build / deploy (Blob Storage via Azure CLI, Node 20)

### Divers
- `.bolt/` : artefacts générés par bolt.new (config + prompt initial)
- `cosmos_migration` : script d'introspection schémas / validators / indexes MongoDB
- `src.zip` : snapshot d'archive de `src/` (sauvegarde)
- `tests/` (54 specs) à la racine du front, en plus de `src/__tests__/`

---

## 4. Lancer la stack en local

Dans deux terminaux séparés :

```bash
# Terminal 1 — backend (nécessite MongoDB ; en dev profile, Quarkus DevServices le démarre)
cd FactBack-main
./mvnw quarkus:dev
# → http://localhost:8080 (Swagger UI: /swagger-ui, Dev UI: /q/dev)

# Terminal 2 — frontend
cd FactFront-main
npm ci && npm run dev
# → http://localhost:5173 (proxy /api → :8080)
```

Pour l'authentification réelle, fournir un compte Azure AD du tenant configuré ; sinon utiliser le fallback login local exposé par `AuthResource` côté back.

---

## 5. Conventions et points d'attention

- **MongoDB en replica set obligatoire** dès que des transactions sont en jeu (documenté dans `FactBack-main/README.md`).
- **Bounded contexts** : un test ArchUnit (`BoundedContextArchitectureTest`) vérifie qu'ils ne se mélangent pas — toute nouvelle dépendance inter-domaine cassera la build.
- **Cache Caffeine** : invalider explicitement lors des écritures sur contracts / tariffs / payments / event-config.
- **PDF / templates** : les templates Handlebars sont sous `src/main/resources/templates/` ; les changements doivent être testés via les endpoints `/invoice/{id}/pdf`.
- **AIS Stream** : si la clé `AISSTREAM_API_KEY` est absente, le client WS reste off — pas d'erreur fatale au démarrage.
- **Sécurité de repo** : `FactFront-main/src/components/` contient `contract.json` et `credentialsCapetownMsc.txt` — à auditer / déplacer hors du source frontend (risque d'exposition dans le bundle).
- **`src.zip`** dans le front est une archive ; ne pas la considérer comme du code source vivant.
