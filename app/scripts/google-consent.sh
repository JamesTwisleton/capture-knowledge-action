#!/usr/bin/env bash
# Runs T09's one-time Google consent flow and prints the refresh token to add to app/.env.
#
# Once, per machine, per Google account. A refresh token is what lets CKA keep reading the
# recordings folder with nobody present; access tokens last an hour and are renewed from it
# silently. T20's ./setup.sh will call this as part of the wizard.
#
# Not run inside the backend container: it has to open a browser on your machine and catch the
# redirect on a loopback port, neither of which a container can do.
set -euo pipefail
cd "$(dirname "$0")/.."   # app/

[ -f .env ] || { echo "No app/.env — copy .env.example to .env and fill in the Google client id and secret."; exit 1; }

# set -a exports everything sourced, which is how the client id and secret reach the JVM without
# appearing on a command line, where `ps` would show them to every other process on the machine.
set -a
# shellcheck disable=SC1091
. ./.env
set +a

cd backend
./mvnw -q compile
CLASSPATH=$(./mvnw -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout 2>/dev/null | tail -1)
exec java -cp "target/classes:${CLASSPATH}" com.cka.provider.capture.GoogleConsent
