#!/bin/bash
# start.sh - Demarre le serveur web

# Se placer dans le dossier du script
cd "$(dirname "$0")" || exit 1
mkdir -p ../run

echo "Demarrage du serveur..."
cd ../bin
java HttpServer &
echo $! > ../run/myweb.pid
echo "Serveur demarre (PID : $(cat ../run/myweb.pid))"
