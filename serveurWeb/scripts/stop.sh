#!/bin/bash
# stop.sh - Arrete le serveur web

# Se placer dans le dossier du script
cd "$(dirname "$0")" || exit 1

if [ ! -f "../run/myweb.pid" ]; then
    echo "Le serveur n'est pas en cours d'execution."
    exit 1
fi

PID=$(cat "../run/myweb.pid")
kill "$PID"
rm -f "../run/myweb.pid"
echo "Serveur arrete (PID : $PID)"
