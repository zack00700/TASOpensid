#!/usr/bin/env bash
# Arrête la stack TASOpensid : frontend Vite, backend Quarkus, MongoDB.
#
# Usage:
#   ./stop.sh           # arrêt propre
#   ./stop.sh --clean   # arrêt + suppression du volume MongoDB (perte des données)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
RUN_DIR="$ROOT/.run"
PID_DIR="$RUN_DIR/pids"

CLEAN=0
for arg in "$@"; do
  case "$arg" in
    --clean) CLEAN=1 ;;
    -h|--help) sed -n '2,7p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Argument inconnu : $arg" >&2; exit 2 ;;
  esac
done

color() { printf "\033[1;%sm%s\033[0m\n" "$1" "$2"; }
info()  { color 36 "[stop] $*"; }
ok()    { color 32 "[stop] $*"; }
warn()  { color 33 "[stop] $*"; }

kill_pid() {
  local name="$1" pidfile="$2"
  if [ -f "$pidfile" ]; then
    local pid; pid="$(cat "$pidfile")"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      info "Arrêt $name (pid $pid)…"
      # Tuer le process group pour attraper aussi les enfants (Maven → Java, etc.)
      kill -TERM "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
      for _ in $(seq 1 10); do
        kill -0 "$pid" 2>/dev/null || break
        sleep 1
      done
      if kill -0 "$pid" 2>/dev/null; then
        warn "$name ne s'arrête pas, SIGKILL…"
        kill -KILL "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null || true
      fi
    else
      info "$name : aucun process actif (pidfile périmé)"
    fi
    rm -f "$pidfile"
  else
    info "$name : pas de pidfile"
  fi
}

kill_pid "frontend" "$PID_DIR/front.pid"
kill_pid "backend"  "$PID_DIR/back.pid"

# Filets de sécurité : tuer les éventuels orphelins
pkill -f "quarkus:dev"        2>/dev/null || true
pkill -f "vite"                2>/dev/null || true
pkill -f "FactBack-main.*java" 2>/dev/null || true

info "Arrêt de MongoDB…"
if [ "$CLEAN" -eq 1 ]; then
  warn "Suppression du volume MongoDB (données perdues)…"
  (cd "$ROOT" && docker compose down -v) >/dev/null
else
  (cd "$ROOT" && docker compose down) >/dev/null
fi

ok "Stack arrêtée."
