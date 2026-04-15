# AIDLC Pipeline — стадии и поток артефактов

**Легенда:**
- 🟢 **всегда** выполняется
- 🟠 **условно** (зависит от типа проекта / решения AI)
- `→` сплошная стрелка — основная цепочка выполнения
- `-.->` пунктир — кросс-стадийное потребление артефакта

---

## Полный пайплайн

```mermaid
flowchart TD
    classDef always fill:#2E7D32,stroke:#1B5E20,color:#fff,font-weight:bold
    classDef cond   fill:#E65100,stroke:#BF360C,color:#fff
    classDef term   fill:#283593,stroke:#1A237E,color:#fff

    subgraph INC["🔵 INCEPTION"]
        direction TB
        WD["🟢 Workspace Detection\n─────────────\n✅ human gate"]:::always
        RE["🟠 Reverse Engineering\nbrowfield only\n─────────────\n✅ human gate"]:::cond
        RA["🟢 Requirements Analysis\n─────────────\n✅ human gate"]:::always
        US["🟠 User Stories\n─────────────\n✅ human gate"]:::cond
        AD["🟠 Application Design\n─────────────\n✅ human gate"]:::cond
        UD["🟠 Units Decomposition\n─────────────\n✅ human gate"]:::cond
        WP["🟢 Workflow Planning\n─────────────\n✅ human gate"]:::always
    end

    subgraph CON["🟢 CONSTRUCTION (per unit)"]
        direction TB
        FD["🟠 Functional Design\n─────────────\n✅ human gate"]:::cond
        NFR["🟠 NFR Requirements\n─────────────\n✅ human gate"]:::cond
        CGP["🟢 Code Gen Plan\n─────────────\n✅ human gate"]:::always
        CGE["🟢 Code Gen Execute\n─────────────\n✅ human gate"]:::always
        BT["🟢 Build & Test\n─────────────\n✅ human gate"]:::always
    end

    TERM(["✅ Complete"]):::term

    %% ── Основная цепочка с артефактами ──────────────────────────────────────
    WD  -->|"📄 aidlc-state.md"| RE
    RE  -->|"📄 architecture.md\n📄 component-inventory.md\n📄 technology-stack.md\n📄 code-structure.md\n📄 api-documentation.md\n📄 dependencies.md\n📄 business-overview.md\n📄 code-quality-assessment.md"| RA
    RA  -->|"📄 requirements.md"| US
    US  -->|"📄 stories.md\n📄 personas.md"| AD
    AD  -->|"📄 application-design.md"| UD
    UD  -->|"📄 unit-of-work.md\n📄 unit-of-work-dependency.md\n📄 unit-of-work-story-map.md"| WP
    WP  -->|"📄 execution-plan.md"| FD
    FD  -->|"📄 domain-entities.md\n📄 business-rules.md\n📄 data-flow.md"| NFR
    NFR -->|"📄 nfr-requirements.md\n📄 tech-stack-decisions.md"| CGP
    CGP -->|"📄 code-generation-plan.md"| CGE
    CGE -->|"🔧 workspace mutations"| BT
    BT  -->|"📄 build-instructions.md\n📄 test-instructions.md\n📄 build-and-test-summary.md"| TERM

    %% ── Кросс-стадийные зависимости (пунктир) ───────────────────────────────

    %% requirements.md уходит дальше по пайплайну
    RA -.->|requirements.md| AD
    RA -.->|requirements.md| WP
    RA -.->|requirements.md| FD
    RA -.->|requirements.md| NFR
    RA -.->|requirements.md| CGP

    %% RE артефакты нужны при генерации кода в brownfield
    RE -.->|RE artifacts| CGP

    %% stories.md нужны Workflow Planning
    US -.->|stories.md| WP

    %% unit-of-work.md нужен Functional Design и Code Gen Plan
    UD -.->|unit-of-work.md| FD
    UD -.->|unit-of-work.md| CGP

    %% application-design.md нужен Code Gen Plan
    AD -.->|application-design.md| CGP

    %% execution-plan.md задаёт какие стадии выполнять
    WP -.->|execution-plan.md| NFR
    WP -.->|execution-plan.md| CGP

    %% functional design artifacts нужны Code Gen Plan
    FD -.->|functional design| CGP
```

---

## Таблица артефактов: кто создаёт → кто потребляет

| Артефакт | Создаётся в | Потребляется в |
|---|---|---|
| `aidlc-state.md` | Workspace Detection | Reverse Engineering |
| `architecture.md` | Reverse Engineering | Requirements Analysis, **Code Gen Plan** |
| `component-inventory.md` | Reverse Engineering | Requirements Analysis |
| `technology-stack.md` | Reverse Engineering | Requirements Analysis |
| `code-structure.md` | Reverse Engineering | **Code Gen Plan** |
| `api-documentation.md` | Reverse Engineering | **Code Gen Plan** |
| `dependencies.md` | Reverse Engineering | **Code Gen Plan** |
| `business-overview.md` | Reverse Engineering | Requirements Analysis |
| `code-quality-assessment.md` | Reverse Engineering | Requirements Analysis |
| `requirements.md` | Requirements Analysis | User Stories, Application Design, **Workflow Planning**, Functional Design, NFR Requirements, **Code Gen Plan** |
| `stories.md` + `personas.md` | User Stories | Application Design, Workflow Planning |
| `application-design.md` | Application Design | Units Decomposition, **Code Gen Plan** |
| `unit-of-work.md` + 2 | Units Decomposition | Workflow Planning, Functional Design, **Code Gen Plan** |
| `execution-plan.md` | Workflow Planning | Functional Design, NFR Requirements, **Code Gen Plan** |
| `domain-entities.md` | Functional Design | **Code Gen Plan** |
| `business-rules.md` | Functional Design | **Code Gen Plan** |
| `data-flow.md` | Functional Design | **Code Gen Plan** |
| `nfr-requirements.md` | NFR Requirements | **Code Gen Plan** |
| `tech-stack-decisions.md` | NFR Requirements | **Code Gen Plan** |
| `code-generation-plan.md` | Code Gen Plan | Code Gen Execute |
| workspace mutations | Code Gen Execute | Build & Test |
| `build-and-test-summary.md` + 2 | Build & Test | — |

> **Паттерн:** `requirements.md` и RE-артефакты — самые "широкие" артефакты пайплайна.
> Все дороги ведут в **Code Gen Plan** — он агрегирует все предыдущие артефакты.
