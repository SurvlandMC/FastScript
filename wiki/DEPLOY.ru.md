# Развёртывание вики (простой гайд)

Самый короткий путь от зипа до сайта. Нужен сервер Ubuntu/Debian
с sudo, домен, указывающий на сервер, и 10 минут.

## Шаг 1. Залить архив

```bash
scp fastscript-wiki.zip user@SERVER:/tmp/
ssh user@SERVER
cd /tmp && unzip -o fastscript-wiki.zip -d fastscript-wiki-src
cd fastscript-wiki-src/wiki   # рядом лежат mkdocs.yml, docs/, install.sh
```

В архиве только папка `wiki/` — больше ничего не нужно.

## Шаг 2. Запустить установку

```bash
sudo bash install.sh wiki.example.com
```

Что произойдёт (6 шагов с прогрессом в консоли):

1. `apt install python3 python3-venv nginx curl`.
2. Копирование в `/opt/fastscript-wiki`.
3. Виртуальное окружение + `pip install mkdocs-material mkdocs-static-i18n`.
4. Контрольная сборка (упасть здесь — значит битый контент, дальше не пойдёт).
5. Systemd-сервис `fastscript-wiki` (слушает `127.0.0.1:8000`), проверка через `curl`.
6. Nginx-сайт с reverse proxy на сервис, `nginx -t`, reload.

Готово: `http://ваш-домен/` показывает вики, на английском и русском
(переключатель в шапке).

## Шаг 3. HTTPS (одна команда)

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d wiki.example.com
```

## Шаг 4. Как обновлять контент

```bash
# на своей машине правите docs/<en|ru>/*.md, пакуете заново:
Compress-Archive -Path wiki -DestinationPath fastscript-wiki.zip -Force
# на сервере:
cd /tmp && unzip -o fastscript-wiki.zip -d fastscript-wiki-src
sudo cp -r fastscript-wiki-src/wiki/docs/* /opt/fastscript-wiki/docs/
sudo cp fastscript-wiki-src/wiki/mkdocs.yml /opt/fastscript-wiki/mkdocs.yml
# на случай, если venv старше двуязычного плагина:
sudo /opt/fastscript-wiki/.venv/bin/pip install mkdocs-material mkdocs-static-i18n
sudo systemctl restart fastscript-wiki
# проверка, что отвечают оба языка:
curl -s http://127.0.0.1:8000/ | grep -o '<html lang="[^"]*"' | head -1
curl -s http://127.0.0.1:8000/ru/ | grep -o '<html lang="[^"]*"' | head -1
```

Сервис `mkdocs serve` подхватывает файлы и сам (livereload), рестарт —
для надёжности. Пересобирать venv не нужно.

## Шаг 5 (необязательно). Статика без сервиса

Если сервис не нужен вовсе:

```bash
/opt/fastscript-wiki/.venv/bin/mkdocs build -f /opt/fastscript-wiki/mkdocs.yml -d /opt/fastscript-wiki/site
```

и в nginx-блоке заменить `location /` на:

```nginx
root /opt/fastscript-wiki/site;
index index.html;
try_files $uri $uri/ $uri.html =404;
```

## Если что-то пошло не так

| Симптом | Действие |
|---|---|
| `install.sh` просит root | `sudo bash install.sh ...` |
| Сайт не открывается | `systemctl status fastscript-wiki`, `journalctl -u fastscript-wiki -n 50`, `nginx -t`, `curl 127.0.0.1:8000/` |
| 502 Bad Gateway | сервис не поднялся — смотреть `journalctl` (обычно pip/venv или занятый порт; порт меняется через `PORT=9000 sudo -E bash install.sh ...`) |
| 404 на вложенных страницах | `try_files` нужен только для static-варианта, не для proxy |
| Фаервол | `sudo ufw allow 80,443/tcp` |
| `/` и `/ru/` показывают один язык | протухший контент: `ls docs/` обязан показать подпапки `en/` **и** `ru/` — плоская раскладка `docs/*.md` с i18n-конфигом собирает один язык везде. Скопируйте свежие `docs/` **и** `mkdocs.yml` из зипа (см. шаг 4), проверьте плагин в рабочем окружении, рестарт |
| `site_lang: Unrecognised configuration name` | протухший `mkdocs.yml`, смешанный с новым — перекопируйте из зипа, иначе безвредно |

Локальный предпросмотр без сервера (для авторов):
`pip install mkdocs-material mkdocs-static-i18n && mkdocs serve` в папке `wiki/`.
