# Production Deployment on One VM (docker compose)

Документ описывает продакшен-развертывание на **одной Linux VM** c тремя сервисами в одном compose:
- `postgres`
- `backend` (Spring Boot + qwen CLI)
- `frontend` (nginx + статические файлы)

## 1. Preconditions

На VM должны быть установлены:
- Docker Engine
- Docker Compose plugin (`docker compose`)
- доступ к registry с образами backend/frontend

Проверка:

```bash
docker --version
docker compose version
```

## 2. Подготовка директорий на VM

```bash
sudo mkdir -p /opt/hgsdlc/compose
sudo mkdir -p /opt/hgsdlc/secrets
sudo mkdir -p /opt/hgsdlc/workspace
sudo mkdir -p /opt/hgsdlc/backups/postgres
```

Скопируйте папку `deploy/` из репозитория на VM, например в `/opt/hgsdlc/compose`.

## 3. Секреты и env

1. Создайте env-файл:

```bash
cd /opt/hgsdlc/compose
cp .env.prod.example .env.prod
```

2. Отредактируйте `.env.prod`:
- `BACKEND_IMAGE_REPO`, `BACKEND_TAG`
- `FRONTEND_IMAGE_REPO`, `FRONTEND_TAG`
- `POSTGRES_PASSWORD`
- `QWEN_SETTINGS_FILE=/opt/hgsdlc/secrets/qwen-settings.json`

3. Положите `qwen settings.json`:

```bash
sudo cp /path/to/your/settings.json /opt/hgsdlc/secrets/qwen-settings.json
sudo chmod 600 /opt/hgsdlc/secrets/qwen-settings.json
```

Важно: файл монтируется в backend-контейнер read-only как `/home/app/.qwen/settings.json`.

## 4. Сборка и публикация образов

Обычно делается в CI. Если нужно собрать вручную:

```bash
cd /path/to/repo

docker build -f deploy/backend.Dockerfile -t ghcr.io/your-org/hgsdlc-backend:1.0.0 .
docker build -f deploy/frontend.Dockerfile -t ghcr.io/your-org/hgsdlc-frontend:1.0.0 .

docker push ghcr.io/your-org/hgsdlc-backend:1.0.0
docker push ghcr.io/your-org/hgsdlc-frontend:1.0.0
```

Backend-образ устанавливает `qwen` фиксированной версии через `QWEN_CLI_VERSION` в `backend.Dockerfile`.

## 5. Первый запуск (one command)

```bash
cd /opt/hgsdlc/compose
./deploy.sh ./.env.prod
```

Скрипт делает:
- `docker compose pull`
- `docker compose up -d --remove-orphans`
- ожидание healthchecks (`postgres`, `backend`, `frontend`)

## 6. Проверка после запуска

```bash
docker compose --env-file .env.prod -f compose.prod.yml ps
curl -fsS http://127.0.0.1:${BACKEND_PORT:-8080}/actuator/health
curl -fsS http://127.0.0.1:${FRONTEND_PORT:-80}/healthz
```

Логи backend (в т.ч. проверка qwen на старте):

```bash
docker compose --env-file .env.prod -f compose.prod.yml logs --tail=200 backend
```

## 7. Обновление версии

1. Поменяйте теги в `.env.prod`:
- `BACKEND_PREVIOUS_TAG=<текущий BACKEND_TAG>`
- `BACKEND_TAG=<новый тег>`
- `FRONTEND_PREVIOUS_TAG=<текущий FRONTEND_TAG>`
- `FRONTEND_TAG=<новый тег>`

2. Выполните:

```bash
./deploy.sh ./.env.prod
```

## 8. Откат

`rollback.sh` меняет местами `*_TAG` и `*_PREVIOUS_TAG`, тянет образы и перезапускает сервисы.

```bash
./rollback.sh ./.env.prod
```

## 9. Бэкап PostgreSQL

Ручной запуск:

```bash
./backup-postgres.sh ./.env.prod
```

Что делает скрипт:
- `pg_dump` из контейнера postgres
- сохраняет `.sql.gz` в `BACKUP_DIR`
- удаляет старые бэкапы по `BACKUP_RETENTION_DAYS`

Рекомендуется запускать по cron/systemd timer (ежедневно).

## 10. Restore PostgreSQL

1. Остановите приложение (минимум backend, лучше backend+frontend):

```bash
docker compose --env-file .env.prod -f compose.prod.yml stop backend frontend
```

2. Восстановите дамп:

```bash
gunzip -c /opt/hgsdlc/backups/postgres/postgres-sdlc-YYYYMMDDTHHMMSSZ.sql.gz \
  | docker compose --env-file .env.prod -f compose.prod.yml exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
```

Если переменные не экспортированы в shell, подставьте значения вручную (`-U sdlc -d sdlc`).

3. Поднимите приложение:

```bash
docker compose --env-file .env.prod -f compose.prod.yml up -d backend frontend
```

## 11. Hardening (minimum)

- Не храните `.env.prod` и `qwen-settings.json` в git.
- Права на секреты: `600`, владелец root/ops.
- Используйте immutable tags (не `latest`).
- Держите TLS на внешнем reverse proxy или добавьте TLS-терминацию в frontend слой.
- Регулярно тестируйте restore из backup на отдельной среде.

## 12. Troubleshooting

- Backend unhealthy:
  - проверить `qwen settings.json` path и права
  - проверить `docker compose logs backend`
- Postgres unhealthy:
  - проверить `POSTGRES_PASSWORD`, volume и свободное место
- Frontend недоступен:
  - проверить порт `FRONTEND_PORT`
  - проверить `docker compose logs frontend`
