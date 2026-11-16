# ResolveAI — Autonomous AI Customer Support Platform

> Agentic AI support for **ShopEase**, a demo e-commerce store. ResolveAI answers policy questions with RAG-grounded accuracy, checks orders, creates tickets, recommends refunds and escalates to humans — autonomously, with a full audit trail and human-in-the-loop controls.

Built for the Unstop **AI Builder Hackathon** and **Gen-AI Product Hackathon**.

| | |
|---|---|
| **Live demo (frontend)** | _deployed on Vercel — URL added after deployment_ |
| **API (backend)** | _deployed on Render — URL added after deployment_ |
| **Demo video** | _3-minute walkthrough — link added after recording_ |

---

## Table of contents

1. [Problem & solution](#problem--solution)
2. [Features](#features)
3. [AI concepts demonstrated](#ai-concepts-demonstrated)
4. [Architecture](#architecture)
5. [Tech stack](#tech-stack)
6. [Repository structure](#repository-structure)
7. [Getting started](#getting-started)
8. [Environment variables](#environment-variables)
9. [Database](#database)
10. [API reference](#api-reference)
11. [Using the app](#using-the-app)
12. [Testing](#testing)
13. [Deployment](#deployment)
14. [AI contribution disclosure](#ai-contribution-disclosure)
15. [License](#license)

---

## Problem & solution

**Problem.** E-commerce support teams drown in repetitive tickets — "where is my order?", "what's your return policy?", "I want a refund". Human agents spend most of their time on lookups and policy copy-paste, while customers wait hours for answers. Pure chatbots hallucinate policies and cannot *do* anything.

**Solution.** ResolveAI is an **agentic** support system: an LLM (Gemini 2.5 Flash via Spring AI) that *reasons* about each customer message, *decides* which action to take, and *executes* real operations through tool calling — checking live order data, creating tickets, recommending refunds — while store policies are retrieved from a **pgvector RAG knowledge base** so answers are grounded, not invented. Refunds are never auto-executed: the AI creates a **pending recommendation** that a human agent approves or rejects in a dashboard (**human-in-the-loop**). Every tool call is persisted as an auditable action card visible to both customer and agent.

## Features

**Customer chat**
- Streaming AI responses (SSE) with live "agent action" cards as tools execute
- Policy answers grounded in the store knowledge base (RAG with source citations)
- Order status checks against real database records (ownership-enforced)
- Refund requests → pending human approval, with policy-aware refusals (30-day window, amount caps)
- Escalation to human agents; human replies appear in the same chat
- Conversation history, multiple conversations, suggested prompts
- Self-registration + seeded demo account

**Agent dashboard**
- Live ticket queue (priority, status, customer, refund details), auto-refreshing
- Full conversation transcript with the AI's action trail
- **Approve / reject refund recommendations** (human-in-the-loop)
- Reply to customers as a human agent; resolve tickets

**Platform**
- JWT authentication with role-based access (CUSTOMER / AGENT)
- Flyway-managed PostgreSQL schema + pgvector vector store
- Idempotent knowledge-base ingestion (hash-based, re-embeds only when docs change)
- Graceful degradation: chat errors surface cleanly; KB retrieval failure doesn't break chat

## AI concepts demonstrated

| Concept | Where in ResolveAI |
|---|---|
| **Agentic AI** | The agent reasons per message, chooses tools, executes multi-step resolutions and reports outcomes — visible as action cards |
| **LLMs** | Gemini 2.5 Flash via Spring AI `ChatClient` |
| **Function calling / tool integration** | 4 `@Tool` methods (`checkOrderStatus`, `createTicket`, `recommendRefund`, `escalateToHuman`) with per-request `ToolContext` scoping |
| **RAG** | 6 policy documents chunked, embedded (`text-embedding-004`) into pgvector; top-4 similarity retrieval injected into the system prompt with source names |
| **Semantic search** | Cosine-distance HNSW index over 768-dim embeddings |
| **Human-in-the-loop** | Refund recommendations are PENDING until a human decides; escalations hand the conversation to a human agent |

## Architecture

```
┌────────────────────────┐        ┌─────────────────────────────────────────┐
│  React + Tailwind SPA  │  REST  │        Spring Boot 3.5 (Java 21)        │
│  (Vercel)              │ + JWT  │                                         │
│  • Customer chat (SSE) │◄──────►│  Spring Security + JWT filter           │
│  • Agent dashboard     │        │  Auth / Orders / Conversations / Agent  │
└────────────────────────┘        │                                         │
                                  │  ChatService ──► Spring AI ChatClient   │
                                  │      │              │  tool calling     │
                                  │      │ RAG          ▼                   │
                                  │      │         SupportTools (4 @Tool)   │
                                  │      │              │                   │
                                  │      ▼              ▼                   │
                                  │  PgVectorStore   JPA repositories       │
                                  └──────┬──────────────┬───────────────────┘
                                         │              │
                              ┌──────────▼──────────────▼─────────┐
                              │   PostgreSQL + pgvector (Render)  │
                              │   users, orders, conversations,   │
                              │   messages, tickets, refunds,     │
                              │   agent_actions, vector_store     │
                              └───────────────────────────────────┘
                                         ▲
                              Gemini API │ chat: gemini-2.5-flash
                              (Google)   │ embeddings: text-embedding-004
```

**Agent flow per message:** persist user message → embed query & retrieve top-4 policy chunks → build system prompt (persona + rules + grounded context) + last-20-message history → stream Gemini response with tool callbacks → tools execute against the DB and emit SSE `action` events → persist AI answer → `done` event.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full design (data model, security, tool contracts, RAG pipeline).

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 19, Tailwind CSS 4, Vite 7, react-router-dom 7 |
| Backend | Java 21, Spring Boot 3.5, Spring Web MVC, Spring Security, Spring Data JPA / Hibernate |
| AI | Spring AI 1.1 — Google GenAI (Gemini 2.5 Flash chat, text-embedding-004 embeddings), PgVector store |
| Database | PostgreSQL 16+ with pgvector, Flyway migrations |
| Auth | JWT (jjwt 0.12), BCrypt |
| Build | Maven (backend), npm/Vite (frontend) |
| Deployment | Render (Dockerised API + managed Postgres), Vercel (SPA) |
| Version control | Git + GitHub |

## Repository structure

```
├── backend/                  Spring Boot API
│   ├── src/main/java/com/resolveai/
│   │   ├── auth/             JWT filter, security config, login/register
│   │   ├── user/             User entity + repository
│   │   ├── order/            Order entity + customer orders API
│   │   ├── chat/             Conversations, messages, SSE chat, RAG ingestion
│   │   ├── agent/            SupportTools (@Tool), tickets, refunds, agent API
│   │   └── common/           Health check, async executor
│   ├── src/main/resources/
│   │   ├── db/migration/     V1 schema (incl. pgvector), V2 demo seed
│   │   ├── kb/               6 policy documents (RAG source)
│   │   └── application.yml
│   ├── src/test/java/        23 integration tests (local Postgres)
│   └── Dockerfile            Multi-stage build for Render
├── frontend/                 React SPA (chat + dashboard)
├── docs/                     ARCHITECTURE.md, DEMO_SCRIPT.md
├── render.yaml               Render blueprint (Postgres + Docker web service)
└── README.md
```

## Getting started

### Prerequisites

- Java 21, Maven 3.9+
- Node.js 20+
- PostgreSQL 16+ with the `pgvector` extension available
- A Gemini API key from [Google AI Studio](https://aistudio.google.com) (free tier works)

### 1. Database

```bash
createdb resolveai
psql -d resolveai -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

Flyway applies the schema and demo seed automatically on first boot.

### 2. Backend

```bash
cd backend
cp .env.example .env   # then export the values, or set them in your shell
export GEMINI_API_KEY=your-key-here
export DB_USER=your_pg_user DB_PASSWORD=your_pg_password
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. On first boot with `KB_INGEST=true`, the 6 policy documents are embedded into `vector_store` (requires a valid Gemini key).

### 3. Frontend

```bash
cd frontend
cp .env.example .env   # VITE_API_URL=http://localhost:8080
npm install
npm run dev
```

Open `http://localhost:5173`.

## Environment variables

### Backend (`backend/.env.example`)

| Variable | Required | Description |
|---|---|---|
| `GEMINI_API_KEY` | Yes (for AI) | Google Gemini Developer API key — used for both chat and embeddings. Without it the app boots, but chat/RAG return graceful errors |
| `DB_URL` | No | Full JDBC URL; defaults to `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:resolveai}` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | No | Alternative to `DB_URL` (used by the Render blueprint) |
| `DB_USER` / `DB_PASSWORD` | Yes | PostgreSQL credentials |
| `JWT_SECRET` | Prod | HS256 signing secret, ≥ 32 chars. A dev default exists for local use only |
| `CORS_ORIGIN` | Prod | Allowed frontend origin(s), comma-separated |
| `PORT` | No | HTTP port (Render sets it automatically; default 8080) |
| `KB_INGEST` | No | `true` (default) embeds the KB at startup; `false` skips it |

### Frontend (`frontend/.env.example`)

| Variable | Description |
|---|---|
| `VITE_API_URL` | Backend base URL, no trailing slash (default `http://localhost:8080`) |

**Secrets are never committed.** `.env` files are git-ignored; only `.env.example` templates are in the repo. On Render, `GEMINI_API_KEY` is set in the dashboard and `JWT_SECRET` is auto-generated by the blueprint.

## Database

Schema is managed by Flyway (`backend/src/main/resources/db/migration/`):

- **V1__schema.sql** — enables `vector`; creates `users`, `orders` (JSONB items), `conversations`, `messages`, `agent_actions`, `tickets`, `refund_recommendations`, `kb_ingestion` with CHECK-constrained enums, foreign keys and indexes. `vector_store` is created by Spring AI's PgVectorStore (768 dims, HNSW, cosine).
- **V2__seed.sql** — demo accounts and 8 orders covering every scenario:

| Order | Status | Scenario |
|---|---|---|
| SE-1001 | PROCESSING | normal lookup |
| SE-1002 | SHIPPED | tracking id TRK-88421-IN |
| SE-1003 | DELIVERED 5 days ago | **refund-eligible** |
| SE-1004 | DELIVERED 40 days ago | outside 30-day window → refused |
| SE-1005 | DELAYED | 10-day delay refund entitlement |
| SE-1006 | CANCELLED | cancelled order |
| SE-1007 | DELIVERED 2 days ago | recent delivery |
| SE-1008 | PROCESSING | normal lookup |

Relationships: `users 1—N orders/conversations`, `conversations 1—N messages/agent_actions/tickets`, `tickets 1—1 refund_recommendations`, `refund_recommendations N—1 orders`.

## API reference

All endpoints except auth/health require `Authorization: Bearer <JWT>`.

| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/api/auth/register` | public | `{name,email,password}` → `{token,role,name,email}` (CUSTOMER) |
| POST | `/api/auth/login` | public | `{email,password}` → `{token,role,name,email}` |
| GET | `/api/health` | public | Liveness probe |
| GET | `/api/orders` | CUSTOMER | Own orders |
| POST | `/api/conversations` | CUSTOMER | Create conversation |
| GET | `/api/conversations` | CUSTOMER | Own conversations (newest first) |
| GET | `/api/conversations/{id}/messages` | owner / AGENT | `{conversation, messages, actions}` |
| POST | `/api/chat/{conversationId}` | owner | `{message}` → **SSE stream**: `token`, `action`, `done`, `error` events |
| GET | `/api/agent/tickets` | AGENT | Ticket queue incl. nested refund recommendation |
| GET | `/api/agent/conversations` | AGENT | All conversations |
| POST | `/api/agent/tickets/{id}/reply` | AGENT | `{content}` → HUMAN_AGENT message; OPEN → IN_REVIEW |
| POST | `/api/agent/refunds/{id}/decide` | AGENT | `{decision: APPROVED\|REJECTED}`; 409 if already decided; notifies customer in chat |
| POST | `/api/agent/tickets/{id}/resolve` | AGENT | Resolve ticket + conversation |

**Agent tools (LLM function calling):** `checkOrderStatus(orderNumber)`, `createTicket(subject, description, priority)`, `recommendRefund(orderNumber, amount, reason)`, `escalateToHuman(reason)`. All are ownership-scoped via `ToolContext`; every invocation is persisted to `agent_actions` and streamed to the UI.

## Using the app

**Demo accounts** (password `demo1234`):

| Role | Email | Lands on |
|---|---|---|
| Customer (Aarav Sharma) | `customer@demo.com` | `/chat` |
| Support agent (Priya) | `agent@demo.com` | `/dashboard` |

**Try the golden path:**
1. As customer: *"What is your return policy?"* → RAG-grounded answer citing the policy.
2. *"Where is my order SE-1002?"* → `checkOrderStatus` action card + tracking details.
3. *"I want a refund for SE-1003, the item was not as described."* → `recommendRefund` action card, **pending approval**.
4. Sign in as agent in another browser → dashboard shows the ticket → **Approve**.
5. Back in the customer chat, the approval message appears within ~5s; conversation marked RESOLVED.
6. *"My order arrived damaged, I want to talk to a human."* → escalation banner + HIGH ticket; agent replies in the dashboard.

**Edge cases:** SE-1004 refund is refused (outside 30-day window); asking about another customer's order is refused; new customers can register.

## Testing

Backend integration tests run against a local PostgreSQL (no Docker needed):

```bash
cd backend
mvn test
```

23 tests cover: registration/login/JWT/401/409 flows, seeded order access, all 4 tools (ownership, refund window, amount caps, escalation), agent APIs (approve/reject/409 double-decision/reply/resolve/role enforcement), and the chat pipeline with a mocked `ChatModel` (RAG context injection, message persistence, error handling).

Frontend production build check: `cd frontend && npm run build`.

## Deployment

### Backend + database on Render

1. Push this repo to GitHub.
2. In Render: **New → Blueprint**, select the repo. `render.yaml` provisions:
   - `resolveai-db` — managed PostgreSQL (pgvector enabled by Flyway's `CREATE EXTENSION`)
   - `resolveai-api` — Docker web service built from `backend/Dockerfile`, health check `/api/health`
3. Set `GEMINI_API_KEY` (prompted by the blueprint, `sync: false`) and `CORS_ORIGIN` (your Vercel URL). `JWT_SECRET` is auto-generated; DB credentials are wired from the managed database.
4. First boot runs Flyway + KB ingestion automatically.

### Frontend on Vercel

```bash
cd frontend
vercel deploy --prod
```

Set `VITE_API_URL` to the Render API URL in the Vercel project's environment variables, then redeploy. `vercel.json` provides the SPA rewrite. Finally set the backend's `CORS_ORIGIN` to the Vercel domain.

## AI contribution disclosure

This project was built for hackathons that permit AI coding assistants. In the spirit of transparent judging:

- **Human contribution:** project idea and problem selection, feature scope and product decisions (demo domain, human-in-the-loop refund policy, UX flow), architecture choice (native Spring AI tool calling vs. hand-rolled agent loop), all credential/account provisioning, testing feedback and final acceptance.
- **AI-assisted contribution:** the majority of implementation code (backend services, tools, migrations, React UI, tests) and documentation drafts were generated with an AI coding agent under human direction, then compiled, tested (23 integration tests), browser-verified and iterated by the same human operator.
- The **application itself** uses AI at runtime as described above (Gemini + Spring AI); that is the product, distinct from the AI-assisted development process.

## License

[MIT](LICENSE)