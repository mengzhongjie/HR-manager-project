# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build the entire project (skip tests for speed during development)
cd hr_manager_project && mvn clean compile -DskipTests

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=HrManagerProjectApplicationTests

# Package as executable JAR
mvn clean package -DskipTests

# Run the application (requires MongoDB + Redis running locally on default ports)
mvn spring-boot:run

# Quick compile check
mvn compile -q
```

## Prerequisites

- Java 17+
- MongoDB (default: localhost:27017, database: hrms_db)
- Redis (default: localhost:6379)
- No Maven wrapper needed — `mvn` in PATH is sufficient

## Architecture Overview

This is a **Spring Boot 3.5.16 + Thymeleaf** monolithic web app with two frontend modules (求职端/Seeker, HR管理端/HR Admin) and a **MongoDB + Redis** backend.

### Database Strategy
- **MongoDB** — primary document store for all business entities (seekers, candidates, interviews, offers, onboardings)
- **Redis** — resume parsing stream queue (`parsing_queue` via XADD/XREADGROUP/XACK), distributed locks (`state_lock:candidate:{id}`), temp resume storage (`resume_temp:{id}`)

### Resume Upload Flow (the most architecturally significant pipeline)
```
求职者上传PDF → PdfValidator (size + magic bytes)
  → FileStorageUtil (save to ./uploads/)
  → Redis Stream parsing_queue (XADD)
  → ResumeParserConsumer (@Scheduled, XREADGROUP/XACK)
  → ResumeParseAgent (AI parse via regex mock or real API)
  → Redis temp store (resume_temp:{id}, TTL=30min)
  → ResumeBatchPersistenceService (@Scheduled batch flush)
  → MongoDB candidates collection
```

### Agent Architecture (hot-pluggable via @ConditionalOnProperty)
```
HrAgent<T, R> interface → AgentRegistry (DI collects enabled agents)
  ├── ResumeParseAgent       — hr.agent.resume-parse.enabled=true
  └── CandidateQualifyAgent  — hr.agent.candidate-qualify.enabled=true
```
Agents are disabled by toggling properties in `application.properties`. Currently: resume-parse + candidate-qualify enabled, interview + offer agents are intentionally omitted (human-decided).

### State Machine (8 states, 3-layer consistency)
States: NEW → PENDING_ARCHIVE | INTERVIEW_INVITED → IN_INTERVIEW → WAITING_OFFER → OFFERED → ONBOARDED | REJECTED

Consistency: (1) MongoDB @Version optimistic lock, (2) Event Sourcing via statusHistory[], (3) Redis SETNX distributed lock

### Two Frontend Modules
- **求职端** `/seeker/*` — upload, status check, respond to interview/offer
- **HR管理端** `/hr/*` — position selection → per-position candidate list/detail/backup, cross-position interview/offer/onboarding management

## Key Configurations in application.properties

```properties
# Batch persist: 10 entries OR every 5 seconds
hr.resume.batch.size=10
hr.resume.batch.interval-ms=5000

# Mock AI mode (no external API needed)
hr.agent.ai.mock=true

# Hot-plug agents
hr.agent.resume-parse.enabled=true
hr.agent.candidate-qualify.enabled=true
```

## Important Patterns

- **Lombok `@Data`** used on all entities — Maven `maven-compiler-plugin` with `annotationProcessorPaths` is configured in pom.xml
- **Constructor injection** preferred over `@Autowired` field injection
- **Candidate filtering** done in-memory via `CandidateService.applyFilters()` (not Spring Data Specifications) — works fine for assessment-scale data
- **Status transitions** validated in `CandidateService.validateStatusTransition()` — any breach throws `IllegalStateException`
- **Interview round progression** enforced in `InterviewService.saveInterview()` — ROUND_1 → ROUND_2 → ROUND_3 sequence
- **Template variables** use Thymeleaf 3 syntax — use `th:action="@{'/path/' + ${var}}"` not `__${var}__` preprocessing
