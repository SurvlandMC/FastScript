# Deploying the wiki (short guide)

The shortest path from the zip to a live site. You need an Ubuntu/Debian
server with sudo, a domain pointing at it, and 10 minutes.

## Step 1. Upload the archive

```bash
scp fastscript-wiki.zip user@SERVER:/tmp/
ssh user@SERVER
cd /tmp && unzip -o fastscript-wiki.zip -d fastscript-wiki-src
cd fastscript-wiki-src/wiki   # mkdocs.yml, docs/, install.sh live here
```

The archive contains only the `wiki/` folder — nothing else is needed.

## Step 2. Run the installer

```bash
sudo bash install.sh wiki.example.com
```

What happens (6 steps with progress in the console):

1. `apt install python3 python3-venv nginx curl`.
2. Copy to `/opt/fastscript-wiki`.
3. Virtualenv + `pip install mkdocs-material mkdocs-static-i18n`.
4. Verification build (if it fails here the content is broken — nothing proceeds).
5. Systemd service `fastscript-wiki` (listens on `127.0.0.1:8000`), checked with `curl`.
6. Nginx site with a reverse proxy to the service, `nginx -t`, reload.

Done: `http://your-domain/` serves the wiki, in English and Russian
(switcher in the header).

## Step 3. HTTPS (one command)

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d wiki.example.com
```

## Step 4. Updating content

```bash
# on your machine, edit docs/<en|ru>/*.md, re-pack:
Compress-Archive -Path wiki -DestinationPath fastscript-wiki.zip -Force
# on the server:
cd /tmp && unzip -o fastscript-wiki.zip -d fastscript-wiki-src
sudo cp -r fastscript-wiki-src/wiki/docs/* /opt/fastscript-wiki/docs/
sudo cp fastscript-wiki-src/wiki/mkdocs.yml /opt/fastscript-wiki/mkdocs.yml
# in case the venv predates the bilingual plugin:
sudo /opt/fastscript-wiki/.venv/bin/pip install mkdocs-material mkdocs-static-i18n
sudo systemctl restart fastscript-wiki
# check both languages answer:
curl -s http://127.0.0.1:8000/ | grep -o '<html lang="[^"]*"' | head -1
curl -s http://127.0.0.1:8000/ru/ | grep -o '<html lang="[^"]*"' | head -1
```

The `mkdocs serve` service picks files up itself (livereload); restart
is for reliability. No venv rebuild needed.

## Step 5 (optional). Static, no service

If you don't want a service at all:

```bash
/opt/fastscript-wiki/.venv/bin/mkdocs build -f /opt/fastscript-wiki/mkdocs.yml -d /opt/fastscript-wiki/site
```

and replace the nginx `location /` block with:

```nginx
root /opt/fastscript-wiki/site;
index index.html;
try_files $uri $uri/ $uri.html =404;
```

## If something breaks

| Symptom | Action |
|---|---|
| `install.sh` asks for root | `sudo bash install.sh ...` |
| Site doesn't open | `systemctl status fastscript-wiki`, `journalctl -u fastscript-wiki -n 50`, `nginx -t`, `curl 127.0.0.1:8000/` |
| 502 Bad Gateway | the service is down — see `journalctl` (usually pip/venv or a busy port; change it via `PORT=9000 sudo -E bash install.sh ...`) |
| 404 on nested pages | `try_files` is only needed for the static variant; not for proxy |
| Firewall | `sudo ufw allow 80,443/tcp` |

Local preview without a server (for authors):
`pip install mkdocs-material mkdocs-static-i18n && mkdocs serve` in `wiki/`.
