#!/bin/bash
# stop.sh - Arrete le serveur web

RUN_DIR="$(dirname "$0")/../run"
PID_FILE="$RUN_DIR/myweb.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "Le serveur n'est pas en cours d'execution."
    exit 1
fi

PID=$(cat "$PID_FILE")
kill "$PID"
rm -f "$PID_FILE"
echo "Serveur arrete (PID : $PID)"
