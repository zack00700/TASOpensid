---
name: quarkus-agents
description: >
  Strategy for using AI Agents and Sub-agents to automate complex Quarkus development tasks.
  Use this skill whenever a request involves multiple interdependent steps that would benefit
  from parallel or sequential agent orchestration: generating a complete feature (resource +
  service + repository + tests + validation), auditing an entire codebase across multiple
  quality axes, analyzing Azure DevOps pipeline failures, reviewing a PR across all skill
  dimensions simultaneously, designing a new microservice from scratch, or any task where
  the answer is "I need to do X, then Y, then Z based on the result of Y".
  Trigger also when the user says "do everything", "generate the full stack", "analyze all",
  "audit the whole project", or any compound request that touches 3+ skills or files.
  Never use agents for simple single-step tasks — agents add overhead. Use them when
  parallelism or decomposition genuinely saves time or improves quality.
---

# Quarkus Agents & Sub-agents — Architect Reference

## Core Philosophy
> *"Un architecte ne code pas tout lui-même. Il orchestre des spécialistes.
> Les agents sont vos spécialistes — chacun expert dans un domaine, travaillant en parallèle."*

Un agent = une instance de Claude avec un rôle, un contexte, et une tâche précise.
Un sous-agent = un agent spawné par un orchestrateur pour accomplir une partie d'une tâche plus large.

**Règle fondamentale** : n'utiliser les agents que quand la décomposition apporte une valeur réelle —
parallelisme, isolation des responsabilités, ou tâches trop larges pour un seul contexte.

---

## 1. Matrice de Décision — Agent ou Pas ?

```
┌─────────────────────────────────────────────────────────────┐
│                    ÉVALUER LA REQUÊTE                       │
└──────────────────────────┬──────────────────────────────────┘
                           │
           ┌───────────────▼───────────────┐
           │  Peut être répondu en         │
           │  1 étape, 1 compétence ?      │
           └───────────────┬───────────────┘
                    OUI    │    NON
                    ▼      │      ▼
             Réponse    Combien d'axes ?
             directe    ┌──────────────┐
                        │  2–3 axes    │  → Agent séquentiel simple
                        │  4+ axes     │  → Agents parallèles
                        │  > 1 fichier │  → Sub-agents par fichier/module
                        │  Inconnu     │  → Agent explorateur d'abord
                        └──────────────┘
```

| Type de requête | Approche recommandée |
|----------------|---------------------|
| "Explique-moi comment X fonctionne" | Réponse directe — pas d'agent |
| "Génère un endpoint REST" | Réponse directe — code inline |
| "Génère la feature complète : resource + service + repo + tests" | **Agent orchestrateur** |
| "Audite ce microservice" | **Sub-agents parallèles** (sécurité / perf / tests / architecture) |
| "Analyse pourquoi ce pipeline a échoué" | **Agent séquentiel** (logs → analyse → remédiation) |
| "Review toute la PR" | **Sub-agents** par fichier ou par axe qualité |
| "Conçois l'architecture d'un nouveau service" | **Agent explorateur** puis **agents spécialisés** |
| "Refactore ce service pour respecter SOLID" | **Agent orchestrateur** (analyse → plan → exécution) |

---

## 2. Patterns d'Orchestration

### Pattern 1 — Orchestrateur / Workers

L'orchestrateur décompose, délègue, agrège. Les workers exécutent une tâche précise.

```
                    ┌─────────────────────┐
                    │    ORCHESTRATEUR    │
                    │  Décompose la tâche │
                    │  et agrège les      │
                    │  résultats          │
                    └──────┬──────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
   ┌───────────┐    ┌───────────┐    ┌───────────┐
   │  Worker 1 │    │  Worker 2 │    │  Worker 3 │
   │  Resource │    │  Service  │    │   Tests   │
   │ + DTOs    │    │ + Domain  │    │  Unitaires│
   │           │    │           │    │  + Intég. │
   └───────────┘    └───────────┘    └───────────┘
```

**Cas d'usage** : génération complète d'une feature, refactoring multi-fichiers

### Pattern 2 — Pipeline Séquentiel

Chaque agent enrichit le résultat du précédent. La sortie de N est l'entrée de N+1.

```
  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
  │ Analyse  │ →  │  Design  │ →  │  Génère  │ →  │ Valide & │
  │ du besoin│    │archi+API │    │  le code │    │  Teste   │
  └──────────┘    └──────────┘    └──────────┘    └──────────┘
```

**Cas d'usage** : conception d'un nouveau microservice, analyse de pipeline CI/CD

### Pattern 3 — Agents Parallèles

Plusieurs agents travaillent simultanément sur des dimensions indépendantes,
puis un agrégateur consolide les résultats.

