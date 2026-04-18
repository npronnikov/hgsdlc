# Performance Test Instructions

## Purpose

Performance тесты проверяют соответствие NFR требованиям для функциональности "Embeddings for Skills and Rules":

- **PERF-001**: Время отклика API поиска (p95 < 500ms)
- **PERF-002**: Время генерации embedding (< 500ms локально, < 1000ms с OpenAI)
- **PERF-003**: Производительность миграции (10-50 embeddings/sec)
- **PERF-004**: Оптимизация использования памяти (< 600 MB overhead)

---

## Setup

### 1. Performance Testing Environment

**Рекомендуется использовать выделенное окружение:**
- PostgreSQL на отдельной машине (не локальной)
- Тестовая БД с реалистичным объёмом данных (10,000+ записей)
- JVM с production settings (heap 2GB, G1GC)

**Запуск performance БД:**
```bash
docker run -d --name pgvector-perf \
  -e POSTGRES_USER=perf \
  -e POSTGRES_PASSWORD=perf \
  -e POSTGRES_DB=perf \
  -p 5434:5432 \
  -c shared_buffers=256MB \
  -c effective_cache_size=1GB \
  -c maintenance_work_mem=128MB \
  pgvector/pgvector:pg16

# Миграция схемы
docker exec -i pgvector-perf psql -U perf -d perf < backend/src/main/resources/db/changelog/048-embeddings-support.sql
```

---

### 2. Generate Test Data

**Создание 10,000 навыков для performance тестов:**

Скрипт: `backend/src/test/resources/perf/generate-data.sql`
```sql
-- Создаём 10,000 навыков с разными embedding векторами
INSERT INTO skills (id, skill_id, version, name, description, markdown, status, embedding_vector)
SELECT
    gen_random_uuid(),
    gen_random_uuid(),
    1,
    'Performance Test Skill ' || i,
    'Description for skill ' || i,
    '# Skill ' || i || '\n\nContent for performance testing.',
    'PUBLISHED',
    '[' || array_to_string(array_fill('0.1', ARRAY[384]), ',') || ']'::vector
FROM generate_series(1, 10000) AS s(i);
```

**Выполнение:**
```bash
docker exec -i pgvector-perf psql -U perf -d perf -f /path/to/generate-data.sql
```

---

## Load Testing Tools

### Рекомендуемые инструменты:

1. **JMeter** (GUI + CLI)
2. **k6** (CLI, JavaScript based)
3. **Gatling** (Scala DSL)
4. **Apache Bench (ab)** (простой CLI)

В этом документе используются **k6** и **JMeter**.

---

## Test Scenarios

### Scenario 1: API Search Performance (PERF-001)

**NFR Требование:**
- p50 (медиана): < 200ms
- p95: < 500ms
- p99: < 1000ms

#### k6 Script

Файл: `backend/src/test/resources/perf/search-performance.k6.js`
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 10 },   // Ramp up to 10 users
    { duration: '3m', target: 50 },   // Ramp up to 50 users
    { duration: '2m', target: 50 },   // Stay at 50 users
    { duration: '1m', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // NFR PERF-001
    http_req_failed: ['rate<0.01'],   // < 1% errors
  },
};

const BASE_URL = 'http://localhost:8080';
const AUTH_TOKEN = 'your-test-token-here'; // Получить через /api/auth/login

export default function () {
  // Случайный ID навыка (предполагается, что данные есть)
  const skillId = Math.floor(Math.random() * 10000) + 1;

  // Поиск похожих навыков
  const params = {
    headers: {
      'Authorization': `Bearer ${AUTH_TOKEN}`,
      'Content-Type': 'application/json',
    },
  };

  const res = http.get(
    `${BASE_URL}/api/skills/${skillId}/similar?threshold=0.6&limit=10`,
    params
  );

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has results': (r) => JSON.parse(r.body).total > 0,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });

  sleep(1);
}
```

**Запуск:**
```bash
# Установить k6 (если не установлен)
brew install k6  # macOS
# или
curl https://github.com/grafana/k6/releases/download/v0.47.0/k6-v0.47.0-linux-amd64.tar.gz -L | tar xvz

