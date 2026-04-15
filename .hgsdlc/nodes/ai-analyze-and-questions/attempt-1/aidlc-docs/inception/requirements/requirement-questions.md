# Requirements Clarification Questions

## Request Classification
- **Clarity**: Vague
- **Type**: New Feature
- **Scope**: Multiple Components (backend API, database, frontend UI, ML/embedding service)
- **Complexity**: Complex

## Summary of Understanding
Пользователь хочет добавить функционал поиска "похожих" навыков (skills) и правил (rules) с использованием embeddings. Это предполагает внедрение ML-компонента для генерации embeddings и их хранения, изменения в базе данных, расширение API для поиска похожих элементов и обновление UI для отображения результатов. Основные неопределённости касаются выбора способа генерации embeddings (внешний сервис vs локальная модель), стратегии показа похожих элементов (в каталоге, в детальной карточке, при создании), и методов интеграции с существующей архитектурой.

---

## Functional Requirements

### Q1: Какие данные должны использоваться для генерации embeddings?
**Context**: В `SkillVersion` и `RuleVersion` есть несколько текстовых полей: `name`, `description`, `skillMarkdown`/`ruleMarkdown`, `tags`. Выбор полей существенно влияет на качество поиска похожих элементов.
**Options**:
A) Только name + description (краткий контекст, быстрое вычисление)
B) name + description + markdown-контент (полный контекст, точнее но медленнее)
C) Все поля включая tags (максимальный контекст, требует весовой схемы)
D) Включая метаданные (teamCode, platformCode, scope, skillKind/ruleKind)
X) Other (describe after [Answer]:)

[Answer]:

### Q2: Когда должны генерироваться/обновляться embeddings?
**Context**: Embeddings нужно вычислять и обновлять при определённых событиях жизненного цикла skill/rule. Выбор момента влияет на консистентность данных.
**Options**:
A) При сохранении draft (`POST /api/skills/{id}/save` с статусом DRAFT)
B) Только при публикации (после approve, когда статус меняется на PUBLISHED)
C) При изменении любого из зависимых полей (вне зависимости от статуса)
D) Фоновая задача переодически пересчитывает embeddings для всех PUBLISHED элементов
X) Other (describe after [Answer]:)

[Answer]:

### Q3: Где именно в UI должны показываться "похожие" элементы?
**Context**: В системе есть несколько точек взаимодействия: каталог (`Skills.jsx`, `Rules.jsx`), детальная карточка (`SkillEditor.jsx`, `RuleEditor.jsx`), возможно другие экраны. Размещение влияет на UX.
**Options**:
A) Только в детальной карточке skill/rule (секция "Similar skills")
B) В каталоге как дополнительная колонка или бейдж
C) В обоих местах (каталог + детальная карточка)
D) Как отдельная страница "Discover similar skills"
E) В виде popup/tooltip при наведении
X) Other (describe after [Answer]:)

[Answer]:

### Q4: Как должна выглядеть выдача "похожих" элементов?
**Context**: Пользователь должен понимать, почему элементы считаются похожими, и иметь возможность действовать с этими результатами.
**Options**:
A) Простой список похожих элементов (без метрики схожести)
B) Список с процентом сходства (similarity score)
C) С пояснением "почему похож" (ключевые слова или фразы)
D) Сгруппированный результат по типам сходства (например, "по функционалу", "по технологии")
E) С возможностью фильтровать/сортировать по степени сходства
X) Other (describe after [Answer]:)

[Answer]:

### Q5: Нужно ли искать похожие элементы и при создании skill/rule?
**Context**: Это может помочь избежать дубликатов ("don't reinvent the wheel") и показать существующие решения перед созданием нового.
**Options**:
A) Да, показывать похожие при создании/редактировании как подсказку
B) Нет, только в уже опубликованных элементах
C) Только если создаваемый skill/rule очень похож на существующий (warning)
D) Только при выборе определённых атрибутов (например, codingAgent, skillKind)
X) Other (describe after [Answer]:)