```
                    ┌─────────────────┐
                    │ Tâche complexe  │
                    │ (audit complet) │
                    └────────┬────────┘
      ┌─────────────┬────────┼────────┬─────────────┐
      ▼             ▼        ▼        ▼             ▼
 ┌─────────┐ ┌─────────┐ ┌──────┐ ┌──────────┐ ┌──────────┐
 │ Sécurité│ │  SOLID  │ │Tests │ │  Perf &  │ │  Code    │
 │  Agent  │ │  Agent  │ │Agent │ │  SQL     │ │  Review  │
 │(OWASP)  │ │(Design) │ │(Cov.)│ │  Agent   │ │  Agent   │
 └────┬────┘ └────┬────┘ └──┬───┘ └────┬─────┘ └────┬─────┘
      └───────────┴──────────┴──────────┴─────────────┘
                              ▼
                    ┌─────────────────┐
                    │   Rapport final │
                    │   consolidé     │
                    └─────────────────┘
```

**Cas d'usage** : audit complet d'un microservice, review de PR multi-axes

### Pattern 4 — Critique / Réviseur

Un agent génère, un second critique, le premier révise.
Produit des résultats de meilleure qualité sur des tâches critiques.

```
  ┌───────────┐    ┌───────────┐    ┌───────────┐
  │ Générateur│ →  │  Critique │ →  │  Réviseur │
  │  (draft)  │    │ (issues)  │    │ (final)   │
  └───────────┘    └───────────┘    └───────────┘
```

**Cas d'usage** : design d'API critique, conception d'une architecture de sécurité

---

## 3. Workflows Agentiques — Exemples Concrets Quarkus

### Workflow A — Génération Complète d'une Feature

**Requête** : *"Génère la feature complète de gestion des escales (vessel calls) :
resource, service, repository, validations et tests."*

```
ORCHESTRATEUR reçoit la demande
│
├── [Analyse] Comprendre le domaine "vessel call" dans le contexte iPaki
│   → Identifie les entités : VesselCall, Vessel, Terminal, Berth
│   → Définit les opérations CRUD + recherche par terminal/date
│
├── [Worker 1 — Domain & Repository] PARALLÈLE
│   Lit: quarkus-solid-design, quarkus-code-best-practices
│   Génère:
│   - VesselCall.java (entity @Entity, immutable)
│   - VesselCallRepository.java (PanacheRepository)
│   - VesselCallStatus.java (enum)
│
├── [Worker 2 — API & Validation] PARALLÈLE
│   Lit: quarkus-api, quarkus-validation
│   Génère:
│   - VesselCallResource.java (@Path, @RolesAllowed, pagination)
│   - VesselCallCreateRequest.java (record + @Valid)
│   - VesselCallDto.java (record)
│   - ValidationExceptionMapper (si pas déjà présent)
│
├── [Worker 3 — Service] SÉQUENTIEL (après Worker 1 + 2)
│   Lit: quarkus-code-best-practices, quarkus-solid-design
│   Génère:
│   - VesselCallService.java (orchestration, logique métier)
│   - VesselCallMapper.java (Domain ↔ DTO)
│
├── [Worker 4 — Tests] SÉQUENTIEL (après Worker 3)
│   Lit: quarkus-testing, quarkus-testing-best-practices
│   Génère:
│   - VesselCallServiceTest.java (unit, Mockito)
│   - VesselCallResourceIT.java (@QuarkusTest, RestAssured)
│   - VesselCallTestData.java (builders)
│
└── [Validation finale] Vérifier la cohérence inter-fichiers
    → Imports corrects ?  → Layering respecté ?  → Tests compilent ?
```

**Prompt orchestrateur à utiliser :**

```
Je vais générer la feature "vessel calls" complète pour le projet iPaki.

Contexte : service Quarkus (Java 17), SQL Server via Panache, RESTEasy Reactive.
Une VesselCall représente l'escale d'un navire à un terminal portuaire.
Champs : id, vesselName, imoNumber, terminalCode, berthId, eta, etd, status (PLANNED/ARRIVED/DEPARTED).

Génère dans cet ordre :
1. Entity + Repository + Status enum
2. DTOs (CreateRequest record, UpdateRequest record, VesselCallDto record)
3. Service (avec logique : impossible de planifier si overlap de berth)
4. Resource REST (/api/v1/vessel-calls, CRUD + GET by terminal)
5. Tests unitaires du service (Mockito)
6. Tests d'intégration de la resource (@QuarkusTest + RestAssured)

Respecte les skills : quarkus-solid-design, quarkus-api, quarkus-validation,
quarkus-testing. Chaque fichier doit être complet et compilable.
```

---

### Workflow B — Audit Complet d'un Microservice