# Запуск теста
k6 run backend/src/test/resources/perf/search-performance.k6.js
```

**Ожидаемый результат:**
```
✓ status is 200
✓ has results
✓ response time < 500ms

checks.........................: 100.00% ✓ 2997  ✗ 0
http_req_duration..............: avg=156ms  min=50ms   med=145ms  max=890ms  p(95)=420ms
http_req_failed................: 0.00%   ✓ 0     ✗ 2997
```

**Ключевые метрики:**
- **p(95) < 500ms** ✅ (NFR PERF-001)
- **med < 200ms** ✅
- **Errors < 1%** ✅

---

#### JMeter Test Plan

Файл: `backend/src/test/resources/perf/search-performance.jmx`

**Настройка в JMeter GUI:**
1. Thread Group: 50 threads, 3 minutes
2. HTTP Request: GET `/api/skills/${skillId}/similar?threshold=0.6&limit=10`
3. HTTP Header Manager: `Authorization: Bearer ${token}`
4. View Results Tree: для дебага
5. Summary Report: для метрик
6. Aggregate Graph: для визуализации

**Запуск:**
```bash
jmeter -n -t backend/src/test/resources/perf/search-performance.jmx \
  -l results.jtl \
  -e -o report/
```

**Анализ:**
- Открыть `report/index.html` в браузере
- Проверить Percentiles (95%, 99%)

---

### Scenario 2: Embedding Generation Performance (PERF-002)

**NFR Требование:**
- Sentence-BERT local: < 500ms per document
- OpenAI API: < 1000ms (с network latency)

#### Unit Test with Performance Tracking

Файл: `backend/src/test/java/ru/hgd/sdlc/common/embedding/application/EmbeddingServicePerformanceTest.java`
```java
@Test
void testLocalEmbeddingGenerationPerformance() {
    String testText = "# Test Skill\n\nThis is a test skill for performance testing.";

    int iterations = 100;
    List<Long> durations = new ArrayList<>();

    for (int i = 0; i < iterations; i++) {
        long start = System.currentTimeMillis();

        float[] embedding = embeddingService.generateEmbedding(testText);

        long duration = System.currentTimeMillis() - start;
        durations.add(duration);

        assertNotNull(embedding);
        assertEquals(384, embedding.length);
    }

    // Статистика
    long avg = durations.stream().mapToLong(Long::longValue).sum() / iterations;
    long p95 = durations.stream().sorted().collect(Collectors.toList()).get((int) (iterations * 0.95));

    // NFR PERF-002: Local < 500ms
    assertTrue(p95 < 500, "p95=" + p95 + "ms, expected < 500ms");

    System.out.println("Average: " + avg + "ms, p95: " + p95 + "ms");
}
```

**Запуск:**
```bash
cd backend
./gradlew test --tests "*EmbeddingServicePerformanceTest"
```

**Ожидаемый результат:**
```
Average: 234ms, p95: 412ms
✅ p95 < 500ms
```

---

### Scenario 3: Migration Performance (PERF-003)

**NFR Требование:**
- Скорость: 10-50 embeddings/сек
- Время для 10,000 записей: < 30 минут

#### Integration Test

Файл: `backend/src/test/java/ru/hgd/sdlc/common/embedding/application/EmbeddingMigrationServicePerformanceTest.java`
```java
@Test
void testMigrationPerformance() {
    // Создаём 1000 тестовых навыков
    int skillCount = 1000;
    List<SkillVersion> skills = new ArrayList<>();
    for (int i = 0; i < skillCount; i++) {
        SkillVersion skill = new SkillVersion();
        skill.setName("Perf Test Skill " + i);
        skill.setMarkdown("# Skill " + i + "\n\nContent.");
        skill.setStatus(SkillStatus.PUBLISHED);
        skill.setEmbeddingVector(null);
        skills.add(skill);
    }
    skillRepository.saveAll(skills);

    // Замер времени миграции
    long start = System.currentTimeMillis();

    embeddingMigrationService.migratePublishedSkills();

    // Ожидание завершения (с таймаутом)
    await().atMost(10, TimeUnit.MINUTES)
        .until(() -> {
            long processed = skillRepository.findAll().stream()
                .filter(s -> s.getEmbeddingVector() != null)
                .count();
            return processed == skillCount;
        });

    long duration = System.currentTimeMillis() - start;

    // Расчёт throughput
    double throughput = (double) skillCount / (duration / 1000.0);

    // NFR PERF-003: 10-50 embeddings/sec
    assertTrue(throughput >= 10, "Throughput=" + throughput + "/sec, expected >= 10");
    assertTrue(throughput <= 100, "Throughput=" + throughput + "/sec, expected <= 100 (upper bound)");

    System.out.println("Migrated " + skillCount + " skills in " + duration + "ms");
    System.out.println("Throughput: " + throughput + " embeddings/sec");
}
```

**Запуск:**
```bash
cd backend
./gradlew test --tests "*EmbeddingMigrationServicePerformanceTest"
```

**Ожидаемый результат:**
```
Migrated 1000 skills in 34567ms
Throughput: 28.9 embeddings/sec
✅ 10 <= throughput <= 50
```

---

### Scenario 4: Memory Usage (PERF-004)

**NFR Требование:**
- Размер модели Sentence-BERT: ~400 MB в RAM
- Heap overhead: ~100-200 MB
- Total: ~500-600 MB дополнительной памяти

#### JVM Metrics Test

**Настройка JVM для метрик:**
```bash
cd backend
JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200" \
./gradlew bootRun
```

**Мониторинг через JConsole или VisualVM:**
```bash
# Запуск приложения с JMX
JAVA_OPTS="-Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.port=9010 \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.ssl=false" \
./gradlew bootRun

