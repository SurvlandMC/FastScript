#!/usr/bin/env bash
# FastScript Wiki updater: pulls fresh wiki/ sources from GitHub
# (sparse checkout — only the wiki folder is downloaded) and refreshes
# the install. Handles both cases:
#   - existing install: copies docs/ + mkdocs.yml, refreshes pip deps,
#     restarts the service, verifies both languages;
#   - fresh machine (no install dir): runs install.sh from the clone.
#
#   sudo bash update.sh [domain]
#
# DOMAIN is only used for a fresh install; updates keep the existing
# nginx domain untouched.

set -euo pipefail

REPO="${REPO:-https://github.com/SurvlandMC/FastScript.git}"
BRANCH="${BRANCH:-main}"
INSTALL_DIR="${INSTALL_DIR:-/opt/fastscript-wiki}"
SERVICE_NAME="${SERVICE_NAME:-fastscript-wiki}"
PORT="${PORT:-8000}"

if [ "$(id -u)" -ne 0 ]; then
  echo "run as root (sudo bash update.sh [domain])" >&2
  exit 1
fi
command -v git >/dev/null || {
  apt-get update -y
  DEBIAN_FRONTEND=noninteractive apt-get install -y git
}

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "==> cloning $REPO@$BRANCH (wiki/ only)"
if git clone --depth 1 --filter=blob:none --sparse \
        --branch "$BRANCH" "$REPO" "$WORK/repo" >/dev/null 2>&1 \
    && git -C "$WORK/repo" sparse-checkout set wiki >/dev/null 2>&1; then
  SRC="$WORK/repo/wiki"
else
  echo "    sparse checkout unsupported here, falling back to a shallow clone"
  rm -rf "$WORK/repo"
  git clone --depth 1 --branch "$BRANCH" "$REPO" "$WORK/repo"
  SRC="$WORK/repo/wiki"
fi

if [ ! -d "$INSTALL_DIR" ]; then
  echo "==> no install found at $INSTALL_DIR, running full install from the clone"
  bash "$SRC/install.sh" "${1:-wiki.example.com}"
  exit 0
fi

echo "==> refreshing content in $INSTALL_DIR"
rm -rf "$INSTALL_DIR/docs" "$INSTALL_DIR/mkdocs.yml"
cp -r "$SRC/docs" "$SRC/mkdocs.yml" "$INSTALL_DIR/"

echo "==> refreshing python deps"
"$INSTALL_DIR/.venv/bin/pip" install "mkdocs-material" "mkdocs-static-i18n>=1.0"

echo "==> restarting $SERVICE_NAME"
systemctl restart "$SERVICE_NAME"
sleep 3
ROOT_LANG=$(curl -fsS "http://127.0.0.1:${PORT}/" | grep -o '<html lang="[^"]*"' | head -1 || true)
RU_LANG=$(curl -fsS "http://127.0.0.1:${PORT}/ru/" | grep -o '<html lang="[^"]*"' | head -1 || true)
echo "    / -> ${ROOT_LANG:-none}, /ru/ -> ${RU_LANG:-none}"
if [ "$ROOT_LANG" != '<html lang="en"' ] || [ "$RU_LANG" != '<html lang="ru"' ]; then
  echo "language check FAILED (want en + ru), see journalctl -u $SERVICE_NAME" >&2
  exit 1
fi
echo "UPDATED OK"
