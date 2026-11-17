CLAUDE.md — Matessa


Read this first, every session. This file is the project's rules layer. It tells you (the agent) what the canonical references are, how to work on this project, and which skills apply when. When anything here conflicts with your defaults, this file wins unless the human says otherwise.



Project in one line

Matessa is an e-commerce platform: Spring Boot microservices (3 services + Eureka + API Gateway) with a Next.js frontend. It is an isolated sibling of the Adventure Dose platform (separate repo, databases, containers, and ports) targeting the Indian market. Roles: CUSTOMER, MANAGER, ADMIN. Java packages: com.matessa.*.

Port scheme (host side; offset so both stacks coexist on one machine): gateway 9080, user-service 9081, commercial-service 9082, order-service 9083, Eureka 9761, Postgres 5533/5534/5535, Redis 6380, Grafana 3002, Tempo 3201, Next dev 3100. Container-internal ports keep the conventional values (8080–8083, 8761, 5432, 6379).


Canonical references — SCAN BEFORE WRITING ANY CODE

These are the source of truth. Before generating, editing, or reviewing code, read the ones relevant to the task. Do not improvise around them; if you believe one is wrong, raise it with the human rather than silently diverging. This file (CLAUDE.md) is the hub/index — each doc below is the authority for its area.

DocAuthority overRead it when…DATA-MODEL.mdentities, fields, relationships, ownership, per-service DB split, shared-code policythe task touches data, persistence, or entity shapeARCHITECTURE.mdservices, infra, security (RS256 JWT filter chain), API conventions, Kafka, Docker, v1-vs-later phase tagsthe task touches services, security, API design, infra, or messagingapi.mdAPI endpoints, request/response shapes, error contractbuilding/consuming any API, or typing frontend props/dataBRAND.mdbrand identity — palette meaning, typography, voice, logo rules, imagery, guardrails (the what/why)any visual, copy, or brand-facing workDESIGN-SYSTEM.mdfrontend execution — design tokens, components, layout, mobile→desktop handoff, motion, hero animation, innovations (the how)building or styling any frontend UIFRONTEND-BUILD.mdtranslating the Claude Design HTML/Tailwind output into the Next.js app — process & code conventionsdoing the design→Next.js translation specifically
AUDIT-FOLLOWUP.mdstatus of the Nov 2026 full-stack audit (store isolation, security, scale-out, checkout) — what is fixed, deferred, open; how to run the tests; how to deploy on the VPStouching auth, checkout, tenancy/stores, the BFFs, deployment, or picking up open audit itemsadventure.refactor.md (to be supplied)the second storefront (`second-frontend/`, store `adventure`) refactor planworking on second-frontend once the file exists

Quick routing:


Entities/data → DATA-MODEL.md. Services/security/infra/messaging → ARCHITECTURE.md. (Most backend tasks touch both.)
Any API work, frontend or backend → also api.md.
Any frontend work → BRAND.md (identity) + DESIGN-SYSTEM.md (execution). If specifically translating the Claude Design output → also FRONTEND-BUILD.md.
When unsure which apply, they're listed above — open the relevant ones before coding.
Auth, checkout, stores/tenancy, BFF, deployment → also AUDIT-FOLLOWUP.md (open items + test/deploy recipes) and ARCHITECTURE.md §8.1–§8.2.



How to work on this project (working style)


Plan first, then execute on approval. For any non-trivial task, present a short plan — what you'll build, which files, which conventions apply — and wait for explicit go-ahead before writing code. Do not jump straight to implementation.
Respect the build order in ARCHITECTURE.md (Eureka → user-service → gateway → commercial → order → async). Get one service working end-to-end before starting the next; avoid leaving three services half-built.
Respect phase tags. Build [v1] items. Do NOT build [later] items (Redis, CDN, WebSocket, full Transactional Outbox) unless the human explicitly asks. Flag if you think a [later] item is needed early, but don't add it unprompted.
One service = one database. Never write a cross-service DB join or foreign key. Cross-service relationships are ID references resolved via Feign (sync) or Kafka events (async). See DATA-MODEL.md.
Ask, don't assume, on ambiguity. When a request is unclear or under-specified, ask a focused question before building. Prefer structured choices.
Outputs handoff-ready. Reference files and specs should be clean and immediately usable by another agent or session.


Hard conventions (from ARCHITECTURE.md §8, §12, §14 — summarized)


