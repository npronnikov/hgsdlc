# Обзор проекта — Human Guided SDLC

## Назначение

**Human Guided SDLC** — управляемая платформа исполнения AI coding-агентов для корпоративной среды. Обеспечивает наблюдаемый, воспроизводимый и контролируемый рантайм поверх coding-агентов: аудит всех действий, человеческий контроль в ключевых точках и версионирование всех артефактов.

**Ключевая идея:** корпоративная среда требует не просто «AI пишет код», а управляемого pipeline с точками ревью, правилами выполнения и возможностью отката.

---

## Тип репозитория

**Multi-part** — 4 самостоятельные части в одном репозитории.

| Часть | Тип | Технологии | Корень |
|-------|-----|-----------|--------|
| Backend | `backend` | Java 21 + Spring Boot 3.3 | `backend/` |
| Frontend | `web` | React 18 + Vite 5 + Ant Design 5 | `frontend/` |
| Infrastructure | `infra` | Docker Compose | `infra/` |
| Catalog Repo | `data` | YAML-артефакты | `catalog-repo/` |

---

## Технологический стек

### Backend
| Категория | Технология | Версия |
|-----------|-----------|--------|
| Язык | Java | 21 |
| Фреймворк | Spring Boot | 3.3.0 |
| Безопасность | Spring Security | 6.x |
| Персистентность | Spring Data JPA + Hibernate | 6.x |
| БД (prod) | PostgreSQL | 16 |
| БД (dev/test) | H2 in-memory (MODE=PostgreSQL) | — |
| Миграции | Liquibase | — |
| Сериализация | Jackson | 2.17.1 |
| Валидация схем | json-schema-validator (networknt) | 1.0.87 |
| CLI | Spring Shell | 3.2.0 |
| Генерация кода | Lombok | 1.18.42 |
| Криптография | Bouncy Castle | 1.78.1 |
| Тесты | Testcontainers + JUnit 5 | 1.19.8 |
| Сборка | Gradle Kotlin DSL | — |
| Группа артефактов | `ru.hgd` | — |

### Frontend
| Категория | Технология | Версия |
|-----------|-----------|--------|
| Фреймворк | React | 18.2.0 |
| Сборщик | Vite | 5.2.0 |
| UI-библиотека | Ant Design | 5.15.0 |
| Маршрутизация | React Router DOM | 6.22.0 |
| Граф нод | ReactFlow + dagre | 11.11.3 / 0.8.5 |
| Редактор кода | Monaco Editor | 4.6.0 |
| Рендеринг Markdown | react-markdown + remark-gfm | 9.0.1 / 4.0.0 |
| YAML-парсинг | yaml | 2.4.5 |

---

## Архитектурный тип

| Характеристика | Значение |
|----------------|----------|
| Паттерн backend | Модульная DDD (Domain-Driven Design) |
| Слои | api → application → domain → infrastructure |
| Runtime-паттерн | Event-sourcing (audit_events с монотонным sequence_no) |
| Персистентность | CQRS-lite: оптимистичная блокировка через `@Version` |
| Фронтенд-паттерн | SPA с HashRouter, локальный state per page |
| Интеграция агентов | Strategy pattern (CodingAgentStrategy) |

---

## Домен

Система работает с 6 ключевыми доменными объектами:

```
Flow ──────────────── запускается как ──► Run
  │                                         │
  ├─ rule_refs ──► Rule (правила агента)     ├─ NodeExecution (выполнение ноды)
  │                                         │    │
  └─ nodes[].skill_refs ──► Skill (навыки)  │    └─► GateInstance (human контроль)
                                            │
Project ◄─────────────── принадлежит ───── │
                                            └─► ArtifactVersion (результаты)
                                            └─► AuditEvent (полный аудит)
```

---

## Текущее состояние (2026-03-26)

- Реализован один coding-агент: **Qwen Code** (`QwenCodingAgentStrategy`)
- Publication pipeline: поддержка flows, rules, skills
- Каталог: один опубликованный flow (`restore-architecture-flow@1.0`)
- 32 DB-миграции — схема активно развивается
- Frontend: полный набор экранов для работы оператора и ревьюера

---

## Ссылки на документацию

- [Архитектура системы](./architecture.md)
- [Анализ структуры кода](./source-tree-analysis.md)
- [Интеграционная архитектура](./integration-architecture.md)
- [API Контракты](./api-contracts-backend.md)
- [Модели данных](./data-models-backend.md)
- [Руководство по разработке](./development-guide.md)
- [UI-компоненты](./ui-components-frontend.md)
