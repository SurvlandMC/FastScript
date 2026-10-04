# FastScript Wiki — установка
#
# Ubuntu/Debian, без Docker. Ставит MkDocs Material из pip (venv),
# поднимает вики как systemd-сервис (mkdocs serve на 127.0.0.1:8000)
# и проксирует его через nginx.
#
# Запуск на сервере из каталога wiki/ репозитория:
#   sudo bash install.sh [domain]
# Пример:
#   sudo bash install.sh wiki.example.com
#
# Переменные ниже можно править руками.

set -euo pipefail

DOMAIN="${1:-wiki.example.com}"
PORT="${PORT:-8000}"
INSTALL_DIR="${INSTALL_DIR:-/opt/fastscript-wiki}"
SERVICE_NAME="${SERVICE_NAME:-fastscript-wiki}"

if [ "$(id -u)" -ne 0 ]; then
  echo "run as root (sudo bash install.sh [domain])" >&2
  exit 1
fi

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> [1/6] packages (python3, venv, nginx, curl)"
apt-get update -y
DEBIAN_FRONTEND=noninteractive apt-get install -y python3 python3-venv nginx curl

echo "==> [2/6] copy wiki sources to ${INSTALL_DIR}"
mkdir -p "${INSTALL_DIR}"
cp -r "${SRC_DIR}/mkdocs.yml" "${SRC_DIR}/docs" "${INSTALL_DIR}/"
rm -rf "${INSTALL_DIR}/docs/assets/.cache" 2>/dev/null || true

echo "==> [3/6] python venv + mkdocs-material"
if [ ! -x "${INSTALL_DIR}/.venv/bin/python" ]; then
  python3 -m venv "${INSTALL_DIR}/.venv"
fi
"${INSTALL_DIR}/.venv/bin/pip" install --upgrade pip
"${INSTALL_DIR}/.venv/bin/pip" install "mkdocs-material" "mkdocs-static-i18n>=1.0"

echo "==> [4/6] verify build"
"${INSTALL_DIR}/.venv/bin/mkdocs" build -f "${INSTALL_DIR}/mkdocs.yml" -d /tmp/fastscript-wiki-check
rm -rf /tmp/fastscript-wiki-check

echo "==> [5/6] systemd service ${SERVICE_NAME}"
sed -e "s#__INSTALL_DIR__#${INSTALL_DIR}#g" -e "s#__PORT__#${PORT}#g" \
  "${SRC_DIR}/systemd/fastscript-wiki.service" > "/etc/systemd/system/${SERVICE_NAME}.service"
systemctl daemon-reload
systemctl enable --now "${SERVICE_NAME}"
sleep 2
curl -fsS "http://127.0.0.1:${PORT}/" >/dev/null
curl -fsS "http://127.0.0.1:${PORT}/ru/" >/dev/null
echo "    service answers on 127.0.0.1:${PORT} in both languages (/, /ru/)"

echo "==> [6/6] nginx reverse proxy for ${DOMAIN}"
sed -e "s#__DOMAIN__#${DOMAIN}#g" -e "s#__PORT__#${PORT}#g" \
  "${SRC_DIR}/nginx/fastscript-wiki.conf" > "/etc/nginx/sites-available/${SERVICE_NAME}.conf"
ln -sf "/etc/nginx/sites-available/${SERVICE_NAME}.conf" "/etc/nginx/sites-enabled/${SERVICE_NAME}.conf"
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

echo
echo "DONE: http://${DOMAIN}/  ->  127.0.0.1:${PORT} (${SERVICE_NAME}.service)"
echo "TLS: apt install certbot python3-certbot-nginx && certbot --nginx -d ${DOMAIN}"
echo "Static alternative (no service): mkdocs build + 'root ${INSTALL_DIR}/site' — see nginx conf comment."