[Answer]:

### Q6: Должна ли быть интеграция с AI-агентом для уточняющих вопросов о похожих элементах?
**Context**: В системе есть Ask Agent функциональность для gates. Можно использовать её для интерактивного поиска похожих элементов.
**Options**:
A) Да, интегрировать с существующим Ask Agent
B) Нет, только статический список
C) Да, но только как отдельный endpoint (без связи с gates)
X) Other (describe after [Answer]:)

[Answer]:

### Q7: Должен ли функционал работать и для flows?
**Context**: В TODO.md упоминаются flows как часть каталога, но в запросе упомянуты только skills и rules.
**Options**:
A) Нет, только skills и rules как указано в запросе
B) Да, flows тоже должны иметь embeddings
C) flows позже (phase 2), но архитектура должна допускать расширение
X) Other (describe after [Answer]:)

[Answer]:

---

## Non-Functional Requirements

### Q8: Какая модель/сервис должна использоваться для генерации embeddings?
**Context**: Это ключевой архитектурный выбор, влияющий на стоимость, сложность, производительность и качество.
**Options**:
A) Внешний API (OpenAI embeddings, Cohere, etc.) - просто, но требует денег и интернета
B) Локальная модель (Sentence-BERT, etc.) - бесплатно, но требует ресурсов
C) Внешний сервис с fallback на локальную модель
D) Внедрить собственную lightweight модель (например, TF-IDF + cosine similarity)
X) Other (describe after [Answer]:)

[Answer]:

### Q9: Какое максимально допустимое время генерации embedding для одного элемента?
**Context**: Это влияет на выбор модели и UX при сохранении/публикации skills/rules.
**Options**:
A) < 1 секунда (требует быстрых моделей или кэша)
B) 1-5 секунд (приемлемо для большинства моделей)
C) 5-10 секунд (требует асинхронной обработки)
D) Не важно, можно в фоне (background job)
X) Other (describe after [Answer]:)

[Answer]:

### Q10: Какое максимальное количество "похожих" элементов должно возвращаться?
**Context**: Влияет на UI производительность и пользовательский опыт (слишком много результатов может быть шумом).
**Options**:
A) Фиксированное число (например, топ-5)
B) Фиксированное число (например, топ-10)
C) Настраиваемый параметр в системных настройках
D) Все элементы с similarity score выше порога
X) Other (describe after [Answer]:)

[Answer]:

### Q11: Насколько "похожими" должны быть элементы для попадания в результаты?
**Context**: Порог similarity score влияет на точность и полноту результатов. Слишком низкий порог = много шума, слишком высокий = мало результатов.
**Options**:
A) Жёсткий порог (например, > 0.8)
B) Средний порог (например, > 0.6)
C) Мягкий порог (например, > 0.4)
D) Настраиваемый порог в системных настройках
E) Динамический порог в зависимости от количества результатов
X) Other (describe after [Answer]:)

[Answer]:

### Q12: Как обрабатывать multilingual контент?
**Context**: Skills/rules могут быть на разных языках. Модель embeddings должна учитывать это для корректного поиска.
**Options**:
A) Поддерживать только один язык (например, английский)
B) Использовать multilingual модель embeddings
C) Отдельные embeddings для каждого языка
D) Определять язык и использовать соответствующую модель
X) Other (describe after [Answer]:)

[Answer]:

### Q13: Как обеспечить качество поиска (prevent false positives)?
**Context**: Embeddings могут давать неожиданные результаты ("похожие" элементы, которые на самом деле не связаны функционально).
**Options**:
A) Дополнительная фильтрация по metadata (tags, teamCode, scope)
B) Гибридный поиск (embeddings + текстовый поиск)
C) Ручная валидация результатов перед показом (curated)
D) Отсутствие гарантий, принимаем false positives как данность
X) Other (describe after [Answer]:)

[Answer]:

---

## User Scenarios

