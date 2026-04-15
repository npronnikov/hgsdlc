---
name: AIDLC NFR Requirements
description: >
  Assess non-functional requirements for a single unit of work — scalability,
  performance, availability, security, reliability, maintainability — and make
  explicit technology stack decisions. Produces nfr-requirements.md and
  tech-stack-decisions.md.
---

# AIDLC NFR Requirements

## Goal
Make explicit, recorded decisions about system quality attributes and technology
choices before code generation begins, so the implementation is built right
from the start rather than retrofitted.

---

## Step 1 — Load Unit Context

Read:
- `aidlc-docs/construction/{unit-name}/functional-design/` (all files)
- `aidlc-docs/inception/requirements/requirements.md` (NFR mentions)
- `aidlc-docs/inception/reverse-engineering/technology-stack.md` (if brownfield)

---

## Step 2 — Assess Each NFR Category

Evaluate ALL categories; for each identify the requirement level and constraints:

### Scalability
- Expected load: requests/second, concurrent users, data volume
- Growth rate over 1 year
- Horizontal vs vertical scaling preference
- Stateless vs stateful design requirements

### Performance
- Response time target (P50, P95, P99)
- Throughput target
- Acceptable latency for background operations
- Caching requirements

### Availability
- Uptime SLA (99%, 99.9%, 99.99%)
- Disaster recovery RPO / RTO
- Failover strategy
- Graceful degradation requirements

### Security
- Authentication mechanism (JWT, session, API key)
- Authorization model (RBAC, ABAC, ACL)
- Data sensitivity level (PII, financial, public)
- Encryption requirements (at rest, in transit)
- Compliance requirements (GDPR, PCI-DSS, HIPAA)

### Reliability
- Error handling strategy (retry, circuit breaker, fallback)
- Idempotency requirements
- Data consistency model (strong, eventual)
- Monitoring and alerting requirements

### Maintainability
- Code quality standards (linting, formatting)
- Documentation requirements
- Test coverage minimum (unit, integration)
- Observability: logging format, trace IDs, metrics

---

## Step 3 — Make Technology Stack Decisions

For each technology choice, record:
- Selected technology and version
- Rationale (why this over alternatives)
- Constraints it imposes on implementation

Typical decisions to cover: framework, ORM/data access layer, cache layer, message
broker, auth library, test framework, API documentation tool.

---

## Output Format

Save `aidlc-docs/construction/{unit-name}/nfr-requirements/nfr-requirements.md`:

```markdown
# NFR Requirements — [unit-name]

## Scalability
- **Expected Load**: [req/s, concurrent users]
- **Scaling Strategy**: [horizontal / vertical / auto]
- **Design Implication**: [stateless, connection pooling, etc.]

## Performance
- **Response Time Target**: P95 < [X]ms
- **Throughput**: [X] req/s
- **Caching**: [strategy if applicable]

## Availability
- **SLA**: [%]
- **Failover**: [strategy]
- **Recovery**: RPO=[X], RTO=[X]

## Security
- **Auth**: [mechanism]
- **Authorization**: [model]
- **Data Sensitivity**: [level]
- **Compliance**: [requirements or N/A]

## Reliability
- **Error Strategy**: [retry / circuit breaker / fail-fast]
- **Consistency**: [strong / eventual]
- **Monitoring**: [tool and key metrics]

## Maintainability
- **Test Coverage Minimum**: [%]
- **Logging Format**: [JSON / plain, fields required]
- **Documentation**: [what must be documented]
```

Save `aidlc-docs/construction/{unit-name}/nfr-requirements/tech-stack-decisions.md`:

```markdown
# Technology Stack Decisions — [unit-name]

| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Framework | [e.g., Spring Boot] | [3.3] | [reason] |
| ORM | [e.g., Hibernate JPA] | [6.x] | [reason] |
| Cache | [e.g., Redis] | [7.x] | [reason or N/A] |
| Auth | [e.g., JWT + Spring Security] | | [reason] |
| Testing | [e.g., JUnit 5 + Testcontainers] | | [reason] |

## Constraints Imposed
- [Constraint 1]: [what the technology choice requires or forbids]
```

---

## Constraints

- Do NOT write implementation code.
- Every technology decision must have explicit rationale.
- Brownfield: prefer the existing technology stack unless there is a documented,
  justified reason to introduce a new dependency.
- Mark any NFR category as N/A only if it genuinely does not apply to this unit,
  with a brief explanation.
