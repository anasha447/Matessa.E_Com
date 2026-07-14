# AGENTS.md — Adventure Dose

Cross-tool agent guide: structure, commands, and git rules. For project rules, conventions, and skill usage, see **`CLAUDE.md`**. For data → **`DATA-MODEL.md`**; for architecture/security → **`ARCHITECTURE.md`**. Read those before writing code.

## Project
Adventure Dose: Spring Boot microservices (3 services + Eureka + API Gateway) + Next.js frontend. E-commerce for clothes, helmets, artwork.

## Structure (monorepo, git root = project root)
```
adventure-dose/
├── backend/  (eureka, gateway, user-service, commercial-service, order-service)
├── frontend/ (Next.js)
├── docker-compose.yml
└── *.md (CLAUDE, AGENTS, DATA-MODEL, ARCHITECTURE)
```

## Commands
- Boot infra/stack: `docker compose up -d`  ·  stop: `docker compose down`  ·  wipe data: `docker compose down -v`
- Build one service: `mvn -pl backend/<service> clean package`
- Test one service: `mvn -pl backend/<service> test`
- Integration tests use Testcontainers (real Postgres/Kafka).
- Frontend: `npm install` · `npm run dev` · `npm run build` (npm only — not pnpm/yarn).

## Git rules
- Repo is initialized at the **project root** (monorepo). `.gitignore` must exist before the first commit and must exclude: `target/`, `build/`, `node_modules/`, `.env*`, the RS256 private key, `.idea/`, `.vscode/`.
- **Commit style:** Conventional Commits — `feat:`, `fix:`, `chore:`, `refactor:`, `test:`, `docs:`. One logical working change per commit.
- **When to commit:** at every working checkpoint (a service boots, an endpoint passes a test, before a risky refactor). Small and frequent over large and rare.
- **Agent boundaries:**
  - Agent MAY: stage changes, draft commit messages, propose when to commit, propose branches/PRs.
  - Human RUNS or explicitly approves: every commit, every push, every merge.
  - Agent MUST NOT: commit secrets/`.env`/private keys (verify `.gitignore` first); force-push, rewrite history, hard-reset, or `down -v` without explicit confirmation.
- Solo branch model: `main` + short-lived feature branches. No GitFlow.

## Definition of done
A task is done when: code builds, its tests pass, it follows the conventions in CLAUDE.md/ARCHITECTURE.md, no secrets are committed, and the change is staged with a drafted Conventional Commit message for human approval.

## Safety
Never hard-code `localhost` for inter-container calls — use service names (e.g. `users-db`, `api-gateway`). Config via env vars. Never commit the private key or secrets. No shared-code module in v1 — services self-contained; never put entities/repos/business logic in a shared module (see CLAUDE.md / ARCHITECTURE.md §15).