### Q14: Как пользователь должен использовать результаты поиска похожих элементов?
**Context**: Понимание intent помогает спроектировать правильный UX и API.
**Options**:
A) Только для информирования (reference, inspiration)
B) Для выбора существующего вместо создания нового (avoid duplication)
C) Для forking/клонирования похожего элемента
D) Для связывания/отношений между элементами (linked skills)
E) Комбинация вышеуказанного
X) Other (describe after [Answer]:)

[Answer]:

### Q15: Как должны обрабатываться ситуация, когда похожих элементов нет?
**Context**: Пользователь должен видеть понятный feedback, а не пустой экран.
**Options**:
A) Скрывать секцию "похожие" если результатов нет
B) Показывать "No similar skills found" message
C) Показывать предложение расширить критерии поиска (снизить порог)
D) Показывать "Популярные" или "Рекомендуемые" как fallback
X) Other (describe after [Answer]:)

[Answer]:

### Q16: Должен ли функционал быть доступен всем пользователям или только определённым ролям?
**Context**: В системе есть ролевая модель (ADMIN, FLOW_CONFIGURATOR, и др.). Доступ влияет на безопасность и бизнес-ценность.
**Options**:
A) Всем аутентифицированным пользователям
B) Только ADMIN и FLOW_CONFIGURATOR
C) Всем, но с разными уровнями детализации
D) Только для просмотра, но не для редактирования
X) Other (describe after [Answer]:)

[Answer]:

### Q17: Как пользователь может дать feedback о качестве рекомендаций?
**Context**: Feedback loop может улучшить качество системы overtime.
**Options**:
A) thumbs up/down для каждой рекомендации
B) Возможность manually "unlink" или "flag" как неуместное
C) Аналитика implicitly (какие рекомендации кликают)
D) Никакого feedback, static система
X) Other (describe after [Answer]:)

[Answer]:

---

## Business Context

### Q18: Какова бизнес-цель этой функции?
**Context**: Понимание "зачем" помогает приоритизировать требования и найти правильный баланс между сложностью и ценностью.
**Options**:
A) Уменьшение дублирования навыков (экономия времени)
B) Помощь новичкам в discovery существующих решений
C) Улучшение повторного использования (reuse) кода/знаний
D) Улучшение качества через benchmarking against similar skills
E) Комбинация вышеуказанного
X) Other (describe after [Answer]:)

[Answer]:

### Q19: Есть ли ограничения на стоимость (если используется внешний API для embeddings)?
**Context**: Внешние API (OpenAI, Cohere) требуют денег, что может быть ограничением для частых вызовов.
**Options**:
A) Бюджет не ограничен
B) Нужна бесплатная альтернатива (локальная модель)
C) Гибридная схема (кэш + внешний API)
D) Только для PUBLISHED элементов (не для drafts)
X) Other (describe after [Answer]:)

[Answer]:

### Q20: Какой success metric для этой функции?
**Context**: Нужен способ измерить, достигнута ли бизнес-цель.
**Options**:
A) Уменьшение количества duplicate skills/rules
B) Увеличение reuse существующих навыков
C) Пользовательский satisfaction (опросы)
D) Time saving при создании новых навыков
E) Комбинация метрик
X) Other (describe after [Answer]:)

[Answer]:

### Q21: Есть ли планы на monetization или внешнее использование этой функции?
**Context**: Это влияет на требования к scalability, security и API design.
**Options**:
A) Только внутреннее использование
B) Планируется对外开放 (external API)
C) Возможность продавать как feature
D) Неопределённо, но архитектура должна допускать расширение
X) Other (describe after [Answer]:)

[Answer]:

---

## Technical Context

### Q22: Как хранить embeddings в базе данных?
**Context**: Нужно выбрать структуру хранения, учитывая существующую схему PostgreSQL.
**Options**:
A) Новая колонка `embedding_vector` в таблицах `skills` и `rules` (тип vector или bytea)
B) Отдельная таблица `skill_embeddings` и `rule_embeddings` (1:1 relationship)
C) Отдельная таблица `embeddings` с polymorphic references (для будущего расширения)
D) Внешний vector store (Pinecone, Weaviate, etc.) с references в БД
X) Other (describe after [Answer]:)

