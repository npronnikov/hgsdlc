# План реализации

## Краткое резюме
Нужно добавить в `human_input` режим HTML-формы без изменения backend-контракта: если контент артефакта HTML, UI рендерит форму, перехватывает `submit`, извлекает ответы и сохраняет их как содержимое выходного артефакта, объявленного в `produced_artifacts` этой `human_input` ноды. Для Markdown/обычного текста остаётся текущий редактор. План состоит из 5 шагов; основной риск — безопасный рендер HTML и устойчивый сбор `question-answer` без схемы.

## Архитектурные решения
- **Решение 1**: Не менять API `POST /gates/{gateId}/submit-input` и backend-валидацию; передаём ответы как `content_base64` существующего modifiable артефакта.
Почему: текущий runtime уже хранит версии артефактов и проверяет “artifact modified”; это покрывает задачу без миграций БД и без новых DTO.
- **Решение 2**: Определять режим UI по контенту выбранного editable-артефакта (`html` c `<form>` vs markdown/text), а не по новым полям flow.
Почему: пользователь явно просит без схем и без межнодовой валидации.
- **Решение 3**: На submit HTML-формы сериализовать ответы в детерминированный текст (Q/A + JSON блок) и писать в тот же артефакт.
Почему: downstream-ноды получают привычный текстовый файл, но внутри есть структурированный блок ответов.
- **Решение 4**: Целевой артефакт для сохранения брать только из списка выходов `human_input` (runtime payload `human_input_artifacts`, который строится из modifiable `produced_artifacts`/`execution_context`), а не из произвольного выбранного файла в UI.
Почему: это выполняет контракт ноды и исключает запись ответа в неверный путь.

---

## Шаги реализации

### Шаг 1 — Выделить режимы отображения human_input (сложность: medium)
**Цель**: в `HumanGate` определить для выбранного editable-артефакта режим `html_form` или `text_editor`.

**Файлы**:
- `frontend/src/pages/HumanGate.jsx` — добавить функции `detectHumanInputViewMode(content, path)` и вычисление режима для `selectedEditable`.
- `frontend/src/pages/HumanGate.jsx` — оставить существующий markdown/text editor как fallback по умолчанию.

**Детали реализации**:
```javascript
function detectHumanInputViewMode(content, path) {
  const body = String(content || '').trim().toLowerCase();
  if (body.includes('<form') && (body.includes('<input') || body.includes('<textarea') || body.includes('<select'))) {
    return 'html_form';
  }
  return 'text_editor';
}
```

**Зависит от**: нет зависимостей

**Проверка**:
- `cd frontend && npm run build` — проект собирается
- Ручная проверка: для markdown-файла поведение не изменилось

---

### Шаг 2 — Рендер HTML-формы в контейнере и перехват submit (сложность: high)
**Цель**: отрисовать HTML в “воротах” и перехватить событие `submit` контейнером.

**Файлы**:
- `frontend/src/pages/HumanGate.jsx` — добавить HTML-container (`ref`) и `useEffect` с делегированием события `submit`.
- `frontend/src/styles.css` — добавить стили для блока формы (`.human-gate-html-form`, поля, кнопки).
- `frontend/package.json` — добавить безопасный sanitizer (например, `dompurify`) перед вставкой HTML.

**Детали реализации**:
```javascript
useEffect(() => {
  const el = htmlContainerRef.current;
  if (!el) return;
  const onSubmit = (event) => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    event.preventDefault();
    handleHtmlFormSubmit(form);
  };
  el.addEventListener('submit', onSubmit, true);
  return () => el.removeEventListener('submit', onSubmit, true);
}, [handleHtmlFormSubmit]);
```

**Зависит от**: шаг 1

**Проверка**:
- HTML из editable-артефакта визуально рендерится на странице `HumanGate`
- Нажатие кнопки `type="submit"` не вызывает перезагрузку страницы

---

### Шаг 3 — Сериализация question-answer и запись в выходной артефакт ноды (сложность: high)
**Цель**: при submit формы собрать ответы пользователя и превратить их в текст строго того артефакта, который объявлен выходом `human_input` ноды, после чего отправить текущий backend submit.

**Файлы**:
- `frontend/src/pages/HumanGate.jsx` — реализовать `collectFormAnswers(form)` + `serializeAnswers(answers)` + `resolveHumanInputOutputArtifact(...)`, затем обновлять `editedByPath[targetArtifact.path]`.
- `frontend/src/pages/HumanGate.jsx` — расширить `submitInput` для сценария auto-submit после HTML submit.

**Детали реализации**:
```javascript
function collectFormAnswers(form) {
  const fd = new FormData(form);
  const map = {};
  for (const [key, value] of fd.entries()) {
    if (map[key] === undefined) map[key] = value;
    else if (Array.isArray(map[key])) map[key].push(value);
    else map[key] = [map[key], value];
  }
  return map;
}

function serializeAnswers(answers) {
  return [
    '# Human Input Answers',
    '',
    '```json',
    JSON.stringify({ answers }, null, 2),
    '```',
  ].join('\n');
}

