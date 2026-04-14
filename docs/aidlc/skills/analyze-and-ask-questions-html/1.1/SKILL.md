---
name: Анализ проекта и генерация вопросов в HTML-формате
description: >
  Изучи кодовую базу и запрос пользователя, сформулируй 10–15 уточняющих вопросов.
  Сохрани их в виде html формы с кнопкой
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
<form id="surveyForm">
  <div>
    <p><strong>1. Какой цвет вам нравится больше всего?</strong></p>
    <label><input type="radio" name="color" value="Красный"> Красный</label><br>
    <label><input type="radio" name="color" value="Синий"> Синий</label><br>
    <label><input type="radio" name="color" value="Зелёный"> Зелёный</label><br>
    <label>
      <input type="radio" name="color" value="Другое">
      Другое:
      <input type="text" name="color_other" placeholder="Ваш вариант">
    </label>
  </div>

  <br>

  <div>
    <p><strong>2. Какие фрукты вы любите?</strong></p>
    <label><input type="checkbox" name="fruit" value="Яблоко"> Яблоко</label><br>
    <label><input type="checkbox" name="fruit" value="Банан"> Банан</label><br>
    <label><input type="checkbox" name="fruit" value="Апельсин"> Апельсин</label><br>
    <label>
      <input type="checkbox" name="fruit" value="Другое">
      Другое:
      <input type="text" name="fruit_other" placeholder="Ваш вариант">
    </label>
  </div>

  <br>

  <div>
    <p><strong>3. Напишите ваш комментарий:</strong></p>
    <textarea name="comment" placeholder="Введите ваш текст здесь..."></textarea>
  </div>

  <br>

  <button type="button" onclick="collectAnswers()">Собрать ответы</button>
</form>

<script>
  function collectAnswers() {
    const result = [];

    const colorSelected = document.querySelector('input[name="color"]:checked');
    let colorAnswer = '';
    if (colorSelected) {
      if (colorSelected.value === 'Другое') {
        colorAnswer = document.querySelector('input[name="color_other"]').value.trim() || 'Другое';
      } else {
        colorAnswer = colorSelected.value;
      }
    }

    result.push({
      question: 'Какой цвет вам нравится больше всего?',
      answer: colorAnswer
    });

    const fruitChecked = document.querySelectorAll('input[name="fruit"]:checked');
    const fruitAnswers = [];

    fruitChecked.forEach(item => {
      if (item.value === 'Другое') {
        const otherValue = document.querySelector('input[name="fruit_other"]').value.trim();
        fruitAnswers.push(otherValue || 'Другое');
      } else {
        fruitAnswers.push(item.value);
      }
    });

    result.push({
      question: 'Какие фрукты вы любите?',
      answer: fruitAnswers
    });

    const comment = document.querySelector('textarea[name="comment"]').value.trim();
    result.push({
      question: 'Напишите ваш комментарий:',
      answer: comment
    });

    console.log(result);
  }
</script>
```

**Правила:**
- Заменить все `{PLACEHOLDER}` реальным содержимым из анализа
- N в "Вопрос X из N" — общее количество вопросов
- У каждого вопроса 3-4 конкретных варианта ответа + "Другое"
- Никаких внешних шрифтов, картинок, скриптов — только inline CSS

---

## Ограничения

- Вопросы строго по делу — никаких вопросов ради количества
- Варианты ответов реалистичные и взаимоисключающие
- Не предлагать решений в вопросах — только прояснять требования
- Не писать никакого кода реализации
- Вывод — только файл: `questions.html` 