[Answer]:

### Q23: Нужно ли использовать специализированное расширение PostgreSQL (например, pgvector)?
**Context**: pgvector позволяет выполнять similarity search напрямую в SQL, что упрощает архитектуру, но требует дополнительной настройки.
**Options**:
A) Да, использовать pgvector для vector similarity search
B) Нет, хранить как bytea и вычислять similarity в приложении
C) Использовать отдельный vector database
D) Использовать встроенные PostgreSQL средства (нет расширений)
X) Other (describe after [Answer]:)

[Answer]:

### Q24: Как будет выглядеть API для поиска похожих элементов?
**Context**: Нужно определить endpoint и contract для интеграции с frontend.
**Options**:
A) Новый endpoint `GET /api/skills/{id}/similar` (и для rules)
B) Параметр в существующем query endpoint `?similar_to={id}`
C) Новый endpoint `POST /api/similar` с универсальным запросом
D) WebSocket/Stream для real-time обновлений
X) Other (describe after [Answer]:)

[Answer]:

### Q25: Нужно ли поддерживать batch processing для генерации embeddings?
**Context**: При большом количестве существующих skills/rules может понадобиться миграция для генерации embeddings.
**Options**:
A) Да, нужна миграция для всех существующих PUBLISHED элементов
B) Только для новых/изменённых элементов (lazy migration)
C) Оперативная генерация по запросу (on-demand)
D) Фоновая переодическая пересчёт для обновления embeddings
X) Other (describe after [Answer]:)

[Answer]:

### Q26: Как обрабатывать обновление контента skill/rule?
**Context**: При изменении текста embedding может устареть. Нужно определить стратегию инвалидации.
**Options**:
A) Пересчитывать embedding при каждом save (event-driven)
B) Пересчитывать только при публикации (approve)
C) Batch переодический пересчёт для всех элементов
D) Версионировать embeddings (как и сами skills/rules)
X) Other (describe after [Answer]:)

[Answer]:

### Q27: Нужна ли кэширование для результатов поиска похожих элементов?
**Context**: Частые запросы могут быть медленными, кэш может улучшить производительность.
**Options**:
A) Да, кэшировать результаты в Redis (с TTL)
B) Да, кэшировать в памяти приложения
C) Нет, вычислять каждый раз заново (real-time)
D) Предварительно вычислять и хранить пары "похожих" элементов
X) Other (describe after [Answer]:)

[Answer]:

### Q28: Как обеспечить backward compatibility при внедрении embeddings?
**Context**: Существующий API и frontend не должны сломаться при добавлении нового функционала.
**Options**:
A) Добавить новые optional endpoints без изменения существующих
B) Расширить существующие responses с null/empty values для старых элементов
C) Версионировать API (v1 без embeddings, v2 с embeddings)
D) Breaking change acceptable (single version upgrade)
X) Other (describe after [Answer]:)

[Answer]:

---

## Quality Attributes

### Q29: Как тестировать качество рекомендаций?
**Context**: Нужна стратегия для валидации что "похожие" элементы действительно похожи.
**Options**:
A) Manual testing с curated dataset
B) A/B тестирование с пользователями
C) Метрики качества (precision/recall) на основе human labels
D) Unit тесты с mock data
E) Комбинация вышеуказанного
X) Other (describe after [Answer]:)

[Answer]:

### Q30: Как обеспечить maintainability системы embeddings?
**Context**: Модель embeddings может устареть, данные могут менять свойства. Нужен план обновления.
**Options**:
A) Версионировать модель embeddings (как dependency)
B) Плановый переобучения/обновления модели
C) Monitoring качества рекомендаций и alerts при деградации
D) Flexible architecture для легкой замены модели
E) Комбинация вышеуказанного
X) Other (describe after [Answer]:)

[Answer]:
