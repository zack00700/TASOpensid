#!/usr/bin/env bash
# Lance la stack TASOpensid en local : MongoDB (replica set) + Backend Quarkus + Frontend Vite.
#
# Usage:
#   ./start.sh              # lance tout
#   ./start.sh --no-auth    # désactive la sécurité backend (utile pour tester /api sans Azure AD)
#   ./start.sh --logs       # lance tout puis tail les logs
#
# Stop : ./stop.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BACK_DIR="$ROOT/FactBack-main"
FRONT_DIR="$ROOT/FactFront-main"
RUN_DIR="$ROOT/.run"
LOG_DIR="$RUN_DIR/logs"
PID_DIR="$RUN_DIR/pids"

mkdir -p "$LOG_DIR" "$PID_DIR"

NO_AUTH=0
TAIL_LOGS=0
for arg in "$@"; do
  case "$arg" in
    --no-auth) NO_AUTH=1 ;;
    --logs)    TAIL_LOGS=1 ;;
    -h|--help)
      sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "Argument inconnu : $arg" >&2; exit 2 ;;
  esac
done

color() { printf "\033[1;%sm%s\033[0m\n" "$1" "$2"; }
info()  { color 36 "[start] $*"; }
ok()    { color 32 "[start] $*"; }
warn()  { color 33 "[start] $*"; }
err()   { color 31 "[start] $*"; }

# --- Pré-flight ---------------------------------------------------------------
info "Vérification des prérequis…"
command -v docker  >/dev/null || { err "docker introuvable"; exit 1; }
command -v node    >/dev/null || { err "node introuvable";   exit 1; }
docker info >/dev/null 2>&1   || { err "Docker daemon n'est pas démarré"; exit 1; }

# Java: `command -v java` is misleading on macOS because /usr/bin/java is just a
# stub that redirects to the JDK installer when no real JDK is installed. We
# actually run `java -version` and fall back to a Homebrew openjdk@21 if needed.
if ! java -version >/dev/null 2>&1; then
  for candidate in /opt/homebrew/opt/openjdk@21 /usr/local/opt/openjdk@21; do
    if [ -x "$candidate/bin/java" ]; then
      info "Java non trouvé dans le PATH — utilisation de $candidate"
      export JAVA_HOME="$candidate"
      export PATH="$candidate/bin:$PATH"
      break
    fi
  done
fi
if ! java -version >/dev/null 2>&1; then
  err "Aucun JDK fonctionnel trouvé. Installe-le avec : brew install openjdk@21"
  exit 1
fi

port_in_use() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }
for p in 27017 8080 5173; do
  if port_in_use "$p"; then
    warn "Port $p déjà utilisé — vérifie qu'un autre processus ne tourne pas, ou exécute ./stop.sh"
  fi
done

# --- 1. MongoDB ---------------------------------------------------------------
info "Démarrage de MongoDB (replica set rs0)…"
(cd "$ROOT" && docker compose up -d mongo) >/dev/null

info "Attente du healthcheck Mongo…"
for i in $(seq 1 60); do
  status="$(docker inspect --format '{{.State.Health.Status}}' tasopensid-mongo 2>/dev/null || echo unknown)"
  if [ "$status" = "healthy" ]; then
    ok "MongoDB prêt (replica set initialisé)"
    break
  fi
  sleep 2
  if [ "$i" -eq 60 ]; then
    err "MongoDB n'est pas devenu healthy en 2 min. Voir : docker logs tasopensid-mongo"
    exit 1
  fi
done

# --- 2. Backend Quarkus -------------------------------------------------------
info "Démarrage du backend Quarkus (dev mode)…"

# `set -u` + bash 3 (macOS default) throws on "${ARR[@]}" when ARR is empty, so we
# build the extra Maven args as a single string instead of an array.
EXTRA_BACK_ARGS=""
if [ "$NO_AUTH" -eq 1 ]; then
  warn "Sécurité backend désactivée (--no-auth). Tous les @RolesAllowed sont contournés."
  EXTRA_BACK_ARGS="-Dquarkus.security.auth.enabled-in-dev-mode=false -Dquarkus.oidc.enabled=false"
fi

# Désactivation propre des intégrations externes en local
export AIS_ENABLED="${AIS_ENABLED:-false}"
export AZURE_GRAPH_ENABLED="${AZURE_GRAPH_ENABLED:-false}"
# OPENAI_API_KEY / ANTHROPIC_API_KEY sont lus depuis ton shell s'ils existent

cd "$BACK_DIR"
# shellcheck disable=SC2086  # intentional word-splitting on EXTRA_BACK_ARGS
nohup ./mvnw -q quarkus:dev $EXTRA_BACK_ARGS </dev/null >"$LOG_DIR/back.log" 2>&1 &
echo $! > "$PID_DIR/back.pid"
cd "$ROOT"

info "Attente du backend sur http://localhost:8080/q/health …"
for i in $(seq 1 90); do
  if curl -fsS http://localhost:8080/q/health >/dev/null 2>&1; then
    ok "Backend prêt"
    break
  fi
  sleep 2
  if [ "$i" -eq 90 ]; then
    err "Backend non démarré en 3 min. Voir : tail -f $LOG_DIR/back.log"
    exit 1
  fi
done

# --- 3. Frontend Vite ---------------------------------------------------------
info "Installation des dépendances front (si nécessaire)…"
cd "$FRONT_DIR"
if [ ! -d node_modules ]; then
  npm ci >"$LOG_DIR/front-install.log" 2>&1 || { err "npm ci a échoué — voir $LOG_DIR/front-install.log"; exit 1; }
fi

info "Démarrage du frontend Vite…"
nohup npm run dev </dev/null >"$LOG_DIR/front.log" 2>&1 &
echo $! > "$PID_DIR/front.pid"
cd "$ROOT"

info "Attente du frontend sur http://localhost:5173 …"
for i in $(seq 1 60); do
  if curl -fsS http://localhost:5173 >/dev/null 2>&1; then
    ok "Frontend prêt"
    break
  fi
  sleep 1
  if [ "$i" -eq 60 ]; then
    err "Frontend non démarré en 1 min. Voir : tail -f $LOG_DIR/front.log"
    exit 1
  fi
done

# --- 4. Seed local dev user (idempotent) -------------------------------------
# The Vue Login screen always asks for credentials, even when --no-auth disables
# server-side enforcement. We provision a deterministic local account through the
# existing /auth/register endpoint so the tester can sign in immediately.
SEED_USER="devuser"
SEED_PASS="devpass123"
SEED_EMAIL="devuser@local.test"
info "Création du compte de dev local ($SEED_USER) si nécessaire…"
REGISTER_BODY=$(printf '{"username":"%s","email":"%s","password":"%s","fullName":"Dev User"}' \
  "$SEED_USER" "$SEED_EMAIL" "$SEED_PASS")
REGISTER_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Content-Type: application/json" \
  -d "$REGISTER_BODY" \
  http://localhost:8080/auth/register || echo "000")
case "$REGISTER_STATUS" in
  201) ok "Compte de dev créé." ;;
  400) info "Compte de dev déjà présent." ;;
  *)   warn "Création du compte de dev : réponse HTTP $REGISTER_STATUS (le compte n'est peut-être pas exploitable)." ;;
esac

# --- Récap --------------------------------------------------------------------
echo
ok "Stack démarrée. URLs :"
echo "  Frontend     : http://localhost:5173"
echo "  Backend API  : http://localhost:8080/api  (root: /)"
echo "  Swagger UI   : http://localhost:8080/swagger-ui"
echo "  Health       : http://localhost:8080/q/health"
echo "  MongoDB      : mongodb://localhost:27017 (database: tos3d)"
echo
echo "  Identifiants dev : username=$SEED_USER  mot de passe=$SEED_PASS"
echo "                     (ou bouton 'Sign in with Microsoft' si tu as un compte Azure AD du tenant)"
echo
echo "  Logs back  : tail -f $LOG_DIR/back.log"
echo "  Logs front : tail -f $LOG_DIR/front.log"
echo "  Stop       : ./stop.sh"

if [ "$TAIL_LOGS" -eq 1 ]; then
  echo; info "Tail des logs (Ctrl+C pour arrêter le tail — la stack reste up)…"
  tail -f "$LOG_DIR/back.log" "$LOG_DIR/front.log"
fi
