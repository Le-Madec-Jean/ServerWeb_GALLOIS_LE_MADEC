#!/bin/bash
# start.sh - Demarre le serveur web

BIN_DIR="$(dirname "$0")/../bin"
RUN_DIR="$(dirname "$0")/../run"
PID_FILE="$RUN_DIR/myweb.pid"

mkdir -p "$RUN_DIR"

echo "Demarrage du serveur..."
cd "$BIN_DIR"
java HttpServer &
echo $! > "$PID_FILE"
echo "Serveur demarre (PID : $(cat $PID_FILE))"
