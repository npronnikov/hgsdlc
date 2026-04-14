---
name: Анализ проекта и генерация вопросов в HTML-формате
description: >
  Изучи кодовую базу и запрос пользователя, сформулируй 10–15 уточняющих вопросов.
  Сохрани их в виде красивой HTML-страницы (questions.html) и шаблона для ответов (answers.md).
  Используется перед реализацией для снятия неоднозначностей.
---

# Анализ проекта и генерация вопросов в HTML-формате

## Цель

Снять неопределённость запроса до начала реализации: изучить проект,
сформулировать вопросы, ответы на которые напрямую влияют на выбор решения.
Сохранить вопросы в HTML-формате для удобного отображения в gate UI.

---

## Шаг 1 — Изучение проекта

Перед формулировкой вопросов изучи кодовую базу:

1. **Точка входа**: `README.md`, `build.gradle` / `pom.xml`, `package.json`, `application.yml`
2. **Структура**: найди основные слои и модули
3. **Доменная модель**: ключевые классы и их связи
4. **Существующие паттерны**: обработка ошибок, авторизация, валидация
5. **API поверхность**: что уже реализовано
6. **Тест-покрытие**: стиль и уровень тестов

Зафиксируй для себя: что уже есть и что нужно изменить согласно запросу.

---

## Шаг 2 — Анализ запроса

Определи:
- Какие части системы затронет изменение?
- Что неясно: бизнес-правила, граничные случаи, scope?
- Какие решения нужно принять до реализации?

---

## Шаг 3 — Генерация вопросов

Сформулируй от **10 до 15 вопросов**, покрывающих:

| Категория | Что выяснять |
|-----------|-------------|
| **Scope** | Что именно изменяется, что трогать нельзя |
| **Бизнес-правила** | Валидация, ограничения, граничные случаи |
| **API** | Формат, статусы, версионирование |
| **Данные** | Новые поля, миграции, совместимость |
| **Авторизация** | Роли, права доступа |
| **Тестирование** | Ожидаемый уровень покрытия |

Каждый вопрос — конкретный, привязанный к проекту и запросу.

---

## Шаг 4 — Создание questions.html

Создать файл `questions.html` в рабочей директории запуска.

Это самодостаточная HTML-страница (без внешних зависимостей, только inline CSS).
Строго соблюдать следующую структуру:

```html
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Уточняющие вопросы</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
         background: #f5f5f5; color: #1a1a1a; padding: 32px 16px; }
  .container { max-width: 760px; margin: 0 auto; }
  .header { background: #fff; border-radius: 8px; padding: 24px 28px;
            margin-bottom: 24px; border-left: 4px solid #1677ff; }
  .header h1 { font-size: 20px; font-weight: 600; color: #1677ff; margin-bottom: 8px; }
  .header .summary { font-size: 14px; color: #555; line-height: 1.6; }
  .question { background: #fff; border-radius: 8px; padding: 20px 24px;
              margin-bottom: 16px; border: 1px solid #e8e8e8; }
  .question-number { font-size: 11px; font-weight: 600; text-transform: uppercase;
                     letter-spacing: 0.5px; color: #1677ff; margin-bottom: 8px; }
  .question-text { font-size: 15px; font-weight: 500; color: #1a1a1a;
                   margin-bottom: 14px; line-height: 1.5; }
  .options { display: flex; flex-direction: column; gap: 8px; }
  .option { display: flex; align-items: flex-start; gap: 10px; padding: 10px 14px;
            border-radius: 6px; border: 1px solid #e8e8e8; background: #fafafa; }
  .option-label { font-size: 11px; font-weight: 700; color: #888;
                  min-width: 22px; padding-top: 1px; }
  .option-text { font-size: 14px; color: #333; line-height: 1.4; }
  .note { margin-top: 24px; padding: 14px 18px; background: #fffbe6;
          border: 1px solid #ffe58f; border-radius: 6px; font-size: 13px; color: #614700; }
</style>
</head>
<body>
<div class="container">
  <div class="header">
    <h1>Уточняющие вопросы</h1>
    <div class="summary">
      <!-- 2-3 предложения: что за проект, что запрашивается, в чём основная неясность -->
      {SUMMARY_TEXT}
    </div>
  </div>

  <!-- Повторить блок .question для каждого вопроса (10-15 штук) -->
  <div class="question">
    <div class="question-number">Вопрос 1 из N</div>
    <div class="question-text">{QUESTION_TEXT}</div>
    <div class="options">
      <div class="option">
        <span class="option-label">А</span>
        <span class="option-text">{OPTION_A}</span>
      </div>
      <div class="option">
        <span class="option-label">Б</span>
        <span class="option-text">{OPTION_B}</span>
      </div>
      <div class="option">
        <span class="option-label">В</span>
        <span class="option-text">{OPTION_C}</span>
      </div>
      <div class="option">
        <span class="option-label">—</span>
        <span class="option-text">Другое (укажи в answers.md)</span>
      </div>
    </div>
  </div>

  <div class="note">
    📝 Заполни ответы в файле <strong>answers.md</strong> в рабочей директории.
    Для каждого вопроса укажи букву выбранного варианта или напиши свой ответ.
  </div>
</div>
</body>
</html>
```

**Правила:**
- Заменить все `{PLACEHOLDER}` реальным содержимым из анализа
- N в "Вопрос X из N" — общее количество вопросов
- У каждого вопроса 3-4 конкретных варианта ответа + "Другое"
- Никаких внешних шрифтов, картинок, скриптов — только inline CSS

---

## Шаг 5 — Создание answers.md

Создать файл `answers.md` в рабочей директории запуска.

Шаблон строго соблюдать — одна строка на вопрос, владелец продукта заполняет пустое поле:

```markdown
# Ответы на уточняющие вопросы

Для каждого вопроса укажи букву варианта (А / Б / В) или напиши свой ответ.
Посмотри вопросы в файле questions.html.

---

**Вопрос 1. {КРАТКОЕ_НАЗВАНИЕ_ВОПРОСА}**
Ответ: 

**Вопрос 2. {КРАТКОЕ_НАЗВАНИЕ_ВОПРОСА}**
Ответ: 

... (все вопросы в том же формате)
```

**Правила:**
- Краткое название — 5-8 слов, суть вопроса
- Одна пустая строка "Ответ: " после каждого вопроса — владелец продукта допишет
- Количество вопросов совпадает с questions.html

---

## Ограничения

- Вопросы строго по делу — никаких вопросов ради количества
- Варианты ответов реалистичные и взаимоисключающие
- Не предлагать решений в вопросах — только прояснять требования
- Не писать никакого кода реализации
- Вывод — только два файла: `questions.html` и `answers.md`