Money is BigDecimal, never double/float.
All @OneToMany are FetchType.LAZY.
@Version optimistic locking on ProductVariant (anti-oversell).
Soft deletes (active = false) on anything another service references — never hard-delete.
DTOs always at the API boundary — never expose JPA entities.
Pagination (Pageable) on every list endpoint.
One consistent error-response DTO shape across all three services.
JWT is RS256: only user-service holds the private key; all services validate with the public key.
Every service registers the JWT filter and re-validates — defense in depth, never trust "the gateway already checked."
Role comes only from the verified JWT; enforce ownership checks (is this the caller's own order?) separately from role checks.
Config via environment variables; never hard-code localhost for inter-container addresses (use service names); never commit secrets or the private key.
No shared-code module in v1. Services are self-contained; copying the JWT filter across services is fine. Do NOT create a common-lib/common-* module unless the human explicitly asks. NEVER put entities, repositories, or business logic in a shared module (the cardinal sin — it recouples the databases through code). For dependency-version consistency, prefer a parent POM / version catalog, not shared code. Full green/red lists: DATA-MODEL.md → "Shared code policy"; rationale: ARCHITECTURE.md §15.


Tooling defaults


npm, not pnpm/yarn, for the Next.js side.
Migrations: Flyway or Liquibase per service — not Hibernate ddl-auto beyond throwaway local dev.
Boilerplate: MapStruct (entity↔DTO) + Lombok.
API docs: springdoc-openapi per service (each declares its own dependency — do NOT centralize it in a shared module).
Resilience: Resilience4j on Feign calls (timeouts, circuit breakers).
Tests: Testcontainers for integration tests against real Postgres/Kafka.
Running backend tests here: JAVA_HOME must point at JDK 25 (Maven reads JAVA_HOME, not PATH); on this Windows host also disable Ryuk and prefer IPv6 localhost — exact recipe in AUDIT-FOLLOWUP.md §6.
Deployment target: a single VPS with Docker Compose; Caddy is the only public entry (docker-compose.prod.yml, deploy/Caddyfile).



Installed skills — when each applies

These skills are installed. Each fits a different phase — use the right one at the right time; do not run a skill outside its phase.

feature-forge — requirements & spec workshops

Use BEFORE building any new feature. Runs a structured PM-hat + Dev-hat workshop to produce EARS-format requirements, acceptance criteria, and an implementation checklist. This is the front door for turning a vague request ("build checkout") into a real spec.


Apply when: defining a new feature, gathering requirements, writing acceptance criteria, planning a feature's implementation.
Fits the working style: it's interactive (structured questions first) — which matches the plan-first approach above.
Output of a feature-forge session should conform to DATA-MODEL.md and ARCHITECTURE.md; if the spec implies a model or architecture change, surface that explicitly.


improve-codebase-architecture — refactor & interface review

Use AFTER code exists, not before. Surfaces shallow modules and tight coupling and proposes deeper-module refactors (Ousterhout's small-interface/large-implementation principle). Strong fit for keeping service interfaces clean.


Apply when: a service or module is built and you want to review/refactor it; assessing coupling or testability.
Do NOT apply: on an empty or near-empty repo — it has nothing to analyze.
Guardrail: it may propose interface designs (via parallel sub-agents) and RFCs. These are proposals reviewed against ARCHITECTURE.md, not overrides of it. If a proposal conflicts with a locked decision (database-per-service, RS256 JWT, snapshot pattern, etc.), flag the conflict to the human — do not silently refactor away an intentional decision.


find-skills — capability discovery

Use when you hit a capability gap. Helps locate and install other skills when a task needs something not covered here.


Apply when: the human asks "is there a skill for X" or a task clearly needs a capability no installed skill or this repo provides.
Quality bar: prefer well-installed, reputable-source skills (it ranks by install count, source reputation, stars). Don't install a pile of marginal skills — every installed skill costs context. Suggest, let the human confirm installs.


Skills to author later (not yet built)

As patterns get established by building them once by hand, consider authoring project skills under .claude/skills/: a new-service skill (standard layout + Eureka + JWT wiring), a new-endpoint skill (DTO + validation + pagination + status codes), a JPA-entity skill (BigDecimal/LAZY/@Version/soft-delete baked in). Author each AFTER the pattern exists, not speculatively. Restart Claude Code after adding a skill so it's picked up.


Quick decision guide


New feature requested → feature-forge to spec it → plan → (on approval) build per the two reference docs.
Touching data/entities → read DATA-MODEL.md first.
Touching services/security/API/infra → read ARCHITECTURE.md first.
Code already built, want it cleaner → improve-codebase-architecture (review against the docs, don't override them).
Need a capability nothing here covers → find-skills.
Unsure or request is ambiguous → ask a focused question before coding.