# Подключение через JConsole
jconsole localhost:9010
```

**Spring Boot Actuator Metrics:**
```bash
# Получение метрик памяти
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/jvm.memory.max
```

**Heap Dump Analysis (если память растёт):**
```bash
# Создание heap dump
jcmd <PID> GC.heap_dump /tmp/heapdump.hprof

# Анализ через VisualVM
visualvm --openfile /tmp/heapdump.hprof
```

**Ожидаемый результат:**
- Heap after startup: ~200-300 MB
- Heap after embedding model load: ~600-800 MB
- Heap after 10,000 embeddings: ~800-1000 MB (без утечек)

---

## Stress Testing

### Цель: Проверить поведение системы при экстремальной нагрузке

**NFR SCAL-002:** Поддержка до 100 параллельных пользователей

#### k6 Stress Test

Файл: `backend/src/test/resources/perf/stress-test.k6.js`
```javascript
export const options = {
  stages: [
    { duration: '2m', target: 100 },  // Ramp up to 100 concurrent users
    { duration: '5m', target: 100 },  // Stay at 100 users
    { duration: '2m', target: 200 },  // Spike to 200 users (overload)
    { duration: '2m', target: 200 },  // Stay at 200
    { duration: '1m', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'], // Меньше строгости при стрессе
    http_req_failed: ['rate<0.05'],    // < 5% errors допустимо
  },
};
```

**Запуск:**
```bash
k6 run backend/src/test/resources/perf/stress-test.k6.js
```

**Мониторинг системы:**
```bash
# CPU usage
top -p $(pgrep -f 'spring-boot:app')

# Memory usage
ps aux | grep 'spring-boot:app'

# DB connections
docker exec pgvector-perf psql -U perf -d perf -c "SELECT count(*) FROM pg_stat_activity;"
```

---

## Performance Baseline

После первого запуска performance тестов необходимо зафиксировать baseline:

**Файл:** `backend/src/test/resources/perf/baseline.json`
```json
{
  "test_date": "2026-04-18",
  "environment": {
    "java_version": "21",
    "heap_size": "2GB",
    "db_version": "PostgreSQL 16",
    "data_size": "10,000 skills"
  },
  "results": {
    "search_api": {
      "p50_ms": 156,
      "p95_ms": 420,
      "p99_ms": 780
    },
    "embedding_generation_local": {
      "p50_ms": 234,
      "p95_ms": 412
    },
    "migration_throughput_per_sec": 28.9,
    "memory_overhead_mb": 650
  }
}
```

**Сравнение с baseline в CI/CD:**
```bash
# Запустить тесты и сравнить с baseline
k6 run --out json=results.json backend/src/test/resources/perf/search-performance.k6.js
python scripts/compare-perf.py results.json baseline.json
```

---

## Continuous Integration

### Jenkins Pipeline

```groovy
stage('Performance Tests') {
    when {
        branch 'main'  # Только на main ветке
    }
    steps {
        script {
            sh 'cd backend && ./gradlew test --tests "*PerformanceTest"'

            // k6 load testing
            sh 'k6 run backend/src/test/resources/perf/search-performance.k6.js --summary-export=perf.json'

            // Проверка NFR thresholds
            script {
                def perf = readJSON file: 'perf.json'
                def p95 = perf.metrics.http_req_duration.values['p(95)']
                if (p95 > 500) {
                    error("NFR PERF-001 violated: p95=${p95}ms > 500ms")
                }
            }
        }
    }
}
```

### GitHub Actions

```yaml
- name: Performance Tests
  run: |
    cd backend
    ./gradlew test --tests "*PerformanceTest"

- name: k6 Load Test
  uses: grafana/k6-action@v0.3.1
  with:
    filename: backend/src/test/resources/perf/search-performance.k6.js
```

---

## Troubleshooting

### Issue: Slow API Response (> 500ms)

**Причины:**
1. Отсутствует индекс IVFFlat
2. Слишком много записей (> 50,000)
3. Недостаточно RAM для PostgreSQL

**Решение:**
```sql
-- Проверить наличие индекса
\d+ skills

-- Проверить использование индекса через EXPLAIN
EXPLAIN ANALYZE
SELECT * FROM skills
WHERE embedding_vector <=> '[0.1,0.2,...]'::vector < 0.4
ORDER BY embedding_vector <=> '[0.1,0.2,...]'::vector
LIMIT 10;

-- Если индекс не используется, пересоздать
DROP INDEX idx_skills_embedding_vector;
CREATE INDEX idx_skills_embedding_vector ON skills
USING ivfflat (embedding_vector vector_cosine_ops)
WITH (lists = 100);
```

---

### Issue: Low Migration Throughput (< 10/sec)

**Причины:**
1. Медленная генерация embeddings (CPU bottleneck)
2. Слишком маленький batch size
3. Блокировки БД

**Решение:**
```yaml
# application.yml: увеличить batch size
embedding:
  async:
    core-pool-size: 8  # Увеличить с 4
    max-pool-size: 16  # Увеличить с 8
```

---

### Issue: Memory Leaks (Heap постоянно растёт)

**Диагностика:**
```bash
# Heap dump через 1 час работы
jcmd <PID> GC.heap_dump /tmp/heap-after-1h.hprof

# Сравнение с начальным heap dump
jhat -J-Xmx2g /tmp/heap-startup.hprof &
jhat -J-Xmx2g /tmp/heap-after-1h.hprof &

# Найти объекты с наибольшим retention
# Обнаружить утечки в EmbeddingProvider или кэше
```

**Решение:**
```java
// Убедиться, что EmbeddingProvider - Singleton
@Component
public class LocalEmbeddingProvider implements EmbeddingProvider {
    private static volatile LocalEmbeddingProvider instance;

    public static LocalEmbeddingProvider getInstance() {
        if (instance == null) {
            synchronized (LocalEmbeddingProvider.class) {
                if (instance == null) {
                    instance = new LocalEmbeddingProvider();
                }
            }
        }
        return instance;
    }
}
```

---

## Quick Reference

| Задача | Команда |
|--------|---------|
| Load test (k6) | `k6 run backend/src/test/resources/perf/search-performance.k6.js` |
| Stress test | `k6 run backend/src/test/resources/perf/stress-test.k6.js` |
| Unit perf tests | `cd backend && ./gradlew test --tests "*PerformanceTest"` |
| JMX мониторинг | `jconsole localhost:9010` |
| Heap dump | `jcmd <PID> GC.heap_dump /tmp/heapdump.hprof` |
| NFR check (p95 < 500ms) | Проверить k6 summary report |
| NFR check (throughput) | Проверить логи миграции |
| Memory baseline | `curl http://localhost:8080/actuator/metrics/jvm.memory.used` |
