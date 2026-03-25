# Human Guided SDLC

## О проекте
Human Guided SDLC — это рабочая попытка построить контролируемую абстракцию над кодинг‑агентом. Идея в том, что энтерпрайз‑среде нужен управляемый рантайм: наблюдаемый, воспроизводимый и ограничиваемый по правилам выполнения. Этот проект делает шаг в эту сторону, комбинируя декларативные описания флоу, правила, навыки и аудит действий.

## Архитектура
Проект разделён на два компонента:
- Backend: Spring Boot (Java 21), REST API, хранение версий и статусов (draft/published), валидации и проверки.
- Frontend: React + Vite, UI для редакторов `flows`, `rules`, `skills` и управления версиями.

## Запуск (dev)
### Общие требования
- На компьютере должен быть установлен coding-agent Qwen Coder (`Qwen Code`): https://github.com/QwenLM/qwen-code#installation

### Backend
Требования: Java 21.
1. `cd /Users/nick/IdeaProjects/human-guided-development/backend`
2. `./gradlew bootRun`

По умолчанию API доступен на `http://localhost:8080`, база — H2 in‑memory (см. `backend/src/main/resources/application.yml`).

### Frontend
Требования: Node.js 18+ (или совместимый LTS).
1. `cd /Users/nick/IdeaProjects/human-guided-development/frontend`
2. `npm install`
3. `npm run dev`

Vite проксирует `/api` на `http://localhost:8080` (см. `frontend/vite.config.js`).

## Сборка
### Backend
1. `cd /Users/nick/IdeaProjects/human-guided-development/backend`
2. `./gradlew build`

### Frontend
1. `cd /Users/nick/IdeaProjects/human-guided-development/frontend`
2. `npm run build`

## Конфигурация
Ключевые параметры можно задавать через переменные окружения:
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` для подключения к БД.

## Ключевая идея
Энтерпрайзу нужен управляемый рантайм. Этот проект — практическая попытка построить контролируемую абстракцию над кодинг‑агентом, чтобы обеспечить прозрачность, контроль версий и безопасное выполнение сценариев.
Также такой рантайм может содержать ноды вызовов других ИИ‑агентов, что позволяет строить сложные графы исполнения.