function resolveHumanInputOutputArtifact(editableArtifacts) {
  const required = editableArtifacts.find((item) => item?.required === true);
  return required || editableArtifacts[0] || null;
}
```

**Зависит от**: шаг 2

**Проверка**:
- После submit HTML-формы изменяется контент именно у выходного артефакта `human_input` ноды
- `POST /gates/{gateId}/submit-input` уходит в текущем формате (`artifacts[].content_base64`)
- Gate успешно проходит `on_submit` без backend-изменений

---

### Шаг 4 — UX-согласование кнопок submit (сложность: medium)
**Цель**: сделать поведение submit однозначным для пользователя в HTML-режиме.

**Файлы**:
- `frontend/src/pages/HumanGate.jsx` — выбрать одну политику:
  - `вариант A`: submit формы сразу вызывает `submitInput`;
  - `вариант B`: submit формы только сохраняет ответы локально, а верхняя кнопка `Submit input` отправляет gate.
- `frontend/src/styles.css` — визуальный статус “form captured / ready to submit”.

**Детали реализации**:
```javascript
const handleHtmlFormSubmit = async (form) => {
  const answers = collectFormAnswers(form);
  const serialized = serializeAnswers(answers);
  const targetArtifact = resolveHumanInputOutputArtifact(editableArtifacts);
  if (!targetArtifact) return;
  setEditedByPath((prev) => ({ ...prev, [targetArtifact.path]: serialized }));
  await submitInput({ onlyPath: targetArtifact.path, auto: true });
};
```

**Зависит от**: шаг 3

**Проверка**:
- Пользователь понимает, что произошло после нажатия submit (сообщение + статус)
- Нет двойной отправки (`form submit` + верхняя кнопка)

---

### Шаг 5 — Регрессия и e2e сценарий HTML/MD (сложность: medium)
**Цель**: убедиться, что новый режим не ломает существующий markdown human_input.

**Файлы**:
- `frontend/src/pages/HumanGate.jsx` — финальная полировка edge-cases.
- `frontend/src/styles.css` — адаптация мобильной вёрстки HTML-формы.
- `backend/src/test/java/ru/hgd/sdlc/runtime/RuntimeRegressionFlowTest.java` — (опционально) сценарий, где human_input получает уже изменённый контент и успешно сабмитится по текущему API.

**Детали реализации**:
```java
// backend остаётся на текущем контракте: artifacts[].content_base64
// покрываем только интеграционный happy-path submit
```

**Зависит от**: шаги 1–4

**Проверка**:
- `cd frontend && npm run build`
- `cd backend && ./gradlew test --tests ru.hgd.sdlc.runtime.RuntimeRegressionFlowTest`
- Ручной e2e:
  - AI выдаёт HTML с `<form>` -> HumanGate рендерит форму -> submit -> gate закрыт
  - AI выдаёт markdown -> открывается текущий editor -> submit работает как раньше

---

## Риски и способы их снижения

| Риск | Вероятность | Воздействие | Способ снижения |
|------|-------------|-------------|-----------------|
| `requirements.md` отсутствует в репозитории | med | low | Опираться на явно подтверждённые требования из диалога |
| Небезопасный HTML (скрипты/инъекции) | high | high | Санитизация HTML перед рендером + запрет inline-script |
| Потеря части ответов (`checkbox[]`, повторяющиеся name) | med | med | Нормализовать `FormData`: scalar/array, покрыть ручными кейсами |
| Required артефакт сочтётся “неизменённым” | med | high | На form submit всегда сериализовать ответы в новый текст и сравнивать с исходным контентом |
| Неоднозначный UX двух submit-кнопок | high | med | Зафиксировать единый сценарий (auto-submit или two-step) и явно отразить в UI |

## Критические точки

Критическая точка — выбор единой семантики submit в HTML-режиме (автосабмит формы vs двухшаговый submit). Если не зафиксировать до реализации, появятся гонки и двойные отправки.

## Итоговые критерии готовности

- [ ] `cd frontend && npm run build` — фронтенд собирается без ошибок
- [ ] HTML-контент с `<form>` в `human_input` рендерится в `HumanGate`
- [ ] Контейнер `HumanGate` перехватывает `submit` формы и извлекает ответы из `FormData`
- [ ] При submit ответы сохраняются в артефакт, объявленный выходом `human_input` ноды (`produced_artifacts`), и отправляются через текущий `/gates/{gateId}/submit-input`
- [ ] Markdown/text `human_input` работает без изменений
- [ ] После submit происходит переход по `on_submit` и gate закрывается штатно