**Requête** : *"Audite le ManifestService pour identifier tous les problèmes."*

```
ORCHESTRATEUR
│
├── Lecture des fichiers source du service (bash: find + cat)
│
└── Lance 5 SUB-AGENTS en PARALLÈLE :

    [Sub-agent Sécurité]          [Sub-agent Architecture]
    Lit: quarkus-security         Lit: quarkus-solid-design
    Analyse:                      Analyse:
    - @RolesAllowed présents ?    - SOLID respecté ?
    - SQL paramétré ?             - Layering correct ?
    - Secrets exposés ?           - Design patterns appropriés ?
    - Logs sans données sensibles?- Couplage/cohésion ?

    [Sub-agent Tests]             [Sub-agent Performance]
    Lit: quarkus-testing-bp       Lit: quarkus-performance
    Analyse:                      Analyse:
    - Couverture suffisante ?     - Requêtes N+1 ?
    - Tests isolés ?              - Blocking dans Uni ?
    - Cas d'erreur couverts ?     - Pagination manquante ?
    - Anti-patterns ?             - Pool connexions ?

    [Sub-agent Code Quality]
    Lit: quarkus-code-review
    Analyse:
    - Nommage expressif ?
    - Méthodes trop longues ?
    - TODO non liés ?
    - Logging contextuel ?

AGRÉGATEUR : consolide en rapport priorisé
    🔴 CRITICAL (bloquant prod)
    🟡 HIGH (à traiter ce sprint)
    🔵 MEDIUM (dette technique)
    ⚪ LOW (améliorations cosmétiques)
```

---

### Workflow C — Analyse de Pipeline Échoué (Azure DevOps)

**Requête** : *"Mon pipeline CI a échoué, voici les logs. Trouve et corrige le problème."*

```
AGENT SÉQUENTIEL

Étape 1 — TRIAGE
  Lit les logs Azure DevOps
  → Identifie : quelle étape a échoué ? (Build / Unit Tests / SonarQube / Integration)
  → Extrait : message d'erreur exact + stack trace

Étape 2 — DIAGNOSTIC (basé sur le résultat du triage)
  Si Build failure     → analyse erreurs de compilation, deps manquantes
  Si Unit Test failure → lit les tests en échec + code correspondant
  Si SonarQube failure → identifie les règles violées
  Si Integration fail  → vérifie config Testcontainers, ports, timeouts

Étape 3 — REMÉDIATION
  Génère le correctif précis (code ou config)
  Explique POURQUOI le problème est survenu
  Suggère comment éviter la récurrence (test manquant ? règle SonarQube à configurer ?)

Étape 4 — VALIDATION
  Vérifie que le correctif ne casse pas d'autres parties
  Si modification de code → propose les tests à ajouter/mettre à jour
```

---

### Workflow D — Conception d'un Nouveau Microservice

**Requête** : *"On doit créer un nouveau microservice pour gérer les statistiques
de productivité des grues (crane productivity). Conçois l'architecture complète."*

```
AGENT EXPLORATEUR (Phase 1)
  Questions à clarifier :
  → Quelles métriques ? (mouvements/heure, taux d'utilisation, downtime)
  → Consommateurs ? (dashboard web, export Excel, API externe)
  → Source de données ? (événements CODECO/COARRI, saisie manuelle)
  → Volumétrie ? (combien de grues, fréquence de mise à jour)
  → Pays concernés ? (impact sur les certifications fiscales ?)

AGENT ARCHITECTE (Phase 2 — après réponses)
  Lit: quarkus-microservices, quarkus-solid-design, quarkus-api
  Produit:
  → Diagramme de contexte (entités, dépendances, flux)
  → Choix technologiques justifiés (SQL vs NoSQL, sync vs async)
  → Contrats d'API (OpenAPI esquisse)
  → Schéma de données
  → Stratégie d'intégration avec les services existants

AGENTS SPÉCIALISÉS (Phase 3 — en parallèle)
  [Agent Sécurité]      → Risques OWASP spécifiques à ce service
  [Agent Performance]   → SLAs cibles, stratégie de cache
  [Agent Tests]         → Stratégie de test, cas limites
  [Agent Déploiement]   → Config WinSW, Azure DevOps pipeline

SYNTHÈSE FINALE
  → Architecture Decision Record (ADR)
  → Backlog initial structuré
  → Checklist de démarrage du projet
```

---

### Workflow E — Review de PR Multi-Axes

**Requête** : *"Review complète de cette PR avant le merge."*

```
ORCHESTRATEUR
  Lit tous les fichiers modifiés dans la PR
  Identifie les couches touchées (API / Service / Repository / Tests / Config)

SUB-AGENTS PARALLÈLES (un par axe, lit uniquement les fichiers pertinents) :

  [Code Review Agent]     → quarkus-code-review   → checklist complète
  [Security Agent]        → quarkus-security       → OWASP sur les changements
  [Test Coverage Agent]   → quarkus-testing-bp     → tests manquants ?
  [Architecture Agent]    → quarkus-solid-design   → violations SOLID/layering ?
  [Performance Agent]     → quarkus-performance    → N+1, blocking, pagination ?

RAPPORT CONSOLIDÉ :
  Blocking issues (PR doit être corrigée)
  Suggestions (améliorations facultatives)
  Points positifs (encourager les bonnes pratiques)
  Verdict final : ✅ Approve / 🔄 Request Changes
```

---

## 4. Règles de Prompt pour les Agents

### Prompt d'un Sub-agent — Template

```
Tu es un agent spécialisé dans [DOMAINE].
Ton rôle unique dans ce workflow : [TÂCHE PRÉCISE].
Tu ne fais que ça — les autres dimensions sont gérées par d'autres agents.

Contexte fourni :
[FICHIERS / CODE / RÉSULTATS DES AGENTS PRÉCÉDENTS]

Skill de référence : [NOM DU SKILL À APPLIQUER]

Livrables attendus :
1. [FORMAT PRÉCIS DE SORTIE]
2. [...]

Contraintes :
- Ne pas s'éloigner du périmètre défini
- Si une information manque : l'indiquer clairement plutôt qu'inventer
- Sortie structurée en JSON ou Markdown selon indication
```

### Bonnes Pratiques de Décomposition

```
✅ Chaque sub-agent a UN périmètre clair et non-chevauchant
✅ Les dépendances entre agents sont explicites (A avant B)
✅ Le contexte passé à chaque agent est minimal (seulement ce dont il a besoin)
✅ Les sorties sont dans un format facilement agrégeable (JSON, Markdown structuré)
✅ L'orchestrateur valide la cohérence des sorties avant de les présenter

❌ Ne pas spawner un agent pour une tâche de 3 lignes
❌ Ne pas passer tout le codebase à chaque sub-agent (coût + bruit)
❌ Ne pas créer des agents avec des périmètres qui se chevauchent
❌ Ne pas oublier de gérer les cas d'échec d'un sub-agent
```

---

## 5. Quand NE PAS Utiliser les Agents

| Situation | Pourquoi pas d'agent | Faire plutôt |
|-----------|---------------------|--------------|
| "Comment fonctionne @QuarkusTest ?" | Question simple | Réponse directe |
| "Génère un endpoint GET /health" | < 20 lignes de code | Code inline |
| "Corrige ce bug" avec 1 fichier | Contexte unique | Fix direct |
| "Explique SOLID" | Connaissance générale | Explication directe |
| Tâche incertaine dans le périmètre | Agents amplifieraient la confusion | Clarifier d'abord |

**Règle empirique** : si la tâche peut être accomplie en une seule réponse de qualité — pas d'agent.
Les agents ajoutent de la latence et de la complexité. Ne les utiliser que quand le gain est tangible.

---

## 6. Intégration avec Claude Code

Dans un contexte **Claude Code** (terminal), les agents peuvent :
- Lire l'arborescence du projet (`find`, `cat`)
- Créer des fichiers directement dans le workspace
- Exécuter les tests (`mvn test`) et analyser les résultats
- Lancer des builds et inspecter les erreurs
- Soumettre des changements via git

```bash
# Exemple — un sub-agent génère et valide dans la même session
# 1. Génère ManifestService.java
# 2. Exécute : mvn test -Dtest=ManifestServiceTest
# 3. Si échec : analyse + corrige + re-teste
# 4. Rapport : "Généré, 23 tests passent, 0 échec"
```

---

## 7. Checklist Agentique

- [ ] La tâche touche ≥ 3 fichiers ou ≥ 2 dimensions orthogonales → Agent justifié
- [ ] Chaque sub-agent a un périmètre unique et non-ambigu
- [ ] Les dépendances séquentielles sont identifiées (quoi dépend de quoi)
- [ ] Le format de sortie de chaque agent est défini avant le lancement
- [ ] L'orchestrateur vérifie la cohérence avant de présenter le résultat final
- [ ] Le contexte passé à chaque sub-agent est le minimum nécessaire
- [ ] Un plan de fallback existe si un sub-agent échoue ou retourne un résultat vide

---

## See also
- Tous les autres skills Quarkus — les agents les consomment comme référence
- `quarkus-code-review` — Workflow E (PR review multi-axes)
- `quarkus-no-regression` — Workflow C (analyse pipeline échoué)
- `quarkus-solid-design` — Workflow A (génération feature complète)
